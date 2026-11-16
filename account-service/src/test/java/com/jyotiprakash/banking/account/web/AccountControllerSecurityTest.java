/*
 * Copyright (c) 2026 Jyotiprakash Parida
 * SPDX-License-Identifier: MIT
 */
package com.jyotiprakash.banking.account.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.jyotiprakash.banking.account.config.SecurityConfig;
import com.jyotiprakash.banking.account.domain.Account;
import com.jyotiprakash.banking.account.domain.AccountType;
import com.jyotiprakash.banking.account.exception.AccountNotFoundException;
import com.jyotiprakash.banking.account.service.AccountService;
import com.jyotiprakash.banking.account.service.LedgerService;
import com.jyotiprakash.banking.security.config.BankingSecurityAutoConfiguration;

/**
 * HTTP-level checks of 401 vs 403 vs 404 (BOLA) behaviour with the real security filter chain.
 *
 * @author Jyotiprakash Parida
 */
@WebMvcTest(controllers = {AccountController.class, InternalLedgerController.class})
@Import(SecurityConfig.class)
@ImportAutoConfiguration(BankingSecurityAutoConfiguration.class)
@TestPropertySource(properties = {
        "banking.security.issuer-uri=http://keycloak:8080/realms/banking-realm",
        "banking.security.jwk-set-uri=http://keycloak:8080/realms/banking-realm/protocol/openid-connect/certs",
        "banking.security.audiences=account-service"
})
class AccountControllerSecurityTest {

    private static final String ALICE = "11111111-1111-1111-1111-111111111111";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private AccountService accountService;

    @MockitoBean
    private LedgerService ledgerService;

    @Test
    void missingTokenIs401ProblemWithBearerChallenge() throws Exception {
        mvc.perform(get("/api/v1/accounts/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer realm=\"banking\""))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void customerListingAllAccountsIs403() throws Exception {
        mvc.perform(get("/api/v1/accounts").with(customer()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("insufficient_scope"));
    }

    @Test
    void customerReadingSomeoneElsesAccountIs404NotForbidden() throws Exception {
        UUID foreignAccount = UUID.randomUUID();
        when(accountService.getAccount(foreignAccount)).thenThrow(new AccountNotFoundException());

        mvc.perform(get("/api/v1/accounts/{id}", foreignAccount).with(customer()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("account_not_found"));
    }

    @Test
    void customerReadsOwnAccounts() throws Exception {
        when(accountService.listMyAccounts())
                .thenReturn(List.of(Account.open("BNK000000000001", ALICE, AccountType.SAVINGS, "INR")));

        mvc.perform(get("/api/v1/accounts/me").with(customer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].accountNumber").value("BNK000000000001"))
                .andExpect(jsonPath("$[0].ownerSubject").doesNotExist());
    }

    @Test
    void userTokenCannotCallInternalLedger() throws Exception {
        mvc.perform(post("/internal/v1/ledger/transfers").with(customer())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void openAccountRejectsUnknownFieldsSuchAsOwner() throws Exception {
        when(accountService.openAccount(any(), any()))
                .thenReturn(Account.open("BNK000000000003", ALICE, AccountType.SAVINGS, "INR"));

        mvc.perform(post("/api/v1/accounts").with(customer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"SAVINGS\",\"currency\":\"INR\",\"ownerSubject\":\"someone-else\"}"))
                .andExpect(status().isBadRequest());

        mvc.perform(post("/api/v1/accounts").with(customer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"SAVINGS\",\"currency\":\"INR\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"));
    }

    private static RequestPostProcessor customer() {
        return jwt().jwt(token -> token.subject(ALICE))
                .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"),
                        new SimpleGrantedAuthority("account:read"),
                        new SimpleGrantedAuthority("account:write"),
                        new SimpleGrantedAuthority("transfer:create"));
    }
}
