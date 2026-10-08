package com.isd.wms.service;

import com.isd.wms.dto.user.UserCreateRequest;
import com.isd.wms.dto.user.UserUpdateRequest;
import com.isd.wms.entity.User;
import com.isd.wms.enums.Role;
import com.isd.wms.mapper.UserMapper;
import com.isd.wms.repository.UserRepository;
import com.isd.wms.service.validation.SecurityFacade;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Proxy;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Def20CaseInsensitiveUserRemediationTest {

    private UserRepository userRepository;
    private CustomUserDetailsService customUserDetailsService;
    private UserService userService;
    private final Map<Long, User> database = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(100);

    @SuppressWarnings("unchecked")
    private static <T> T createProxy(Class<T> type, java.lang.reflect.InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler);
    }

    @BeforeEach
    void setUp() {
        database.clear();
        SecurityContextHolder.clearContext();

        userRepository = createProxy(UserRepository.class, (proxy, method, args) -> {
            String name = method.getName();
            if ("findByUsernameIgnoreCase".equals(name)) {
                String u = (String) args[0];
                return database.values().stream()
                    .filter(user -> user.getUsername().equalsIgnoreCase(u))
                    .findFirst();
            }
            if ("findByUsername".equals(name)) {
                String u = (String) args[0];
                return database.values().stream()
                    .filter(user -> user.getUsername().equals(u))
                    .findFirst();
            }
            if ("findByEmailIgnoreCase".equals(name)) {
                String e = (String) args[0];
                return database.values().stream()
                    .filter(user -> user.getEmail() != null && user.getEmail().equalsIgnoreCase(e))
                    .findFirst();
            }
            if ("findByEmail".equals(name)) {
                String e = (String) args[0];
                return database.values().stream()
                    .filter(user -> user.getEmail() != null && user.getEmail().equals(e))
                    .findFirst();
            }
            if ("existsByUsernameIgnoreCase".equals(name)) {
                String u = (String) args[0];
                return database.values().stream().anyMatch(user -> user.getUsername().equalsIgnoreCase(u));
            }
            if ("existsByEmailIgnoreCase".equals(name)) {
                String e = (String) args[0];
                return database.values().stream().anyMatch(user -> user.getEmail() != null && user.getEmail().equalsIgnoreCase(e));
            }
            if ("findById".equals(name)) {
                Long id = (Long) args[0];
                return Optional.ofNullable(database.get(id));
            }
            if ("save".equals(name)) {
                User u = (User) args[0];
                if (u.getId() == null) {
                    ReflectionTestUtils.setField(u, "id", idGenerator.incrementAndGet());
                }
                database.put(u.getId(), u);
                return u;
            }
            return null;
        });

        customUserDetailsService = new CustomUserDetailsService(userRepository);

        EmailService noopEmailService = new EmailService(null, null) {
            @Override
            public void sendVerificationEmail(String toEmail, String username, String token) {
                // No-op for unit test
            }
        };

        PasswordEncoder passwordEncoder = new PasswordEncoder() {
            @Override
            public String encode(CharSequence rawPassword) {
                return "encoded_" + rawPassword;
            }

            @Override
            public boolean matches(CharSequence rawPassword, String encodedPassword) {
                return encodedPassword.equals("encoded_" + rawPassword);
            }
        };

        SecurityFacade securityFacade = new SecurityFacade(null) {
            @Override
            public boolean hasRole(Role roleName) {
                return roleName == Role.ROLE_DEV;
            }

            @Override
            public String getCurrentUsername() {
                return "dev_admin";
            }
        };

        UserMapper userMapper = new UserMapper();

        userService = new UserService(
            userRepository,
            passwordEncoder,
            noopEmailService,
            userMapper,
            securityFacade
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private User createTestUser(Long id, String username, String email, Role role, boolean active, boolean verified) {
        User u = new User();
        ReflectionTestUtils.setField(u, "id", id);
        u.setUsername(username);
        u.setEmail(email);
        u.setPassword("hashed_secret");
        u.setUserRole(role);
        u.setIsActive(active);
        u.setEmailVerified(verified);
        u.setVerificationTokenExpiresAt(LocalDateTime.now().plusDays(1));
        database.put(id, u);
        return u;
    }

    @Test
    @DisplayName("DEF-20 Proof: CustomUserDetailsService resolves username case-insensitively")
    void customUserDetailsService_loadsUser_byUsernameWithAnyCase() {
        createTestUser(1L, "Operator_Alice", "Alice@wms.com", Role.ROLE_OPERATOR, true, true);

        UserDetails lowerCaseLookup = customUserDetailsService.loadUserByUsername("operator_alice");
        assertThat(lowerCaseLookup.getUsername()).isEqualTo("Operator_Alice");

        UserDetails upperCaseLookup = customUserDetailsService.loadUserByUsername("OPERATOR_ALICE");
        assertThat(upperCaseLookup.getUsername()).isEqualTo("Operator_Alice");

        UserDetails mixedCaseLookup = customUserDetailsService.loadUserByUsername("OpErAtOr_AlIcE");
        assertThat(mixedCaseLookup.getUsername()).isEqualTo("Operator_Alice");
    }

    @Test
    @DisplayName("DEF-20 Proof: CustomUserDetailsService resolves email case-insensitively")
    void customUserDetailsService_loadsUser_byEmailWithAnyCase() {
        createTestUser(2L, "Supervisor_Bob", "Bob.Warehouse@wms.com", Role.ROLE_SUPERVISOR, true, true);

        UserDetails lowerCaseLookup = customUserDetailsService.loadUserByUsername("bob.warehouse@wms.com");
        assertThat(lowerCaseLookup.getUsername()).isEqualTo("Supervisor_Bob");

        UserDetails upperCaseLookup = customUserDetailsService.loadUserByUsername("BOB.WAREHOUSE@WMS.COM");
        assertThat(upperCaseLookup.getUsername()).isEqualTo("Supervisor_Bob");
    }

    @Test
    @DisplayName("DEF-20 Proof: CustomUserDetailsService throws DisabledException if matched user is inactive or unverified")
    void customUserDetailsService_throwsDisabledException_forInactiveOrUnverifiedUser() {
        createTestUser(3L, "Inactive_User", "inactive@wms.com", Role.ROLE_OPERATOR, false, true);
        createTestUser(4L, "Unverified_User", "unverified@wms.com", Role.ROLE_OPERATOR, true, false);

        assertThatThrownBy(() -> customUserDetailsService.loadUserByUsername("inactive_user"))
            .isInstanceOf(DisabledException.class)
            .hasMessageContaining("Account is inactive");

        assertThatThrownBy(() -> customUserDetailsService.loadUserByUsername("unverified_user"))
            .isInstanceOf(DisabledException.class)
            .hasMessageContaining("Email not verified");
    }

    @Test
    @DisplayName("DEF-20 Proof: CustomUserDetailsService throws UsernameNotFoundException when user does not exist")
    void customUserDetailsService_throwsUsernameNotFoundException_whenUserNotFound() {
        assertThatThrownBy(() -> customUserDetailsService.loadUserByUsername("non_existent_user"))
            .isInstanceOf(UsernameNotFoundException.class)
            .hasMessageContaining("User not found: non_existent_user");
    }

    @Test
    @DisplayName("DEF-20 Proof: UserService prevents duplicate registration with differing username casing")
    void registerUser_preventsDuplicateRegistration_caseInsensitiveUsername() {
        createTestUser(10L, "WarehouseAdmin", "admin@wms.com", Role.ROLE_OPERATOR, true, true);

        UserCreateRequest request = new UserCreateRequest("warehouseadmin", "newadmin@wms.com", Role.ROLE_OPERATOR);

        assertThatThrownBy(() -> userService.registerUser(request))
            .isInstanceOf(RuntimeException.class)
            .hasMessage("This username is already taken by an active user.");
    }

    @Test
    @DisplayName("DEF-20 Proof: UserService prevents duplicate registration with differing email casing")
    void registerUser_preventsDuplicateRegistration_caseInsensitiveEmail() {
        createTestUser(11L, "Manager_Mark", "Mark.Warehouse@wms.com", Role.ROLE_OPERATOR, true, true);

        UserCreateRequest request = new UserCreateRequest("new_mark", "mark.warehouse@wms.com", Role.ROLE_OPERATOR);

        assertThatThrownBy(() -> userService.registerUser(request))
            .isInstanceOf(RuntimeException.class)
            .hasMessage("This email is already registered to an active user.");
    }

    @Test
    @DisplayName("DEF-20 Proof: UserService reactivates deactivated account matching username case-insensitively")
    void registerUser_reactivatesDeactivatedAccount_caseInsensitive() {
        createTestUser(12L, "Deactivated_Dave", "dave@wms.com", Role.ROLE_OPERATOR, false, true);

        UserCreateRequest request = new UserCreateRequest("deactivated_dave", "dave@wms.com", Role.ROLE_OPERATOR);
        userService.registerUser(request);

        User reactivatedUser = database.get(12L);
        assertThat(reactivatedUser).isNotNull();
        assertThat(reactivatedUser.getVerificationToken()).isNotNull();
        assertThat(reactivatedUser.getIsActive()).isFalse(); // pending verification
    }

    @Test
    @DisplayName("DEF-20 Proof: UserService prevents self-reactivation case-insensitively")
    void registerUser_preventsSelfReactivation_caseInsensitive() {
        createTestUser(13L, "Self_Sam", "sam@wms.com", Role.ROLE_OPERATOR, true, true);

        // Simulate authenticated user calling register
        Authentication auth = createProxy(Authentication.class, (p, m, a) -> {
            if ("getName".equals(m.getName())) return "self_sam"; // lowercase in principal
            if ("isAuthenticated".equals(m.getName())) return true;
            return null;
        });
        SecurityContext secContext = createProxy(SecurityContext.class, (p, m, a) -> "getAuthentication".equals(m.getName()) ? auth : null);
        SecurityContextHolder.setContext(secContext);

        UserCreateRequest request = new UserCreateRequest("Self_Sam", "sam@wms.com", Role.ROLE_OPERATOR);

        assertThatThrownBy(() -> userService.registerUser(request))
            .isInstanceOf(AccessDeniedException.class)
            .hasMessageContaining("You cannot reactivate your own account");
    }

    @Test
    @DisplayName("DEF-20 Proof: Inactive authenticated user cannot register or reactivate any account case-insensitively")
    void registerUser_preventsInactiveUserFromRegistering_caseInsensitive() {
        createTestUser(17L, "Inactive_Caller", "caller@wms.com", Role.ROLE_OPERATOR, false, true);

        Authentication auth = createProxy(Authentication.class, (p, m, a) -> {
            if ("getName".equals(m.getName())) return "inactive_caller";
            if ("isAuthenticated".equals(m.getName())) return true;
            return null;
        });
        SecurityContext secContext = createProxy(SecurityContext.class, (p, m, a) -> "getAuthentication".equals(m.getName()) ? auth : null);
        SecurityContextHolder.setContext(secContext);

        UserCreateRequest request = new UserCreateRequest("new_worker", "new_worker@wms.com", Role.ROLE_OPERATOR);

        assertThatThrownBy(() -> userService.registerUser(request))
            .isInstanceOf(AccessDeniedException.class)
            .hasMessageContaining("Inactive accounts cannot register or reactivate users");
    }

    @Test
    @DisplayName("DEF-20 Proof: UserService updateUser prevents username collision with differing casing")
    void updateUser_preventsCollision_caseInsensitive() {
        createTestUser(14L, "Existing_Elena", "elena@wms.com", Role.ROLE_OPERATOR, true, true);
        createTestUser(15L, "Other_User", "other@wms.com", Role.ROLE_OPERATOR, true, true);

        UserUpdateRequest request = new UserUpdateRequest("existing_elena", Role.ROLE_OPERATOR);

        assertThatThrownBy(() -> userService.updateUser(15L, request))
            .isInstanceOf(RuntimeException.class)
            .hasMessage("This username is already taken by another active user.");
    }

    @Test
    @DisplayName("DEF-20 Proof: UserService updateUser allows changing casing of user's own username")
    void updateUser_allowsCasingChangeOfOwnUsername() {
        createTestUser(16L, "lowercase_frank", "frank@wms.com", Role.ROLE_OPERATOR, true, true);

        UserUpdateRequest request = new UserUpdateRequest("LowerCase_Frank", Role.ROLE_OPERATOR);
        userService.updateUser(16L, request);

        User updatedUser = database.get(16L);
        assertThat(updatedUser.getUsername()).isEqualTo("LowerCase_Frank");
    }
}
