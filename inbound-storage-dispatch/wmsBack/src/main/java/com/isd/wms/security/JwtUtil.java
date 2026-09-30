package com.isd.wms.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.function.Function;

@Component
@Slf4j
public class JwtUtil {

    private final SecretKey SECRET_KEY;
    private final long JWT_EXPIRATION_TIME = 86400000; // 24 hours

    public JwtUtil(@Value("${wms.jwt.secret}") String secretString) {
        if (secretString == null || secretString.isBlank()) {
            log.error("CRITICAL: JWT secret string from application.properties is empty or null!");
        } else {
            log.info("JwtUtil initialized. Secret key loaded successfully.");
        }
        this.SECRET_KEY = Keys.hmacShaKeyFor(secretString.getBytes());
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