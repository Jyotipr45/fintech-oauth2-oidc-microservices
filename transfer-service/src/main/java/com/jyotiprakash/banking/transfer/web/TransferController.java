/*
 * Copyright (c) 2026 Jyotiprakash Parida
 * SPDX-License-Identifier: MIT
 */
package com.jyotiprakash.banking.transfer.web;

import java.net.URI;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.jyotiprakash.banking.transfer.security.TokenRevocationService;
import com.jyotiprakash.banking.transfer.service.TransferService;
import com.jyotiprakash.banking.transfer.web.dto.CreateTransferRequest;
import com.jyotiprakash.banking.transfer.web.dto.PageResponse;
import com.jyotiprakash.banking.transfer.web.dto.TransferResponse;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;

/**
 * Public transfer API (reached through the gateway).
 *
 * @author Jyotiprakash Parida
 */
@RestController
@Validated
@RequestMapping("/api/v1/transfers")
public class TransferController {

    static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

    private final TransferService transferService;
    private final TokenRevocationService tokenRevocationService;

    public TransferController(TransferService transferService, TokenRevocationService tokenRevocationService) {
        this.transferService = transferService;
        this.tokenRevocationService = tokenRevocationService;
    }

    /**
     * High-risk operation: guarded by jti revocation and a per-user velocity limit before it gets here.
     * Returns 201 for a new transfer and 200 for an idempotent replay.
     */
    @PostMapping
    @PreAuthorize("hasRole('CUSTOMER') and hasAuthority('transfer:create')")
    public ResponseEntity<TransferResponse> create(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader(IDEMPOTENCY_KEY_HEADER) @Pattern(regexp = "^[A-Za-z0-9\\-]{8,64}$") String idempotencyKey,
            @Valid @RequestBody CreateTransferRequest request) {
        TransferService.TransferResult result = transferService.initiate(jwt, idempotencyKey, request);
        TransferResponse body = TransferResponse.from(result.transfer());
        if (result.created()) {
            return ResponseEntity.created(URI.create("/api/v1/transfers/" + body.id())).body(body);
        }
        return ResponseEntity.ok(body);
    }

    @GetMapping("/{transferId}")
    @PreAuthorize("hasAnyAuthority('transfer:read', 'transfer:read-all')")
    public TransferResponse transfer(@PathVariable UUID transferId, Authentication authentication) {
        return TransferResponse.from(transferService.get(transferId, authentication));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('transfer:read')")
    public PageResponse<TransferResponse> myTransfers(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return PageResponse.from(transferService.listMine(pageable), TransferResponse::from);
    }

    /**
     * Revokes the access token used for this call (e.g. on logout or suspected theft). Subsequent
     * high-risk requests with the same token are rejected with 401 even though it has not expired.
     */
    @PostMapping("/token-revocations")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> revokeCurrentToken(@AuthenticationPrincipal Jwt jwt) {
        if (!StringUtils.hasText(jwt.getId()) || jwt.getExpiresAt() == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
        tokenRevocationService.revoke(jwt.getId(), jwt.getExpiresAt());
        return ResponseEntity.noContent().build();
    }
}
