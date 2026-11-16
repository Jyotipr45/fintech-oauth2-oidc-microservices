/*
 * Copyright (c) 2026 Jyotiprakash Parida
 * SPDX-License-Identifier: MIT
 */
package com.jyotiprakash.banking.account.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jyotiprakash.banking.account.domain.Account;
import com.jyotiprakash.banking.account.domain.AccountStatus;
import com.jyotiprakash.banking.account.domain.AccountType;
import com.jyotiprakash.banking.account.domain.EntryType;
import com.jyotiprakash.banking.account.domain.LedgerEntry;
import com.jyotiprakash.banking.account.exception.AccountLimitExceededException;
import com.jyotiprakash.banking.account.exception.AccountNotFoundException;
import com.jyotiprakash.banking.account.repository.AccountRepository;
import com.jyotiprakash.banking.account.repository.LedgerEntryRepository;
import com.jyotiprakash.banking.security.principal.JwtPrincipals;

/**
 * Account use cases with resource-level authorization (BOLA/IDOR prevention).
 *
 * <p>Role checks ({@code @PreAuthorize}) answer "may this kind of user call this endpoint?". This class
 * answers "may <em>this</em> user touch <em>this</em> account?": unless the caller holds
 * {@code account:read-all}, every lookup is filtered in SQL by {@code owner_subject = <JWT sub>}. A record
 * that exists but belongs to someone else is reported exactly like a missing one (404), so identifiers
 * cannot be enumerated.</p>
 *
 * @author Jyotiprakash Parida
 */
@Service
public class AccountService {

    public static final String READ_ALL_PERMISSION = "account:read-all";
    static final int MAX_ACCOUNTS_PER_CUSTOMER = 5;
    private static final int ACCOUNT_NUMBER_ATTEMPTS = 5;

    private static final Logger LOG = LoggerFactory.getLogger(AccountService.class);

    private final AccountRepository accounts;
    private final LedgerEntryRepository ledger;
    private final AccountNumberGenerator accountNumbers;

    public AccountService(AccountRepository accounts, LedgerEntryRepository ledger,
                          AccountNumberGenerator accountNumbers) {
        this.accounts = accounts;
        this.ledger = ledger;
        this.accountNumbers = accountNumbers;
    }

    @Transactional(readOnly = true)
    public List<Account> listMyAccounts() {
        return accounts.findAllByOwnerSubjectOrderByCreatedAtAsc(JwtPrincipals.currentSubject());
    }

    @Transactional(readOnly = true)
    public Page<Account> listAllAccounts(Pageable pageable) {
        return accounts.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public Account getAccount(UUID accountId) {
        if (canReadAll(currentAuthentication())) {
            return accounts.findById(accountId).orElseThrow(AccountNotFoundException::new);
        }
        return accounts.findByIdAndOwnerSubject(accountId, JwtPrincipals.currentSubject())
                .orElseThrow(AccountNotFoundException::new);
    }

    @Transactional
    public Account openAccount(AccountType type, String currency) {
        String owner = JwtPrincipals.currentSubject();
        if (accounts.countByOwnerSubject(owner) >= MAX_ACCOUNTS_PER_CUSTOMER) {
            throw new AccountLimitExceededException();
        }
        Account account = Account.open(uniqueAccountNumber(), owner, type, currency);
        Account saved = accounts.save(account);
        LOG.info("Account opened: accountId={} subject={}", saved.getId(), owner);
        return saved;
    }

    @Transactional
    public Account changeStatus(UUID accountId, AccountStatus status, String reason) {
        Account account = accounts.findByIdForUpdate(accountId).orElseThrow(AccountNotFoundException::new);
        AccountStatus previous = account.getStatus();
        account.changeStatus(status);
        LOG.info("Account status changed: accountId={} {}->{} by subject={} reason='{}'",
                accountId, previous, status, JwtPrincipals.currentSubject(), reason.replaceAll("[\\r\\n]", " "));
        return account;
    }

    @Transactional
    public Account deposit(UUID accountId, BigDecimal amount) {
        Account account = accounts.findByIdForUpdate(accountId).orElseThrow(AccountNotFoundException::new);
        account.credit(amount);
        String actor = JwtPrincipals.currentSubject();
        ledger.save(new LedgerEntry(null, accountId, EntryType.CREDIT, amount, actor));
        LOG.info("Teller deposit: accountId={} by subject={}", accountId, actor);
        return account;
    }

    static boolean canReadAll(Authentication authentication) {
        return JwtPrincipals.hasAuthority(authentication, READ_ALL_PERMISSION);
    }

    private static Authentication currentAuthentication() {
        return SecurityContextHolder.getContext().getAuthentication();
    }

    private String uniqueAccountNumber() {
        for (int attempt = 0; attempt < ACCOUNT_NUMBER_ATTEMPTS; attempt++) {
            String candidate = accountNumbers.next();
            if (!accounts.existsByAccountNumber(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Could not allocate a unique account number");
    }
}
