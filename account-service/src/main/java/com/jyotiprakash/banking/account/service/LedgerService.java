/*
 * Copyright (c) 2026 Jyotiprakash Parida
 * SPDX-License-Identifier: MIT
 */
package com.jyotiprakash.banking.account.service;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jyotiprakash.banking.account.domain.Account;
import com.jyotiprakash.banking.account.domain.EntryType;
import com.jyotiprakash.banking.account.domain.LedgerEntry;
import com.jyotiprakash.banking.account.exception.AccountNotFoundException;
import com.jyotiprakash.banking.account.exception.CurrencyMismatchException;
import com.jyotiprakash.banking.account.exception.OwnershipMismatchException;
import com.jyotiprakash.banking.account.exception.SameAccountTransferException;
import com.jyotiprakash.banking.account.repository.AccountRepository;
import com.jyotiprakash.banking.account.repository.LedgerEntryRepository;
import com.jyotiprakash.banking.account.web.dto.LedgerTransferRequest;
import com.jyotiprakash.banking.account.web.dto.LedgerTransferResponse;

/**
 * Posts transfers atomically: debit and credit happen in one local database transaction, with both rows
 * locked in a deterministic (id) order to avoid deadlocks between opposite transfers.
 *
 * @author Jyotiprakash Parida
 */
@Service
public class LedgerService {

    private static final Logger LOG = LoggerFactory.getLogger(LedgerService.class);

    private final AccountRepository accounts;
    private final LedgerEntryRepository ledger;

    public LedgerService(AccountRepository accounts, LedgerEntryRepository ledger) {
        this.accounts = accounts;
        this.ledger = ledger;
    }

    /**
     * @param request      validated posting request
     * @param callerSubject {@code sub} of the machine client that called (for the audit trail)
     */
    @Transactional
    public LedgerTransferResponse postTransfer(LedgerTransferRequest request, String callerSubject) {
        if (ledger.existsByTransferId(request.transferId())) {
            return new LedgerTransferResponse(request.transferId(), LedgerTransferResponse.Outcome.ALREADY_APPLIED);
        }

        UUID sourceId = request.sourceAccountId();
        UUID targetId = accounts.findIdByAccountNumber(request.targetAccountNumber())
                .orElseThrow(AccountNotFoundException::new);
        if (sourceId.equals(targetId)) {
            throw new SameAccountTransferException();
        }

        // Lock both rows in a global order so concurrent A->B and B->A transfers cannot deadlock.
        boolean sourceFirst = sourceId.compareTo(targetId) < 0;
        Account first = lock(sourceFirst ? sourceId : targetId);
        Account second = lock(sourceFirst ? targetId : sourceId);
        Account source = sourceFirst ? first : second;
        Account target = sourceFirst ? second : first;

        // Defense in depth: the machine client must be acting for the real owner of the source account.
        if (!source.isOwnedBy(request.initiatedBy())) {
            LOG.warn("Ledger posting rejected: initiator does not own source account. transferId={} caller={}",
                    request.transferId(), callerSubject);
            throw new OwnershipMismatchException();
        }
        if (!source.getCurrency().equals(request.currency()) || !target.getCurrency().equals(request.currency())) {
            throw new CurrencyMismatchException();
        }

        source.debit(request.amount());
        target.credit(request.amount());
        ledger.save(new LedgerEntry(request.transferId(), source.getId(), EntryType.DEBIT, request.amount(),
                callerSubject));
        ledger.save(new LedgerEntry(request.transferId(), target.getId(), EntryType.CREDIT, request.amount(),
                callerSubject));

        LOG.info("Transfer posted: transferId={} initiator={} caller={}",
                request.transferId(), request.initiatedBy(), callerSubject);
        return new LedgerTransferResponse(request.transferId(), LedgerTransferResponse.Outcome.APPLIED);
    }

    private Account lock(UUID accountId) {
        return accounts.findByIdForUpdate(accountId).orElseThrow(AccountNotFoundException::new);
    }
}
