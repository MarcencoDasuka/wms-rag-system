package com.isd.wms.security;

import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JwtUtilTest {

    private static final String SECURE_PROD_SECRET =
        "super_secure_production_secret_key_that_is_at_least_32_bytes_long_1234567890";

    @Test
    void constructor_inProductionProfileWithDefaultSecret_throwsIllegalStateException() {
        Environment env = mock(Environment.class);
        when(env.acceptsProfiles(any(Profiles.class))).thenReturn(true);

        assertThatThrownBy(() -> new JwtUtil(JwtUtil.DEFAULT_DEV_SECRET, env))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Production startup aborted");
    }

    @Test
    void constructor_inProductionProfileWithSecureSecret_succeeds() {
        Environment env = mock(Environment.class);
        when(env.acceptsProfiles(any(Profiles.class))).thenReturn(true);

        JwtUtil jwtUtil = new JwtUtil(SECURE_PROD_SECRET, env);
        assertThat(jwtUtil).isNotNull();

        String token = jwtUtil.generateToken("admin", "ROLE_DEV");
        assertThat(jwtUtil.extractUsername(token)).isEqualTo("admin");
        assertThat(jwtUtil.extractRole(token)).isEqualTo("ROLE_DEV");
        assertThat(jwtUtil.validateToken(token, "admin")).isTrue();
    }

    @Test
    void constructor_inDevProfileWithDefaultSecret_succeeds() {
        Environment env = mock(Environment.class);
        when(env.acceptsProfiles(any(Profiles.class))).thenReturn(false);

        JwtUtil jwtUtil = new JwtUtil(JwtUtil.DEFAULT_DEV_SECRET, env);
        assertThat(jwtUtil).isNotNull();

        String token = jwtUtil.generateToken("user1", "ROLE_OPERATOR");
        assertThat(jwtUtil.extractUsername(token)).isEqualTo("user1");
        assertThat(jwtUtil.extractRole(token)).isEqualTo("ROLE_OPERATOR");
    }

    @Test
    void constructor_withCompromisedHistoricalSecret_throwsIllegalStateException() {
        Environment env = mock(Environment.class);
        String compromisedSecret = new String(
            java.util.Base64.getDecoder().decode("ZjhnSDlzSzJtTjVwUThyVjF2VzR4WjdhQmNEZUZnSGlKa0xtTm9QcVJzVHVWd1h5WjAxMjM0NTY3ODlhQmNEZUY="),
            java.nio.charset.StandardCharsets.UTF_8
        );

        assertThatThrownBy(() -> new JwtUtil(compromisedSecret, env))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("known compromised historical key fingerprint");
    }

    @Test
    void constructor_withShortSecret_throwsIllegalStateException() {
        Environment env = mock(Environment.class);

        assertThatThrownBy(() -> new JwtUtil("short-secret-under-32-bytes", env))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("at least 32 bytes");
    }

    @Test
    void constructor_withNullOrBlankSecret_throwsIllegalStateException() {
        Environment env = mock(Environment.class);

        assertThatThrownBy(() -> new JwtUtil(null, env))
            .isInstanceOf(IllegalStateException.class);

        assertThatThrownBy(() -> new JwtUtil("   ", env))
            .isInstanceOf(IllegalStateException.class);
    }
}
