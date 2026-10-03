package com.isd.wms.controller;

import com.isd.wms.entity.User;
import com.isd.wms.service.AuthService;
import com.isd.wms.service.UserService;
import com.isd.wms.service.validation.SecurityFacade;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * REST controller for authentication and account verification operations.
 *
 * <p>Provides public endpoints for user login, logout, account verification,
 * and an authenticated endpoint for fetching current user profile.</p>
 *
 * <p>Base path: {@code /api/auth}</p>
 */
@RestController
@RequestMapping("/api/auth")
@Slf4j
public class AuthController {

    public static final String JWT_COOKIE_NAME = "jwt_token";
    private static final Duration JWT_COOKIE_MAX_AGE = Duration.ofHours(24);

    private final AuthService authService;
    private final UserService userService;
    private final SecurityFacade securityFacade;
    private final boolean cookieSecure;

    @Autowired
    public AuthController(
            AuthService authService,
            UserService userService,
            SecurityFacade securityFacade,
            @Value("${wms.jwt.cookie-secure:false}") boolean cookieSecure,
            Environment environment) {
        this.authService = authService;
        this.userService = userService;
        this.securityFacade = securityFacade;
        this.cookieSecure = cookieSecure;

        if (environment != null && environment.acceptsProfiles(Profiles.of("prod", "production")) && !cookieSecure) {
            throw new IllegalStateException(
                "Production startup aborted: wms.jwt.cookie-secure must be true in production profile. " +
                "Set WMS_JWT_COOKIE_SECURE=true."
            );
        }
    }

    public AuthController(
            AuthService authService,
            UserService userService,
            SecurityFacade securityFacade,
            boolean cookieSecure) {
        this(authService, userService, securityFacade, cookieSecure, null);
    }

    /**
     * Authenticates a user, issues an HttpOnly JWT cookie, and returns a JWT token on success.
     *
     * <p>The request body must contain {@code username} (username or email) and
     * {@code password} fields.</p>
     *
     * @param loginRequest a map containing {@code username} and {@code password}
     * @param response     the HttpServletResponse to attach the HttpOnly cookie
     * @return {@code 200 OK} with a map containing the generated {@code token}
     */
    @PostMapping("/login")
    public ResponseEntity<Map<String, String>> login(
            @RequestBody Map<String, String> loginRequest,
            HttpServletResponse response) {
        String usernameOrEmail = loginRequest.get("username");
        String password = loginRequest.get("password");

        log.info("Authentication attempt for user/email: {}", usernameOrEmail);

        String token = authService.authenticateAndGenerateToken(usernameOrEmail, password);

        ResponseCookie cookie = ResponseCookie.from(JWT_COOKIE_NAME, token)
                .httpOnly(true)
                .secure(cookieSecure)
                .path("/")
                .maxAge(JWT_COOKIE_MAX_AGE)
                .sameSite("Lax")
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());

        log.info("Authentication successful! Token generated and HttpOnly cookie attached for user: {}", usernameOrEmail);

        return ResponseEntity.ok(Map.of("token", token));
    }

    /**
     * Logs out the user by clearing the HttpOnly JWT cookie.
     *
     * @param response the HttpServletResponse to clear the cookie
     * @return {@code 200 OK} with logout confirmation
     */
    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(JWT_COOKIE_NAME, "")
                .httpOnly(true)
                .secure(cookieSecure)
                .path("/")
                .maxAge(0)
                .sameSite("Lax")
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());

        log.info("User session logged out, {} cookie invalidated", JWT_COOKIE_NAME);
        return ResponseEntity.ok(Map.of("message", "Logged out successfully"));
    }

    /**
     * Retrieves the profile information of the currently authenticated user.
     *
     * @return {@code 200 OK} with user id, username, role, and email
     */
    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> getCurrentUser() {
        User user = securityFacade.getCurrentUser();
        Map<String, Object> userData = new HashMap<>();
        userData.put("id", user.getId());
        userData.put("username", user.getUsername());
        userData.put("role", user.getUserRole() != null ? (user.getUserRole().name().startsWith("ROLE_") ? user.getUserRole().name() : "ROLE_" + user.getUserRole().name()) : "");
        userData.put("email", user.getEmail() != null ? user.getEmail() : "");
        return ResponseEntity.ok(userData);
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
