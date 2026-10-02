package com.isd.wms.service;

import com.isd.wms.dto.user.UserCreateRequest;
import com.isd.wms.entity.User;
import com.isd.wms.enums.Role;
import com.isd.wms.mapper.UserMapper;
import com.isd.wms.repository.UserRepository;
import com.isd.wms.service.validation.SecurityFacade;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceReactivationSecurityTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private EmailService emailService;

    @Mock
    private UserMapper userMapper;

    @Mock
    private SecurityFacade securityFacade;

    @InjectMocks
    private UserService userService;

    private User activeSupervisor;
    private User inactiveSupervisor;
    private User inactiveOperator;

    @BeforeEach
    void setUp() {
        activeSupervisor = new User("supervisor_sam", "sam@wms.com", "encoded", Role.ROLE_SUPERVISOR, true, null, null);
        activeSupervisor.setIsActive(true);
        org.springframework.test.util.ReflectionTestUtils.setField(activeSupervisor, "id", 1L);

        inactiveSupervisor = new User("banned_supervisor", "banned_sup@wms.com", "encoded", Role.ROLE_SUPERVISOR, true, null, null);
        inactiveSupervisor.setIsActive(false);
        org.springframework.test.util.ReflectionTestUtils.setField(inactiveSupervisor, "id", 2L);

        inactiveOperator = new User("banned_operator", "banned_op@wms.com", "encoded", Role.ROLE_OPERATOR, true, null, null);
        inactiveOperator.setIsActive(false);
        org.springframework.test.util.ReflectionTestUtils.setField(inactiveOperator, "id", 3L);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(String username, String role) {
        SecurityContext sc = SecurityContextHolder.createEmptyContext();
        Authentication auth = new UsernamePasswordAuthenticationToken(username, null, List.of(new SimpleGrantedAuthority(role)));
        sc.setAuthentication(auth);
        SecurityContextHolder.setContext(sc);
    }

    @Test
    @DisplayName("Self-reactivation by username is blocked with AccessDeniedException")
    void registerUser_whenSelfReactivationAttemptedByUsername_throwsAccessDeniedException() {
        authenticateAs("supervisor_sam", "ROLE_SUPERVISOR");
        when(userRepository.findByUsername("supervisor_sam")).thenReturn(Optional.of(activeSupervisor));

        UserCreateRequest request = new UserCreateRequest("supervisor_sam", "other@wms.com", Role.ROLE_OPERATOR);

        assertThatThrownBy(() -> userService.registerUser(request))
            .isInstanceOf(AccessDeniedException.class)
            .hasMessageContaining("You cannot reactivate your own account");
    }

    @Test
    @DisplayName("Self-reactivation by email is blocked with AccessDeniedException")
    void registerUser_whenSelfReactivationAttemptedByEmail_throwsAccessDeniedException() {
        authenticateAs("supervisor_sam", "ROLE_SUPERVISOR");
        when(userRepository.findByUsername("supervisor_sam")).thenReturn(Optional.of(activeSupervisor));

        UserCreateRequest request = new UserCreateRequest("new_name", "sam@wms.com", Role.ROLE_OPERATOR);

        assertThatThrownBy(() -> userService.registerUser(request))
            .isInstanceOf(AccessDeniedException.class)
            .hasMessageContaining("You cannot reactivate your own account");
    }

    @Test
    @DisplayName("Inactive account attempting registration is blocked with AccessDeniedException")
    void registerUser_whenCallerIsInactive_throwsAccessDeniedException() {
        authenticateAs("banned_supervisor", "ROLE_SUPERVISOR");
        when(userRepository.findByUsername("banned_supervisor")).thenReturn(Optional.of(inactiveSupervisor));

        UserCreateRequest request = new UserCreateRequest("new_op", "new_op@wms.com", Role.ROLE_OPERATOR);

        assertThatThrownBy(() -> userService.registerUser(request))
            .isInstanceOf(AccessDeniedException.class)
            .hasMessageContaining("Inactive accounts cannot register or reactivate users");
    }

    @Test
    @DisplayName("Supervisor attempting to reactivate an inactive supervisor is blocked with AccessDeniedException")
    void registerUser_whenSupervisorAttemptsToReactivateSupervisor_throwsAccessDeniedException() {
        authenticateAs("supervisor_sam", "ROLE_SUPERVISOR");
        when(userRepository.findByUsername("supervisor_sam")).thenReturn(Optional.of(activeSupervisor));
        when(securityFacade.hasRole(Role.ROLE_DEV)).thenReturn(false);

        when(userRepository.findByUsername("banned_supervisor")).thenReturn(Optional.of(inactiveSupervisor));
        when(userRepository.findByEmail("banned_sup@wms.com")).thenReturn(Optional.of(inactiveSupervisor));

        UserCreateRequest request = new UserCreateRequest("banned_supervisor", "banned_sup@wms.com", Role.ROLE_OPERATOR);

        assertThatThrownBy(() -> userService.registerUser(request))
            .isInstanceOf(AccessDeniedException.class)
            .hasMessageContaining("Supervisors are not allowed to reactivate supervisor accounts");
    }

    @Test
    @DisplayName("Supervisor attempting to register a supervisor account is blocked with AccessDeniedException")
    void registerUser_whenSupervisorAttemptsToRegisterSupervisor_throwsAccessDeniedException() {
        authenticateAs("supervisor_sam", "ROLE_SUPERVISOR");
        when(userRepository.findByUsername("supervisor_sam")).thenReturn(Optional.of(activeSupervisor));
        when(securityFacade.hasRole(Role.ROLE_DEV)).thenReturn(false);

        UserCreateRequest request = new UserCreateRequest("new_sup", "new_sup@wms.com", Role.ROLE_SUPERVISOR);

        assertThatThrownBy(() -> userService.registerUser(request))
            .isInstanceOf(AccessDeniedException.class)
            .hasMessageContaining("Supervisors are only allowed to register operator accounts");
    }

    @Test
    @DisplayName("Supervisor can reactivate an operator account, which remains inactive until email verification")
    void registerUser_whenSupervisorReactivatesOperator_remainsInactiveUntilVerification() {
        authenticateAs("supervisor_sam", "ROLE_SUPERVISOR");
        when(userRepository.findByUsername("supervisor_sam")).thenReturn(Optional.of(activeSupervisor));
        when(securityFacade.hasRole(Role.ROLE_DEV)).thenReturn(false);

        when(userRepository.findByUsername("banned_operator")).thenReturn(Optional.of(inactiveOperator));
        when(userRepository.findByEmail("banned_op@wms.com")).thenReturn(Optional.of(inactiveOperator));
        when(passwordEncoder.encode(anyString())).thenReturn("hashed_temp");

        UserCreateRequest request = new UserCreateRequest("banned_operator", "banned_op@wms.com", Role.ROLE_OPERATOR);

        userService.registerUser(request);

        verify(userRepository).save(argThat(u ->
            u.getUsername().equals("banned_operator") &&
            !u.getIsActive() &&
            !u.isEmailVerified() &&
            u.getVerificationToken() != null
        ));
        verify(emailService).sendVerificationEmail(eq("banned_op@wms.com"), eq("banned_operator"), anyString());
    }

    @Test
    @DisplayName("Email verification successfully activates reactivated account")
    void verifyEmail_whenValidToken_activatesUserAndSetsPassword() {
        inactiveOperator.setVerificationToken("valid-token-123");
        inactiveOperator.setVerificationTokenExpiresAt(LocalDateTime.now().plusHours(12));

        when(userRepository.findByVerificationToken("valid-token-123")).thenReturn(Optional.of(inactiveOperator));
        when(passwordEncoder.encode("newPassword123!")).thenReturn("hashed_new_pw");

        boolean result = userService.verifyEmail("valid-token-123", "newPassword123!");

        assertThat(result).isTrue();
        assertThat(inactiveOperator.isEmailVerified()).isTrue();
        assertThat(inactiveOperator.getIsActive()).isTrue();
        assertThat(inactiveOperator.getPassword()).isEqualTo("hashed_new_pw");
        assertThat(inactiveOperator.getVerificationToken()).isNull();
        verify(userRepository).save(inactiveOperator);
    }
}
