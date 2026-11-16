/*
 * Copyright (c) 2026 Jyotiprakash Parida
 * SPDX-License-Identifier: MIT
 */
package com.jyotiprakash.banking.transfer.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

import com.jyotiprakash.banking.security.error.ProblemDetailSerializer;
import com.jyotiprakash.banking.transfer.security.HighRiskOperationGuardFilter;
import com.jyotiprakash.banking.transfer.security.TokenRevocationService;
import com.jyotiprakash.banking.transfer.security.TransferRateLimiter;

import jakarta.servlet.DispatcherType;

/**
 * Stateless resource-server configuration for transfer-service, plus the high-risk guard (jti revocation
 * and velocity limit) placed directly after Bearer-token authentication.
 *
 * <p>CSRF is disabled: Bearer tokens in the {@code Authorization} header are not sent automatically by
 * browsers, so there is no ambient credential for a cross-site request to abuse.</p>
 *
 * @author Jyotiprakash Parida
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private static final String CONTENT_SECURITY_POLICY = "default-src 'none'; frame-ancestors 'none'";

    @Bean
    public SecurityFilterChain transferSecurityFilterChain(HttpSecurity http,
                                                           JwtAuthenticationConverter jwtAuthenticationConverter,
                                                           AuthenticationEntryPoint authenticationEntryPoint,
                                                           AccessDeniedHandler accessDeniedHandler,
                                                           TokenRevocationService tokenRevocationService,
                                                           TransferRateLimiter transferRateLimiter,
                                                           ProblemDetailSerializer problemDetailSerializer)
            throws Exception {
        HighRiskOperationGuardFilter highRiskGuard = new HighRiskOperationGuardFilter(
                tokenRevocationService, transferRateLimiter, authenticationEntryPoint, problemDetailSerializer);

        http
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(headers -> headers
                        .frameOptions(frame -> frame.deny())
                        .contentSecurityPolicy(csp -> csp.policyDirectives(CONTENT_SECURITY_POLICY))
                        .referrerPolicy(referrer -> referrer.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER)))
                .authorizeHttpRequests(authorize -> authorize
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().denyAll())
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter))
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .addFilterAfter(highRiskGuard, BearerTokenAuthenticationFilter.class)
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler));
        return http.build();
    }
}
