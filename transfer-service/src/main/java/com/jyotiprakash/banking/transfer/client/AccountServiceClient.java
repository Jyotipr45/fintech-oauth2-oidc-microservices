/*
 * Copyright (c) 2026 Jyotiprakash Parida
 * SPDX-License-Identifier: MIT
 */
package com.jyotiprakash.banking.transfer.client;

import java.util.UUID;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.jyotiprakash.banking.transfer.exception.DownstreamUnavailableException;
import com.jyotiprakash.banking.transfer.exception.SourceAccountNotFoundException;
import com.jyotiprakash.banking.transfer.exception.TransferRejectedException;

/**
 * Anti-corruption layer for account-service: hides HTTP and translates its responses into this service's
 * own exceptions.
 *
 * @author Jyotiprakash Parida
 */
@Component
public class AccountServiceClient {

    private static final Logger LOG = LoggerFactory.getLogger(AccountServiceClient.class);
    private static final String UNKNOWN_REJECTION = "rejected";
    private static final String OWNERSHIP_MISMATCH = "ownership_mismatch";

    private final RestClient userTokenClient;
    private final RestClient serviceClient;
    private final ResilientExecutor resilience;

    public AccountServiceClient(@Qualifier("userTokenAccountClient") RestClient userTokenClient,
                                @Qualifier("serviceAccountClient") RestClient serviceClient,
                                ResilientExecutor resilience) {
        this.userTokenClient = userTokenClient;
        this.serviceClient = serviceClient;
        this.resilience = resilience;
    }

    /**
     * <b>User token propagation.</b> Reads the source account <em>as the end user</em>. account-service then
     * applies that user's own RBAC and ownership filter, so a customer cannot use transfer-service as a
     * confused deputy to reach someone else's account: a foreign account simply comes back as 404.
     */
    public AccountSnapshot fetchAccountAsUser(UUID accountId, String userAccessToken) {
        return resilience.execute(() -> call(() -> userTokenClient.get()
                .uri("/api/v1/accounts/{accountId}", accountId)
                .headers(headers -> headers.setBearerAuth(userAccessToken))
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(AccountSnapshot.class), true));
    }

    /**
     * <b>Client Credentials.</b> Posts the transfer to the internal ledger with transfer-service's own
     * machine token. Safe to retry: account-service deduplicates by {@code transferId}.
     */
    public LedgerPostingResponse postToLedger(LedgerPostingRequest request) {
        return resilience.execute(() -> call(() -> serviceClient.post()
                .uri("/internal/v1/ledger/transfers")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(LedgerPostingResponse.class), false));
    }

    private <T> T call(Supplier<T> httpCall, boolean actingAsUser) {
        try {
            return httpCall.get();
        }
        catch (RestClientResponseException ex) {
            throw translate(ex, actingAsUser);
        }
        catch (ResourceAccessException ex) {
            throw new DownstreamUnavailableException("account-service is unreachable");
        }
    }

    private RuntimeException translate(RestClientResponseException ex, boolean actingAsUser) {
        int status = ex.getStatusCode().value();
        if (status >= 500 || status == HttpStatus.TOO_MANY_REQUESTS.value()) {
            return new DownstreamUnavailableException("account-service returned " + status);
        }
        if (status == HttpStatus.NOT_FOUND.value() && actingAsUser) {
            return new SourceAccountNotFoundException();
        }
        RemoteProblem problem = readProblem(ex);
        if (status == HttpStatus.FORBIDDEN.value() && problem != null
                && OWNERSHIP_MISMATCH.equals(problem.errorCode())) {
            return new TransferRejectedException(HttpStatus.FORBIDDEN, OWNERSHIP_MISMATCH);
        }
        if (status == HttpStatus.FORBIDDEN.value() && actingAsUser) {
            return new AccessDeniedException("account-service denied access to the account");
        }
        if (status == HttpStatus.UNAUTHORIZED.value() || status == HttpStatus.FORBIDDEN.value()) {
            // Our own service credentials were rejected: a configuration problem, not the user's fault.
            LOG.error("account-service rejected transfer-service credentials (HTTP {})", status);
            return new DownstreamUnavailableException("service-to-service authentication failed");
        }
        String code = problem == null || problem.errorCode() == null ? UNKNOWN_REJECTION : problem.errorCode();
        HttpStatus mapped = HttpStatus.resolve(status);
        return new TransferRejectedException(mapped == null ? HttpStatus.UNPROCESSABLE_ENTITY : mapped, code);
    }

    private static RemoteProblem readProblem(RestClientResponseException ex) {
        try {
            return ex.getResponseBodyAs(RemoteProblem.class);
        }
        catch (RuntimeException parseFailure) {
            return null;
        }
    }
}
