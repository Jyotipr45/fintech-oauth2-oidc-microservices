/*
 * Copyright (c) 2026 Jyotiprakash Parida
 * SPDX-License-Identifier: MIT
 */
package com.jyotiprakash.banking.account.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import com.jyotiprakash.banking.account.domain.Account;
import com.jyotiprakash.banking.account.domain.AccountType;
import com.jyotiprakash.banking.account.domain.LedgerEntry;
import com.jyotiprakash.banking.account.exception.AccountNotFoundException;
import com.jyotiprakash.banking.account.exception.InsufficientFundsException;
import com.jyotiprakash.banking.account.exception.OwnershipMismatchException;
import com.jyotiprakash.banking.account.repository.AccountRepository;
import com.jyotiprakash.banking.account.repository.LedgerEntryRepository;
import com.jyotiprakash.banking.account.web.dto.LedgerTransferRequest;
import com.jyotiprakash.banking.account.web.dto.LedgerTransferResponse;

/**
 * Resource-level authorization (BOLA/IDOR) and ledger invariants.
 *
 * @author Jyotiprakash Parida
 */
class AccountOwnershipTest {

    private static final String ALICE = "11111111-1111-1111-1111-111111111111";
    private static final String BOB = "22222222-2222-2222-2222-222222222222";

    private final AccountRepository accounts = mock(AccountRepository.class);
    private final LedgerEntryRepository ledger = mock(LedgerEntryRepository.class);
    private final AccountService accountService = new AccountService(accounts, ledger, new AccountNumberGenerator());
    private final LedgerService ledgerService = new LedgerService(accounts, ledger);

    @BeforeEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void customerCannotReadAnotherCustomersAccount() {
        authenticate(ALICE, "ROLE_CUSTOMER", "account:read");
        UUID bobsAccount = UUID.randomUUID();
        when(accounts.findByIdAndOwnerSubject(bobsAccount, ALICE)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.getAccount(bobsAccount)).isInstanceOf(AccountNotFoundException.class);
        verify(accounts, never()).findById(any());
    }

    @Test
    void customerReadsOwnAccountThroughOwnerScopedQuery() {
        authenticate(ALICE, "ROLE_CUSTOMER", "account:read");
        Account own = Account.open("BNK000000000001", ALICE, AccountType.SAVINGS, "INR");
        when(accounts.findByIdAndOwnerSubject(own.getId(), ALICE)).thenReturn(Optional.of(own));

        assertThat(accountService.getAccount(own.getId())).isSameAs(own);
    }

    @Test
    void auditorWithReadAllUsesUnscopedLookup() {
        authenticate("auditor-sub", "ROLE_AUDITOR", "account:read-all");
        Account any = Account.open("BNK000000000002", BOB, AccountType.CURRENT, "INR");
        when(accounts.findById(any.getId())).thenReturn(Optional.of(any));

        assertThat(accountService.getAccount(any.getId())).isSameAs(any);
    }

    @Test
    void ledgerRejectsMachineCallActingForNonOwner() {
        Account bobs = funded(BOB, "500.00");
        Account target = funded(ALICE, "0.00");
        stubTransferAccounts(bobs, target);

        LedgerTransferRequest request = new LedgerTransferRequest(UUID.randomUUID(), bobs.getId(),
                target.getAccountNumber(), new BigDecimal("100.00"), "INR", ALICE);

        assertThatThrownBy(() -> ledgerService.postTransfer(request, "service-account"))
                .isInstanceOf(OwnershipMismatchException.class);
        assertThat(bobs.getBalance()).isEqualByComparingTo("500.00");
    }

    @Test
    void ledgerRejectsOverdraft() {
        Account source = funded(ALICE, "50.00");
        Account target = funded(BOB, "0.00");
        stubTransferAccounts(source, target);

        LedgerTransferRequest request = new LedgerTransferRequest(UUID.randomUUID(), source.getId(),
                target.getAccountNumber(), new BigDecimal("100.00"), "INR", ALICE);

        assertThatThrownBy(() -> ledgerService.postTransfer(request, "service-account"))
                .isInstanceOf(InsufficientFundsException.class);
    }

    @Test
    void ledgerMovesMoneyAndIsIdempotent() {
        Account source = funded(ALICE, "500.00");
        Account target = funded(BOB, "0.00");
        stubTransferAccounts(source, target);
        UUID transferId = UUID.randomUUID();
        LedgerTransferRequest request = new LedgerTransferRequest(transferId, source.getId(),
                target.getAccountNumber(), new BigDecimal("125.50"), "INR", ALICE);

        assertThat(ledgerService.postTransfer(request, "service-account").outcome())
                .isEqualTo(LedgerTransferResponse.Outcome.APPLIED);
        assertThat(source.getBalance()).isEqualByComparingTo("374.50");
        assertThat(target.getBalance()).isEqualByComparingTo("125.50");

        when(ledger.existsByTransferId(transferId)).thenReturn(true);
        assertThat(ledgerService.postTransfer(request, "service-account").outcome())
                .isEqualTo(LedgerTransferResponse.Outcome.ALREADY_APPLIED);
        assertThat(source.getBalance()).isEqualByComparingTo("374.50");
    }

    private void stubTransferAccounts(Account source, Account target) {
        when(ledger.existsByTransferId(any())).thenReturn(false);
        when(ledger.save(any(LedgerEntry.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(accounts.findIdByAccountNumber(target.getAccountNumber())).thenReturn(Optional.of(target.getId()));
        when(accounts.findByIdForUpdate(source.getId())).thenReturn(Optional.of(source));
        when(accounts.findByIdForUpdate(target.getId())).thenReturn(Optional.of(target));
    }

    private static Account funded(String owner, String amount) {
        Account account = Account.open("BNK" + Math.abs(UUID.randomUUID().getMostSignificantBits() % 1_000_000_000_000L),
                owner, AccountType.SAVINGS, "INR");
        BigDecimal balance = new BigDecimal(amount);
        if (balance.signum() > 0) {
            account.credit(balance);
        }
        return account;
    }

    private static void authenticate(String subject, String... authorities) {
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "RS256").subject(subject)
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300)).build();
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(jwt, AuthorityUtils.createAuthorityList(authorities), subject));
    }
}
