package com.isd.wms.controller;

import com.isd.wms.service.AuthService;
import com.isd.wms.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST controller for authentication and account verification operations.
 *
 * <p>Provides public endpoints for user login and email verification.
 * Successful login returns a JWT token; email verification activates a newly
 * registered account using a one-time token.</p>
 *
 * <p>Base path: {@code /api/auth}</p>
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final AuthService authService;
    private final UserService userService;

    /**
     * Authenticates a user and returns a JWT token on success.
     *
     * <p>The request body must contain {@code username} (username or email) and
     * {@code password} fields.</p>
     *
     * @param loginRequest a map containing {@code username} and {@code password}
     * @return {@code 200 OK} with a map containing the generated {@code token}
     */
    @PostMapping("/login")
    public ResponseEntity<Map<String, String>> login(@RequestBody Map<String, String> loginRequest) {
        String usernameOrEmail = loginRequest.get("username");
        String password = loginRequest.get("password");

        log.info("Authentication attempt for user/email: {}", usernameOrEmail);

        String token = authService.authenticateAndGenerateToken(usernameOrEmail, password);

        log.info("Authentication successful! Token is generated for user: {}", usernameOrEmail);

        return ResponseEntity.ok(Map.of("token", token));
    }

    /**
     * Verifies a user's email address and activates their account.
     *
     * <p>The request body must contain a one-time {@code token} (sent via email)
     * and the user's chosen {@code password}. If verification succeeds the account
     * is activated; if the token has expired the unverified account is removed.</p>
     *
     * @param payload a map containing {@code token} and {@code password}
     * @return {@code 200 OK} with a success message if verified;
     *         {@code 400 Bad Request} with an error message if the token is invalid,
     *         expired, or the password is missing
     */
    @PostMapping("/verify")
    public ResponseEntity<Map<String, String>> verifyEmail(@RequestBody Map<String, String> payload) {
        String token = payload.get("token");
        String password = payload.get("password");

        if (password == null || password.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Password is required"));
        }

        try {
            boolean isVerified = userService.verifyEmail(token, password);
            if (isVerified) {
                log.info("Email verified successfully for token: {}", token);
                return ResponseEntity.ok(Map.of("message", "Account activated successfully! You can now log in."));
            } else {
                return ResponseEntity.badRequest().body(Map.of(
                    "error", "The verification link has expired. The unverified account has been removed."
                ));
            }
        } catch (RuntimeException e) {
            log.warn("Email verification failed: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
