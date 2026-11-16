/*
 * Copyright (c) 2026 Jyotiprakash Parida
 * SPDX-License-Identifier: MIT
 */
package com.jyotiprakash.banking.transfer.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.jyotiprakash.banking.transfer.client.AccountServiceClient;
import com.jyotiprakash.banking.transfer.client.AccountSnapshot;
import com.jyotiprakash.banking.transfer.client.LedgerPostingRequest;
import com.jyotiprakash.banking.transfer.client.LedgerPostingResponse;
import com.jyotiprakash.banking.transfer.domain.OutboxEvent;
import com.jyotiprakash.banking.transfer.domain.Transfer;
import com.jyotiprakash.banking.transfer.domain.TransferStatus;
import com.jyotiprakash.banking.transfer.exception.DownstreamUnavailableException;
import com.jyotiprakash.banking.transfer.exception.IdempotencyKeyReuseException;
import com.jyotiprakash.banking.transfer.exception.TransferPendingException;
import com.jyotiprakash.banking.transfer.exception.TransferRejectedException;
import com.jyotiprakash.banking.transfer.repository.OutboxEventRepository;
import com.jyotiprakash.banking.transfer.repository.TransferRepository;
import com.jyotiprakash.banking.transfer.web.dto.CreateTransferRequest;

/**
 * Saga orchestration: token propagation, client-credentials posting, idempotency and outbox writes.
 *
 * @author Jyotiprakash Parida
 */
class TransferServiceTest {

    private static final String ALICE = "11111111-1111-1111-1111-111111111111";
    private static final UUID SOURCE = UUID.randomUUID();
    private static final String TARGET = "BNK123456789012";

    private final TransferRepository transfers = mock(TransferRepository.class);
    private final OutboxEventRepository outbox = mock(OutboxEventRepository.class);
    private final AccountServiceClient accounts = mock(AccountServiceClient.class);
    private final TransactionTemplate transactions = mock(TransactionTemplate.class);
    private final TransferService service = new TransferService(transfers, outbox, accounts, transactions,
            new ObjectMapper().registerModule(new JavaTimeModule()));

    private final Jwt jwt = Jwt.withTokenValue("user-access-token").header("alg", "RS256").subject(ALICE)
            .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300)).build();

    @BeforeEach
    @SuppressWarnings("unchecked")
    void transactionsRunInline() {
        when(transactions.execute(any())).thenAnswer(invocation ->
                ((TransactionCallback<Object>) invocation.getArgument(0)).doInTransaction(null));
        when(transfers.saveAndFlush(any(Transfer.class))).thenAnswer(invocation -> {
            Transfer saved = invocation.getArgument(0);
            when(transfers.findById(saved.getId())).thenReturn(Optional.of(saved));
            return saved;
        });
        when(transfers.findByOwnerSubjectAndIdempotencyKey(any(), any())).thenReturn(Optional.empty());
    }

    @Test
    void readsSourceWithUserTokenAndPostsWithServiceToken() {
        when(accounts.fetchAccountAsUser(SOURCE, "user-access-token"))
                .thenReturn(new AccountSnapshot(SOURCE, "BNK000000000001", "INR", "ACTIVE"));
        when(accounts.postToLedger(any())).thenReturn(new LedgerPostingResponse(UUID.randomUUID(), "APPLIED"));

        TransferService.TransferResult result = service.initiate(jwt, "key-12345678", request("100.00"));

        assertThat(result.created()).isTrue();
        assertThat(result.transfer().getStatus()).isEqualTo(TransferStatus.COMPLETED);

        ArgumentCaptor<LedgerPostingRequest> posting = ArgumentCaptor.forClass(LedgerPostingRequest.class);
        verify(accounts).postToLedger(posting.capture());
        assertThat(posting.getValue().initiatedBy()).isEqualTo(ALICE);

        ArgumentCaptor<OutboxEvent> event = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outbox).save(event.capture());
        assertThat(event.getValue().getEventType()).isEqualTo(TransferService.EVENT_COMPLETED);
    }

    @Test
    void businessRejectionMarksTransferFailed() {
        when(accounts.fetchAccountAsUser(eq(SOURCE), any()))
                .thenReturn(new AccountSnapshot(SOURCE, "BNK000000000001", "INR", "ACTIVE"));
        when(accounts.postToLedger(any()))
                .thenThrow(new TransferRejectedException(HttpStatus.UNPROCESSABLE_ENTITY, "insufficient_funds"));

        Transfer transfer = service.initiate(jwt, "key-12345678", request("100.00")).transfer();

        assertThat(transfer.getStatus()).isEqualTo(TransferStatus.FAILED);
        assertThat(transfer.getFailureCode()).isEqualTo("insufficient_funds");
    }

    @Test
    void ledgerOutageLeavesTransferPendingForIdempotentRetry() {
        when(accounts.fetchAccountAsUser(eq(SOURCE), any()))
                .thenReturn(new AccountSnapshot(SOURCE, "BNK000000000001", "INR", "ACTIVE"));
        when(accounts.postToLedger(any())).thenThrow(new DownstreamUnavailableException("timeout"));

        assertThatThrownBy(() -> service.initiate(jwt, "key-12345678", request("100.00")))
                .isInstanceOf(TransferPendingException.class);
        verify(outbox, never()).save(any());
    }

    @Test
    void reusingIdempotencyKeyWithDifferentPayloadIsRejected() {
        Transfer existing = Transfer.pending(ALICE, "key-12345678", SOURCE, TARGET, new BigDecimal("100.00"), "INR",
                null);
        existing.markCompleted();
        when(transfers.findByOwnerSubjectAndIdempotencyKey(ALICE, "key-12345678")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.initiate(jwt, "key-12345678", request("999.00")))
                .isInstanceOf(IdempotencyKeyReuseException.class);
        verify(accounts, never()).postToLedger(any());
    }

    @Test
    void replayOfCompletedTransferReturnsItWithoutPostingAgain() {
        Transfer existing = Transfer.pending(ALICE, "key-12345678", SOURCE, TARGET, new BigDecimal("100.00"), "INR",
                null);
        existing.markCompleted();
        when(transfers.findByOwnerSubjectAndIdempotencyKey(ALICE, "key-12345678")).thenReturn(Optional.of(existing));

        TransferService.TransferResult result = service.initiate(jwt, "key-12345678", request("100.00"));

        assertThat(result.created()).isFalse();
        assertThat(result.transfer()).isSameAs(existing);
        verify(accounts, never()).postToLedger(any());
    }

    private static CreateTransferRequest request(String amount) {
        return new CreateTransferRequest(SOURCE, TARGET, new BigDecimal(amount), "INR", "rent");
    }
}
