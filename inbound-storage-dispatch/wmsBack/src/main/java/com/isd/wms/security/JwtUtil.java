package com.isd.wms.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.function.Function;

@Component
@Slf4j
public class JwtUtil {

    public static final String DEFAULT_DEV_SECRET = "default_jwt_dev_secret_key_must_be_changed_in_production_32bytes_min";
    public static final String COMPROMISED_HISTORICAL_SECRET_FINGERPRINT =
        "c14ac8f0beed73c045836cd2694ffeec2fda62091c1daf95d458a81f8022ce98";

    private final SecretKey SECRET_KEY;
    private final long JWT_EXPIRATION_TIME = 86400000; // 24 hours
    private final boolean cookieSecure;

    public JwtUtil(
        @Value("${wms.jwt.secret}") String secretString,
        @Value("${wms.jwt.cookie-secure:false}") boolean cookieSecure,
        Environment environment
    ) {
        if (secretString == null || secretString.isBlank()) {
            throw new IllegalStateException("CRITICAL: JWT secret string is empty or null!");
        }

        byte[] secretBytes = secretString.getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < 32) {
            throw new IllegalStateException("CRITICAL: JWT secret key must be at least 32 bytes (256 bits) long!");
        }

        String secretFingerprint = computeSha256Hex(secretBytes);
        if (MessageDigest.isEqual(
                secretFingerprint.getBytes(StandardCharsets.UTF_8),
                COMPROMISED_HISTORICAL_SECRET_FINGERPRINT.getBytes(StandardCharsets.UTF_8))) {
            throw new IllegalStateException(
                "CRITICAL: The configured JWT secret matches a known compromised historical key fingerprint and cannot be used. " +
                "Please configure a newly generated, secure JWT_SECRET."
            );
        }

        this.cookieSecure = cookieSecure;

        if (environment != null && environment.acceptsProfiles(Profiles.of("prod", "production"))) {
            if (DEFAULT_DEV_SECRET.equals(secretString)) {
                throw new IllegalStateException(
                    "Production startup aborted: default JWT secret key cannot be used in production profile. " +
                    "Set a secure JWT_SECRET environment variable."
                );
            }
            if (!cookieSecure) {
                throw new IllegalStateException(
                    "Production startup aborted: wms.jwt.cookie-secure must be true in production profile. " +
                    "Set WMS_JWT_COOKIE_SECURE=true."
                );
            }
        } else if (DEFAULT_DEV_SECRET.equals(secretString)) {
            log.warn("WARNING: Using default development JWT secret key. Ensure JWT_SECRET is configured for production!");
        }

        this.SECRET_KEY = Keys.hmacShaKeyFor(secretBytes);
        log.info("JwtUtil initialized. Secret key loaded successfully.");
    }

    public JwtUtil(String secretString, Environment environment) {
        this(secretString, false, environment);
    }

    public boolean isCookieSecure() {
        return cookieSecure;
    }

    private static String computeSha256Hex(byte[] inputBytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(inputBytes);
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available in current JVM", e);
        }
    }

    public String generateToken(String username, String role) {
        log.debug("Generating JWT token for user: '{}' with role: '{}'", username, role);
        String token = Jwts.builder()
                .setSubject(username)
                .claim("role", role)
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + JWT_EXPIRATION_TIME))
                .signWith(SECRET_KEY)
                .compact();
        log.trace("Token generated successfully for '{}'. Preview: {}...", username, token.substring(0, Math.min(token.length(), 15)));
        return token;
    }

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public String extractRole(String token) {
        return extractClaim(token, claims -> claims.get("role", String.class));
    }

    public boolean validateToken(String token, String username) {
        try {
            final String extractedUsername = extractUsername(token);
            boolean isUsernameValid = extractedUsername.equals(username);
            boolean isExpired = isTokenExpired(token);

            if (!isUsernameValid) {
                log.warn("Token validation failed: Extracted username '{}' does not match expected username '{}'", extractedUsername, username);
            }
            if (isExpired) {
                log.warn("Token validation failed: Token for user '{}' is expired.", username);
            }

            boolean isValid = isUsernameValid && !isExpired;
            log.debug("Token validation result for '{}': {}", username, isValid);
            return isValid;
        } catch (Exception e) {
            log.error("Token validation failed due to an unexpected exception for user '{}'", username, e);
            return false;
        }
    }

    private boolean isTokenExpired(String token) {
        Date expiration = extractClaim(token, Claims::getExpiration);
        boolean expired = expiration.before(new Date());
        if (expired) {
            log.trace("Token expiration check: Token expired at {}", expiration);
        }
        return expired;
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        try {
            final Claims claims = Jwts.parser()
                    .verifyWith(SECRET_KEY)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return claimsResolver.apply(claims);
        } catch (io.jsonwebtoken.security.SignatureException e) {
            log.error("JWT signature validation failed! The token has been tampered with or secret key is incorrect.");
            throw e;
        } catch (io.jsonwebtoken.ExpiredJwtException e) {
            log.warn("JWT token extraction failed: Token is expired.");
            throw e;
        } catch (io.jsonwebtoken.MalformedJwtException e) {
            log.error("JWT token extraction failed: Token string is malformed.");
            throw e;
        } catch (Exception e) {
            log.error("Failed to parse JWT claims due to an unexpected error.", e);
            throw e;
        }
    }
}