/*
 * Copyright (c) 2026 Jyotiprakash Parida
 * SPDX-License-Identifier: MIT
 */
package com.jyotiprakash.banking.security.logging;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * @author Jyotiprakash Parida
 */
class SensitiveDataMaskerTest {

    private static final String JWT =
            "eyJhbGciOiJSUzI1NiIsImtpZCI6ImsxIn0.eyJzdWIiOiJ1c2VyLTEiLCJleHAiOjE3MDAwMDAwMDB9.c2lnbmF0dXJlLXZhbHVl";

    @Test
    void masksBearerHeader() {
        String masked = SensitiveDataMasker.mask("Authorization: Bearer " + JWT);
        assertThat(masked).doesNotContain(JWT).doesNotContain("eyJ").contains("[REDACTED]");
    }

    @Test
    void masksBasicCredentials() {
        assertThat(SensitiveDataMasker.mask("header Basic dHJhbnNmZXI6c2VjcmV0"))
                .isEqualTo("header Basic [REDACTED]");
    }

    @Test
    void masksJsonAndFormSecrets() {
        String json = "{\"access_token\":\"abc.def.ghi\",\"refresh_token\":\"xyz\",\"expires_in\":300}";
        assertThat(SensitiveDataMasker.mask(json))
                .doesNotContain("abc.def.ghi").doesNotContain("xyz").contains("\"expires_in\":300");

        String form = "grant_type=client_credentials&client_secret=s3cr3t-value&scope=openid";
        assertThat(SensitiveDataMasker.mask(form))
                .isEqualTo("grant_type=client_credentials&client_secret=[REDACTED]&scope=openid");
    }

    @Test
    void masksBareJwtAnywhereInText() {
        assertThat(SensitiveDataMasker.mask("token was " + JWT + " for request"))
                .isEqualTo("token was [REDACTED] for request");
    }

    @Test
    void leavesOrdinaryTextUntouched() {
        String text = "GET /api/v1/accounts/42 -> 200 (12 ms)";
        assertThat(SensitiveDataMasker.mask(text)).isEqualTo(text);
        assertThat(SensitiveDataMasker.mask(null)).isNull();
    }

    @Test
    void correlationIdsRejectInjection() {
        assertThat(CorrelationIds.resolve("abc12345-valid")).isEqualTo("abc12345-valid");
        assertThat(CorrelationIds.resolve("bad\nINFO forged log line")).doesNotContain("\n");
        assertThat(CorrelationIds.resolve(null)).hasSize(36);
    }
}
