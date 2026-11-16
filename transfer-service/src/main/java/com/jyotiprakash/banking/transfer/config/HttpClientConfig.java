/*
 * Copyright (c) 2026 Jyotiprakash Parida
 * SPDX-License-Identifier: MIT
 */
package com.jyotiprakash.banking.transfer.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProvider;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.web.client.RestClient;

import com.jyotiprakash.banking.transfer.client.ClientCredentialsInterceptor;

/**
 * Two deliberately separate HTTP clients for account-service:
 *
 * <ol>
 *   <li>{@code userTokenAccountClient} &mdash; <b>token propagation</b>. The caller supplies the end user's
 *       own access token per request, so account-service applies that user's roles and ownership rules.
 *       Used for reads made on the user's behalf.</li>
 *   <li>{@code serviceAccountClient} &mdash; <b>Client Credentials</b>. Every request carries
 *       transfer-service's own machine token. Used for the privileged ledger posting that no end user is
 *       allowed to call directly.</li>
 * </ol>
 *
 * @author Jyotiprakash Parida
 */
@Configuration(proxyBeanMethods = false)
public class HttpClientConfig {

    @Bean
    public OAuth2AuthorizedClientManager serviceAuthorizedClientManager(
            ClientRegistrationRepository clientRegistrations, OAuth2AuthorizedClientService authorizedClients) {
        OAuth2AuthorizedClientProvider provider = OAuth2AuthorizedClientProviderBuilder.builder()
                .clientCredentials()
                .build();
        AuthorizedClientServiceOAuth2AuthorizedClientManager manager =
                new AuthorizedClientServiceOAuth2AuthorizedClientManager(clientRegistrations, authorizedClients);
        manager.setAuthorizedClientProvider(provider);
        return manager;
    }

    @Bean
    @Qualifier("userTokenAccountClient")
    public RestClient userTokenAccountClient(RestClient.Builder builder, TransferProperties properties) {
        return builder.clone()
                .baseUrl(properties.accountServiceUri())
                .requestFactory(requestFactory(properties))
                .build();
    }

    @Bean
    @Qualifier("serviceAccountClient")
    public RestClient serviceAccountClient(RestClient.Builder builder, TransferProperties properties,
                                           OAuth2AuthorizedClientManager serviceAuthorizedClientManager) {
        return builder.clone()
                .baseUrl(properties.accountServiceUri())
                .requestFactory(requestFactory(properties))
                .requestInterceptor(new ClientCredentialsInterceptor(serviceAuthorizedClientManager,
                        properties.clientRegistrationId()))
                .build();
    }

    private static SimpleClientHttpRequestFactory requestFactory(TransferProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.http().connectTimeout());
        factory.setReadTimeout(properties.http().readTimeout());
        return factory;
    }
}
