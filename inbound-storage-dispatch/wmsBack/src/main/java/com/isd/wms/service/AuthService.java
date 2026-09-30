package com.isd.wms.service;


import com.isd.wms.exception.InvalidCredentialsException;
import com.isd.wms.exception.UserNotVerifiedException;
import com.isd.wms.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Service;

/**
 * Service responsible for user authentication and JWT token generation.
 *
 * <p>Delegates credential verification to Spring Security's {@link AuthenticationManager}.
 * On success, loads the full {@link UserDetails} and generates a signed JWT containing
 * the username and role. Converts Spring Security authentication exceptions into
 * domain-specific exceptions for cleaner API error responses.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserDetailsService userDetailsService;
    private final JwtUtil jwtUtil;

    /**
     * Authenticates the given credentials and returns a signed JWT on success.
     *
     * @param username the username or email address of the user attempting to log in
     * @param password the user's plain-text password
     * @return a signed JWT string containing the username and role authority
     * @throws UserNotVerifiedException    if the account exists but the email has not been verified
     * @throws InvalidCredentialsException if the username or password is incorrect
     */
    public String authenticateAndGenerateToken(String username, String password) {
        try {
            authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(username, password)
            );
        } catch (DisabledException e) {
            throw new UserNotVerifiedException("Please verify your email before logging in.");
        } catch (BadCredentialsException e) {
            throw new InvalidCredentialsException("Incorrect username or password.");
        }

        UserDetails userDetails = userDetailsService.loadUserByUsername(username);
        return jwtUtil.generateToken(
            userDetails.getUsername(),
            userDetails.getAuthorities().iterator().next().getAuthority()
        );
    }
}
