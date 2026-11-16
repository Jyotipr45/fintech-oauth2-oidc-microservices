/*
 * Copyright (c) 2026 Jyotiprakash Parida
 * SPDX-License-Identifier: MIT
 */
package com.jyotiprakash.banking.account.web;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.jyotiprakash.banking.account.domain.Account;
import com.jyotiprakash.banking.account.service.AccountService;
import com.jyotiprakash.banking.account.web.dto.AccountResponse;
import com.jyotiprakash.banking.account.web.dto.DepositRequest;
import com.jyotiprakash.banking.account.web.dto.OpenAccountRequest;
import com.jyotiprakash.banking.account.web.dto.PageResponse;
import com.jyotiprakash.banking.account.web.dto.UpdateAccountStatusRequest;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Public account API (reached through the gateway).
 *
 * <table>
 *   <caption>Authorization matrix</caption>
 *   <tr><th>Endpoint</th><th>CUSTOMER</th><th>ADMIN</th><th>AUDITOR</th></tr>
 *   <tr><td>GET /me</td><td>own</td><td>own</td><td>403</td></tr>
 *   <tr><td>GET /</td><td>403</td><td>all</td><td>all</td></tr>
 *   <tr><td>GET /{id}</td><td>own only (else 404)</td><td>any</td><td>any</td></tr>
 *   <tr><td>POST /</td><td>yes</td><td>403</td><td>403</td></tr>
 *   <tr><td>PATCH /{id}/status</td><td>403</td><td>yes</td><td>403</td></tr>
 *   <tr><td>POST /{id}/deposits</td><td>403</td><td>yes</td><td>403</td></tr>
 * </table>
 *
 * @author Jyotiprakash Parida
 */
@RestController
@Validated
@RequestMapping("/api/v1/accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping("/me")
    @PreAuthorize("hasAuthority('account:read')")
    public List<AccountResponse> myAccounts() {
        return accountService.listMyAccounts().stream().map(AccountResponse::from).toList();
    }

    @GetMapping
    @PreAuthorize("hasAuthority('account:read-all')")
    public PageResponse<AccountResponse> allAccounts(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "createdAt"));
        return PageResponse.from(accountService.listAllAccounts(pageable), AccountResponse::from);
    }

    @GetMapping("/{accountId}")
    @PreAuthorize("hasAnyAuthority('account:read', 'account:read-all')")
    public AccountResponse account(@PathVariable UUID accountId) {
        return AccountResponse.from(accountService.getAccount(accountId));
    }

    @PostMapping
    @PreAuthorize("hasRole('CUSTOMER') and hasAuthority('account:write')")
    public ResponseEntity<AccountResponse> open(@Valid @RequestBody OpenAccountRequest request) {
        Account account = accountService.openAccount(request.type(), request.currency());
        return ResponseEntity.created(URI.create("/api/v1/accounts/" + account.getId()))
                .body(AccountResponse.from(account));
    }

    @PatchMapping("/{accountId}/status")
    @PreAuthorize("hasRole('ADMIN') and hasAuthority('account:freeze')")
    public AccountResponse changeStatus(@PathVariable UUID accountId,
                                        @Valid @RequestBody UpdateAccountStatusRequest request) {
        return AccountResponse.from(accountService.changeStatus(accountId, request.status(), request.reason()));
    }

    @PostMapping("/{accountId}/deposits")
    @PreAuthorize("hasRole('ADMIN') and hasAuthority('account:write')")
    public AccountResponse deposit(@PathVariable UUID accountId, @Valid @RequestBody DepositRequest request) {
        return AccountResponse.from(accountService.deposit(accountId, request.amount()));
    }
}
