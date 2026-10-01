package com.isd.wms.service;

import com.isd.wms.exception.InvalidCredentialsException;
import com.isd.wms.exception.UserNotVerifiedException;
import com.isd.wms.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private UserDetailsService userDetailsService;

    @Mock
    private JwtUtil jwtUtil;

    @InjectMocks
    private AuthService authService;

    private UserDetails userDetails;

    @BeforeEach
    void setUp() {
        userDetails = new User(
                "john_doe",
                "encoded_pass",
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_OPERATOR"))
        );
    }

    @Test
    @DisplayName("Active user with correct credentials receives generated JWT")
    void authenticateAndGenerateToken_WhenCredentialsValid_ShouldReturnToken() {
        when(userDetailsService.loadUserByUsername("john_doe")).thenReturn(userDetails);
        when(jwtUtil.generateToken("john_doe", "ROLE_OPERATOR")).thenReturn("test-token");

        String token = authService.authenticateAndGenerateToken("john_doe", "correct_pass");

        assertThat(token).isEqualTo("test-token");
        verify(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
    }

    @Test
    @DisplayName("Inactive user must be rejected with DisabledException")
    void authenticateAndGenerateToken_WhenUserInactive_ShouldThrowDisabledException() {
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new DisabledException("Account is inactive for user: john_doe"));

        assertThatThrownBy(() -> authService.authenticateAndGenerateToken("john_doe", "pass"))
                .isInstanceOf(DisabledException.class)
                .hasMessage("Account is inactive.");
    }

    @Test
    @DisplayName("Unverified user must be rejected with UserNotVerifiedException")
    void authenticateAndGenerateToken_WhenUserEmailNotVerified_ShouldThrowUserNotVerifiedException() {
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new DisabledException("Email not verified for user: john_doe"));

        assertThatThrownBy(() -> authService.authenticateAndGenerateToken("john_doe", "pass"))
                .isInstanceOf(UserNotVerifiedException.class)
                .hasMessage("Please verify your email before logging in.");
    }

    @Test
    @DisplayName("Bad credentials must be rejected with InvalidCredentialsException")
    void authenticateAndGenerateToken_WhenBadCredentials_ShouldThrowInvalidCredentialsException() {
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        assertThatThrownBy(() -> authService.authenticateAndGenerateToken("john_doe", "wrong_pass"))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Incorrect username or password.");
    }
}
