/*
 * Copyright (c) 2026 Jyotiprakash Parida
 * SPDX-License-Identifier: MIT
 */
package com.jyotiprakash.banking.transfer.service;

import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jyotiprakash.banking.security.principal.JwtPrincipals;
import com.jyotiprakash.banking.transfer.client.AccountServiceClient;
import com.jyotiprakash.banking.transfer.client.AccountSnapshot;
import com.jyotiprakash.banking.transfer.client.LedgerPostingRequest;
import com.jyotiprakash.banking.transfer.domain.OutboxEvent;
import com.jyotiprakash.banking.transfer.domain.Transfer;
import com.jyotiprakash.banking.transfer.domain.TransferStatus;
import com.jyotiprakash.banking.transfer.exception.DownstreamUnavailableException;
import com.jyotiprakash.banking.transfer.exception.IdempotencyKeyReuseException;
import com.jyotiprakash.banking.transfer.exception.TransferNotFoundException;
import com.jyotiprakash.banking.transfer.exception.TransferPendingException;
import com.jyotiprakash.banking.transfer.exception.TransferRejectedException;
import com.jyotiprakash.banking.transfer.repository.OutboxEventRepository;
import com.jyotiprakash.banking.transfer.repository.TransferRepository;
import com.jyotiprakash.banking.transfer.web.dto.CreateTransferRequest;

/**
 * Orchestrates a transfer (a small orchestration saga):
 *
 * <ol>
 *   <li><b>Idempotency</b> &mdash; {@code (sub, Idempotency-Key)} returns the existing transfer on retry.</li>
 *   <li><b>Token propagation</b> &mdash; the source account is read from account-service <em>with the
 *       user's own token</em>, so account-service enforces that this user owns it.</li>
 *   <li>The transfer is recorded as {@code PENDING} in a short local transaction.</li>
 *   <li><b>Client Credentials</b> &mdash; the ledger posting is made with the service's machine token,
 *       with circuit breaker, retry and bulkhead.</li>
 *   <li>The final state and its outbox event are committed together in one transaction.</li>
 * </ol>
 *
 * <p>No database transaction is held open across a remote call.</p>
 *
 * @author Jyotiprakash Parida
 */
@Service
public class TransferService {

    public static final String READ_ALL_PERMISSION = "transfer:read-all";
    static final String EVENT_COMPLETED = "TransferCompleted";
    static final String EVENT_FAILED = "TransferFailed";

    private static final Logger LOG = LoggerFactory.getLogger(TransferService.class);

    private final TransferRepository transfers;
    private final OutboxEventRepository outbox;
    private final AccountServiceClient accounts;
    private final TransactionTemplate transactions;
    private final ObjectMapper objectMapper;

    public TransferService(TransferRepository transfers, OutboxEventRepository outbox, AccountServiceClient accounts,
                           TransactionTemplate transactions, ObjectMapper objectMapper) {
        this.transfers = transfers;
        this.outbox = outbox;
        this.accounts = accounts;
        this.transactions = transactions;
        this.objectMapper = objectMapper;
    }

    public TransferResult initiate(Jwt user, String idempotencyKey, CreateTransferRequest request) {
        String owner = user.getSubject();

        Optional<Transfer> existing = transfers.findByOwnerSubjectAndIdempotencyKey(owner, idempotencyKey);
        if (existing.isPresent()) {
            return new TransferResult(replay(existing.get(), request), false);
        }

        // 1. Token propagation: account-service decides, as this user, whether the source account is theirs.
        AccountSnapshot source = accounts.fetchAccountAsUser(request.sourceAccountId(), user.getTokenValue());
        if (!source.isActive()) {
            throw new TransferRejectedException(HttpStatus.CONFLICT, "account_not_active");
        }
        if (!source.currency().equals(request.currency())) {
            throw new TransferRejectedException(HttpStatus.UNPROCESSABLE_ENTITY, "currency_mismatch");
        }

        Transfer pending;
        try {
            pending = transactions.execute(status -> transfers.saveAndFlush(Transfer.pending(owner, idempotencyKey,
                    request.sourceAccountId(), request.targetAccountNumber(), request.amount(), request.currency(),
                    request.reference())));
        }
        catch (DataIntegrityViolationException concurrentDuplicate) {
            // Two identical requests raced; the unique constraint let exactly one through.
            Transfer winner = transfers.findByOwnerSubjectAndIdempotencyKey(owner, idempotencyKey)
                    .orElseThrow(() -> concurrentDuplicate);
            return new TransferResult(replay(winner, request), false);
        }
        LOG.info("Transfer created: transferId={} subject={}", pending.getId(), owner);

        // 2. Client Credentials: privileged ledger posting with the service's own token.
        return new TransferResult(postToLedger(pending), true);
    }

    public Transfer get(UUID transferId, Authentication authentication) {
        if (JwtPrincipals.hasAuthority(authentication, READ_ALL_PERMISSION)) {
            return transfers.findById(transferId).orElseThrow(TransferNotFoundException::new);
        }
        return transfers.findByIdAndOwnerSubject(transferId, JwtPrincipals.currentSubject())
                .orElseThrow(TransferNotFoundException::new);
    }

    public Page<Transfer> listMine(Pageable pageable) {
        return transfers.findAllByOwnerSubject(JwtPrincipals.currentSubject(), pageable);
    }

    private Transfer replay(Transfer existing, CreateTransferRequest request) {
        if (!existing.sameRequestAs(request.sourceAccountId(), request.targetAccountNumber(), request.amount(),
                request.currency())) {
            throw new IdempotencyKeyReuseException();
        }
        if (existing.getStatus() == TransferStatus.PENDING) {
            // Resume a transfer whose ledger posting outcome was unknown; the ledger deduplicates by id.
            return postToLedger(existing);
        }
        return existing;
    }

    private Transfer postToLedger(Transfer transfer) {
        try {
            accounts.postToLedger(new LedgerPostingRequest(transfer.getId(), transfer.getSourceAccountId(),
                    transfer.getTargetAccountNumber(), transfer.getAmount(), transfer.getCurrency(),
                    transfer.getOwnerSubject()));
            return finish(transfer.getId(), null);
        }
        catch (TransferRejectedException rejected) {
            return finish(transfer.getId(), rejected.getErrorCode());
        }
        catch (DownstreamUnavailableException unavailable) {
            LOG.warn("Ledger unavailable; transfer stays PENDING: transferId={} reason={}",
                    transfer.getId(), unavailable.getMessage());
            throw new TransferPendingException();
        }
    }

    private Transfer finish(UUID transferId, String failureCode) {
        return transactions.execute(status -> {
            Transfer transfer = transfers.findById(transferId).orElseThrow(TransferNotFoundException::new);
            if (transfer.getStatus() != TransferStatus.PENDING) {
                return transfer;
            }
            if (failureCode == null) {
                transfer.markCompleted();
            }
            else {
                transfer.markFailed(failureCode);
            }
            outbox.save(new OutboxEvent(transfer.getId(), failureCode == null ? EVENT_COMPLETED : EVENT_FAILED,
                    toJson(TransferEvent.of(transfer))));
            LOG.info("Transfer finished: transferId={} status={}", transferId, transfer.getStatus());
            return transfer;
        });
    }

    private String toJson(TransferEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        }
        catch (JsonProcessingException ex) {
            throw new IllegalStateException("Could not serialise transfer event", ex);
        }
    }

    /**
     * @param transfer the transfer
     * @param created  {@code true} if this call created it, {@code false} for an idempotent replay
     */
    public record TransferResult(Transfer transfer, boolean created) {
    }
}
