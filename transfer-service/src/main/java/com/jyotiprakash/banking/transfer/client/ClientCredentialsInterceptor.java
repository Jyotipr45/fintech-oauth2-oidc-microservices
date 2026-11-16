/*
 * Copyright (c) 2026 Jyotiprakash Parida
 * SPDX-License-Identifier: MIT
 */
package com.jyotiprakash.banking.transfer.client;

import java.io.IOException;

import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;

import com.jyotiprakash.banking.transfer.exception.DownstreamUnavailableException;

/**
 * Adds a machine-to-machine access token (OAuth 2.0 Client Credentials grant) to outgoing requests.
 *
 * <p>The {@link OAuth2AuthorizedClientManager} obtains the token from Keycloak's token endpoint using the
 * {@code transfer-service-client} id and secret, caches it, and fetches a new one shortly before expiry.
 * The token identifies the <em>service</em>, not the end user, and carries only
 * {@code account:balance-adjust}.</p>
 *
 * @author Jyotiprakash Parida
 */
public class ClientCredentialsInterceptor implements ClientHttpRequestInterceptor {

    static final String SERVICE_PRINCIPAL = "transfer-service";

    private final OAuth2AuthorizedClientManager authorizedClientManager;
    private final String clientRegistrationId;

    public ClientCredentialsInterceptor(OAuth2AuthorizedClientManager authorizedClientManager,
                                        String clientRegistrationId) {
        this.authorizedClientManager = authorizedClientManager;
        this.clientRegistrationId = clientRegistrationId;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        OAuth2AuthorizeRequest authorizeRequest = OAuth2AuthorizeRequest
                .withClientRegistrationId(clientRegistrationId)
                .principal(SERVICE_PRINCIPAL)
                .build();
        OAuth2AuthorizedClient authorizedClient = authorizedClientManager.authorize(authorizeRequest);
        if (authorizedClient == null) {
            throw new DownstreamUnavailableException("Could not obtain a service access token");
        }
        request.getHeaders().setBearerAuth(authorizedClient.getAccessToken().getTokenValue());
        return execution.execute(request, body);
    }
}
