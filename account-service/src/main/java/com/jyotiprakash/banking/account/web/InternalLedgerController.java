/*
 * Copyright (c) 2026 Jyotiprakash Parida
 * SPDX-License-Identifier: MIT
 */
package com.jyotiprakash.banking.account.web;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jyotiprakash.banking.account.service.LedgerService;
import com.jyotiprakash.banking.account.web.dto.LedgerTransferRequest;
import com.jyotiprakash.banking.account.web.dto.LedgerTransferResponse;
import com.jyotiprakash.banking.security.principal.JwtPrincipals;

import jakarta.validation.Valid;

/**
 * Internal, machine-to-machine API. Not routed by the gateway and callable only with a Client Credentials
 * token that carries the {@code account:balance-adjust} permission (held solely by transfer-service's
 * service account). End-user tokens never carry that permission.
 *
 * @author Jyotiprakash Parida
 */
@RestController
@RequestMapping("/internal/v1/ledger")
public class InternalLedgerController {

    private final LedgerService ledgerService;

    public InternalLedgerController(LedgerService ledgerService) {
        this.ledgerService = ledgerService;
    }

    @PostMapping("/transfers")
    @PreAuthorize("hasAuthority('account:balance-adjust')")
    public LedgerTransferResponse postTransfer(@Valid @RequestBody LedgerTransferRequest request) {
        return ledgerService.postTransfer(request, JwtPrincipals.currentSubject());
    }
}
