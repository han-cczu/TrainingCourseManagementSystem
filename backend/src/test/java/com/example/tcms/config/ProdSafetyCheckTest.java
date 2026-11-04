package com.example.tcms.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.Test;

class ProdSafetyCheckTest {

    private static final String STRONG_SECRET = "k8sSecret-abcdefghijklmnopqrstuvwxyz012345";

    private static ProdSafetyCheck check(String secret, String adminPassword) {
        return new ProdSafetyCheck(new JwtProperties(secret, Duration.ofHours(8)),
                new AdminProperties("admin", adminPassword));
    }

    @Test
    void rejectsDevDefaultJwtSecret() {
        assertThatThrownBy(() -> check("dev-only-jwt-secret-change-me-0123456789abcdef", "Strong123x").afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET");
    }

    @Test
    void rejectsDevDefaultAdminPassword() {
        assertThatThrownBy(() -> check(STRONG_SECRET, "Admin12345").afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ADMIN_PASSWORD");
    }

    @Test
    void acceptsRealSecrets() {
        assertThatCode(() -> check(STRONG_SECRET, "Strong123x").afterPropertiesSet()).doesNotThrowAnyException();
    }
}
