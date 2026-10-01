package com.isd.wms.service;

import com.isd.wms.entity.User;
import com.isd.wms.enums.Role;
import com.isd.wms.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomUserDetailsServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private CustomUserDetailsService userDetailsService;

    private User activeUser;
    private User inactiveUser;

    @BeforeEach
    void setUp() {
        activeUser = new User(
                "active_user",
                "active@wms.com",
                "encoded_password",
                Role.ROLE_OPERATOR,
                true,
                null,
                null
        );
        activeUser.setIsActive(true);

        inactiveUser = new User(
                "banned_user",
                "banned@wms.com",
                "encoded_password",
                Role.ROLE_OPERATOR,
                true,
                null,
                null
        );
        inactiveUser.setIsActive(false);
    }

    @Test
    @DisplayName("Active and email verified user should successfully load UserDetails with enabled=true")
    void loadUserByUsername_WhenUserIsActiveAndEmailVerified_ShouldReturnEnabledUserDetails() {
        when(userRepository.findByUsername("active_user")).thenReturn(Optional.of(activeUser));

        UserDetails userDetails = userDetailsService.loadUserByUsername("active_user");

        assertThat(userDetails).isNotNull();
        assertThat(userDetails.getUsername()).isEqualTo("active_user");
        assertThat(userDetails.getPassword()).isEqualTo("encoded_password");
        assertThat(userDetails.isEnabled()).isTrue();
        assertThat(userDetails.getAuthorities()).extracting("authority").containsExactly("ROLE_OPERATOR");
    }

    @Test
    @DisplayName("Inactive user must be rejected with DisabledException even if email is verified")
    void loadUserByUsername_WhenUserIsInactive_ShouldThrowDisabledException() {
        when(userRepository.findByUsername("banned_user")).thenReturn(Optional.of(inactiveUser));

        assertThatThrownBy(() -> userDetailsService.loadUserByUsername("banned_user"))
                .isInstanceOf(DisabledException.class)
                .hasMessageContaining("inactive");
    }

    @Test
    @DisplayName("User with null isActive flag must be treated as inactive and rejected")
    void loadUserByUsername_WhenUserHasNullIsActive_ShouldThrowDisabledException() {
        User nullActiveUser = new User(
                "null_active",
                "null@wms.com",
                "pass",
                Role.ROLE_OPERATOR,
                true,
                null,
                null
        );
        nullActiveUser.setIsActive(null);

        when(userRepository.findByUsername("null_active")).thenReturn(Optional.of(nullActiveUser));

        assertThatThrownBy(() -> userDetailsService.loadUserByUsername("null_active"))
                .isInstanceOf(DisabledException.class)
                .hasMessageContaining("inactive");
    }

    @Test
    @DisplayName("Active user without email verification must be rejected with DisabledException")
    void loadUserByUsername_WhenUserEmailNotVerified_ShouldThrowDisabledException() {
        User unverifiedUser = new User(
                "unverified_user",
                "unverified@wms.com",
                "pass",
                Role.ROLE_OPERATOR,
                false,
                "token",
                LocalDateTime.now().plusDays(1)
        );
        unverifiedUser.setIsActive(true);

        when(userRepository.findByUsername("unverified_user")).thenReturn(Optional.of(unverifiedUser));

        assertThatThrownBy(() -> userDetailsService.loadUserByUsername("unverified_user"))
                .isInstanceOf(DisabledException.class)
                .hasMessageContaining("Email not verified");
    }

    @Test
    @DisplayName("Loading non-existent user should throw UsernameNotFoundException")
    void loadUserByUsername_WhenUserNotFound_ShouldThrowUsernameNotFoundException() {
        when(userRepository.findByUsername("unknown")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userDetailsService.loadUserByUsername("unknown"))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessageContaining("User not found: unknown");
    }

    @Test
    @DisplayName("Active user should be resolved by email fallback if username lookup misses")
    void loadUserByUsername_WhenUsernameNotFoundButEmailMatches_ShouldReturnEnabledUserDetails() {
        when(userRepository.findByUsername("active@wms.com")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("active@wms.com")).thenReturn(Optional.of(activeUser));

        UserDetails userDetails = userDetailsService.loadUserByUsername("active@wms.com");

        assertThat(userDetails).isNotNull();
        assertThat(userDetails.getUsername()).isEqualTo("active_user");
        assertThat(userDetails.isEnabled()).isTrue();
    }
}
