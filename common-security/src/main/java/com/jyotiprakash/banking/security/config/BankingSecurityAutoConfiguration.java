/*
 * Copyright (c) 2026 Jyotiprakash Parida
 * SPDX-License-Identifier: MIT
 */
package com.jyotiprakash.banking.security.config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.security.oauth2.resource.reactive.ReactiveOAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.autoconfigure.security.reactive.ReactiveUserDetailsServiceAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jyotiprakash.banking.security.authority.BankingJwtAuthenticationConverters;
import com.jyotiprakash.banking.security.error.ProblemDetailAccessDeniedHandler;
import com.jyotiprakash.banking.security.error.ProblemDetailAuthenticationEntryPoint;
import com.jyotiprakash.banking.security.error.ProblemDetailSerializer;
import com.jyotiprakash.banking.security.error.ReactiveProblemDetailAccessDeniedHandler;
import com.jyotiprakash.banking.security.error.ReactiveProblemDetailAuthenticationEntryPoint;
import com.jyotiprakash.banking.security.jwt.ReactiveJwtDecoders;
import com.jyotiprakash.banking.security.jwt.ServletJwtDecoders;
import com.jyotiprakash.banking.security.logging.MaskedAccessLogFilter;
import com.jyotiprakash.banking.security.logging.ReactiveMaskedAccessLogFilter;

/**
 * Auto-configuration activated by adding {@code common-security} to a service.
 *
 * <p>Contributes the RS256 JWT decoder, the Keycloak authority converter, RFC 7807 401/403 handlers and
 * a masked access-log filter for whichever web stack the service runs on (servlet resource servers or
 * the reactive gateway). Each service still declares its own {@code SecurityFilterChain} so that its
 * authorization rules stay explicit and reviewable next to its endpoints.</p>
 *
 * <p>Ordered before Spring Boot's resource-server auto-configuration so that Boot never builds a second,
 * less strict decoder from {@code spring.security.oauth2.resourceserver.*} properties, and before the
 * user-details auto-configurations so that, once a JWT decoder exists, Boot never creates its default
 * in-memory user with a generated password.</p>
 *
 * @author Jyotiprakash Parida
 */
@AutoConfiguration(after = JacksonAutoConfiguration.class,
        before = {OAuth2ResourceServerAutoConfiguration.class, ReactiveOAuth2ResourceServerAutoConfiguration.class,
                UserDetailsServiceAutoConfiguration.class, ReactiveUserDetailsServiceAutoConfiguration.class})
@EnableConfigurationProperties(BankingSecurityProperties.class)
public class BankingSecurityAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ProblemDetailSerializer problemDetailSerializer(ObjectProvider<ObjectMapper> objectMapper) {
        return new ProblemDetailSerializer(objectMapper.getIfAvailable(ObjectMapper::new));
    }

    /**
     * Servlet stack: account-service and transfer-service.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnClass(name = "jakarta.servlet.Filter")
    static class ServletSecurityConfiguration {

        @Bean
        @ConditionalOnMissingBean(JwtDecoder.class)
        JwtDecoder jwtDecoder(BankingSecurityProperties properties) {
            return ServletJwtDecoders.create(properties);
        }

        @Bean
        @ConditionalOnMissingBean(JwtAuthenticationConverter.class)
        JwtAuthenticationConverter jwtAuthenticationConverter(BankingSecurityProperties properties) {
            return BankingJwtAuthenticationConverters.create(properties);
        }

        @Bean
        @ConditionalOnMissingBean(AuthenticationEntryPoint.class)
        AuthenticationEntryPoint problemDetailAuthenticationEntryPoint(ProblemDetailSerializer serializer) {
            return new ProblemDetailAuthenticationEntryPoint(serializer);
        }

        @Bean
        @ConditionalOnMissingBean(AccessDeniedHandler.class)
        AccessDeniedHandler problemDetailAccessDeniedHandler(ProblemDetailSerializer serializer) {
            return new ProblemDetailAccessDeniedHandler(serializer);
        }

        @Bean
        @ConditionalOnProperty(prefix = "banking.security.request-logging", name = "enabled",
                havingValue = "true", matchIfMissing = true)
        FilterRegistrationBean<MaskedAccessLogFilter> maskedAccessLogFilter() {
            FilterRegistrationBean<MaskedAccessLogFilter> registration =
                    new FilterRegistrationBean<>(new MaskedAccessLogFilter());
            // Runs before the Spring Security filter chain so rejected (401/403) requests are logged too.
            registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
            registration.addUrlPatterns("/*");
            return registration;
        }
    }

    /**
     * Reactive stack: api-gateway.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
    @ConditionalOnClass(name = "reactor.netty.http.client.HttpClient")
    static class ReactiveSecurityConfiguration {

        @Bean
        @ConditionalOnMissingBean(ReactiveJwtDecoder.class)
        ReactiveJwtDecoder reactiveJwtDecoder(BankingSecurityProperties properties) {
            return ReactiveJwtDecoders.create(properties);
        }

        @Bean
        @ConditionalOnMissingBean(ReactiveJwtAuthenticationConverterAdapter.class)
        ReactiveJwtAuthenticationConverterAdapter reactiveJwtAuthenticationConverter(
                BankingSecurityProperties properties) {
            return new ReactiveJwtAuthenticationConverterAdapter(BankingJwtAuthenticationConverters.create(properties));
        }

        @Bean
        @ConditionalOnMissingBean(ServerAuthenticationEntryPoint.class)
        ServerAuthenticationEntryPoint reactiveProblemDetailAuthenticationEntryPoint(
                ProblemDetailSerializer serializer) {
            return new ReactiveProblemDetailAuthenticationEntryPoint(serializer);
        }

        @Bean
        @ConditionalOnMissingBean(ServerAccessDeniedHandler.class)
        ServerAccessDeniedHandler reactiveProblemDetailAccessDeniedHandler(ProblemDetailSerializer serializer) {
            return new ReactiveProblemDetailAccessDeniedHandler(serializer);
        }

        @Bean
        @ConditionalOnProperty(prefix = "banking.security.request-logging", name = "enabled",
                havingValue = "true", matchIfMissing = true)
        ReactiveMaskedAccessLogFilter reactiveMaskedAccessLogFilter() {
            return new ReactiveMaskedAccessLogFilter();
        }
    }
}
