package com.isd.wms.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtUtilTest {

    private static final String SECURE_PROD_SECRET =
        "super_secure_production_secret_key_that_is_at_least_32_bytes_long_1234567890";

    @Test
    void constructor_inProductionProfileWithDefaultSecret_throwsIllegalStateException() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");

        assertThatThrownBy(() -> new JwtUtil(JwtUtil.DEFAULT_DEV_SECRET, true, env))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Production startup aborted: default JWT secret key cannot be used in production profile");
    }

    @Test
    void constructor_inProductionProfileWithCookieSecureFalse_throwsIllegalStateException() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");

        assertThatThrownBy(() -> new JwtUtil(SECURE_PROD_SECRET, false, env))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Production startup aborted: wms.jwt.cookie-secure must be true in production profile");
    }

    @Test
    void constructor_inProductionProfileWithSecureSecretAndCookieSecureTrue_succeeds() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");

        JwtUtil jwtUtil = new JwtUtil(SECURE_PROD_SECRET, true, env);
        assertThat(jwtUtil).isNotNull();
        assertThat(jwtUtil.isCookieSecure()).isTrue();

        String token = jwtUtil.generateToken("admin", "ROLE_DEV");
        assertThat(jwtUtil.extractUsername(token)).isEqualTo("admin");
        assertThat(jwtUtil.extractRole(token)).isEqualTo("ROLE_DEV");
        assertThat(jwtUtil.validateToken(token, "admin")).isTrue();
    }

    @Test
    void constructor_inDevProfileWithCookieSecureFalse_succeeds() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("dev");

        JwtUtil jwtUtil = new JwtUtil(JwtUtil.DEFAULT_DEV_SECRET, false, env);
        assertThat(jwtUtil).isNotNull();
        assertThat(jwtUtil.isCookieSecure()).isFalse();

        String token = jwtUtil.generateToken("user1", "ROLE_OPERATOR");
        assertThat(jwtUtil.extractUsername(token)).isEqualTo("user1");
        assertThat(jwtUtil.extractRole(token)).isEqualTo("ROLE_OPERATOR");
    }

    @Test
    void constructor_withCompromisedHistoricalSecret_throwsIllegalStateException() {
        MockEnvironment env = new MockEnvironment();
        String compromisedSecret = new String(
            java.util.Base64.getDecoder().decode("ZjhnSDlzSzJtTjVwUThyVjF2VzR4WjdhQmNEZUZnSGlKa0xtTm9QcVJzVHVWd1h5WjAxMjM0NTY3ODlhQmNEZUY="),
            java.nio.charset.StandardCharsets.UTF_8
        );

        assertThatThrownBy(() -> new JwtUtil(compromisedSecret, false, env))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("known compromised historical key fingerprint");
    }

    @Test
    void constructor_withShortSecret_throwsIllegalStateException() {
        MockEnvironment env = new MockEnvironment();

        assertThatThrownBy(() -> new JwtUtil("short-secret-under-32-bytes", false, env))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("at least 32 bytes");
    }

    @Test
    void constructor_withNullOrBlankSecret_throwsIllegalStateException() {
        MockEnvironment env = new MockEnvironment();

        assertThatThrownBy(() -> new JwtUtil(null, false, env))
            .isInstanceOf(IllegalStateException.class);

        assertThatThrownBy(() -> new JwtUtil("   ", false, env))
            .isInstanceOf(IllegalStateException.class);
    }
}
