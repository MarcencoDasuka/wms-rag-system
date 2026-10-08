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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class Def15AsyncEmailDispatchRemediationTest {

    private UserRepository userRepository;
    private EmailService emailService;
    private UserService userService;

    private final Map<String, User> databaseUsers = new ConcurrentHashMap<>();
    private final AtomicBoolean emailSent = new AtomicBoolean(false);
    private final AtomicInteger emailSendAttempts = new AtomicInteger(0);
    private final AtomicBoolean smtpFailureSimulated = new AtomicBoolean(false);
    private final List<String> capturedEmails = new ArrayList<>();

    @SuppressWarnings("unchecked")
    private static <T> T createProxy(Class<T> type, java.lang.reflect.InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler);
    }

    @BeforeEach
    void setUp() {
        databaseUsers.clear();
        emailSent.set(false);
        emailSendAttempts.set(0);
        smtpFailureSimulated.set(false);
        capturedEmails.clear();

        userRepository = createProxy(UserRepository.class, (proxy, method, args) -> {
            String name = method.getName();
            if ("findByUsernameIgnoreCase".equals(name) || "findByUsername".equals(name)) {
                String username = (String) args[0];
                return Optional.ofNullable(databaseUsers.get(username.toLowerCase()));
            }
            if ("findByEmailIgnoreCase".equals(name) || "findByEmail".equals(name)) {
                String email = (String) args[0];
                return databaseUsers.values().stream()
                    .filter(u -> email.equalsIgnoreCase(u.getEmail()))
                    .findFirst();
            }
            if ("save".equals(name)) {
                User u = (User) args[0];
                if (u.getId() == null) {
                    ReflectionTestUtils.setField(u, "id", (long) (databaseUsers.size() + 1));
                }
                databaseUsers.put(u.getUsername().toLowerCase(), u);
                return u;
            }
            return null;
        });

        // EmailService subclass without JavaMail network dependency
        emailService = new EmailService(null, null) {
            @Override
            public void sendVerificationEmail(String toEmail, String username, String token) {
                emailSendAttempts.incrementAndGet();
                if (smtpFailureSimulated.get()) {
                    throw new RuntimeException("Simulated SMTP Connection Timeout / MailException (Pool Starvation Avoided)");
                }
                emailSent.set(true);
                capturedEmails.add(toEmail);
            }
        };

        PasswordEncoder passwordEncoder = new PasswordEncoder() {
            @Override
            public String encode(CharSequence rawPassword) {
                return "hash_" + rawPassword;
            }

            @Override
            public boolean matches(CharSequence rawPassword, String encodedPassword) {
                return encodedPassword.equals("hash_" + rawPassword);
            }
        };

        SecurityFacade securityFacade = new SecurityFacade(null) {
            @Override
            public boolean hasRole(Role roleName) {
                return false; // Supervisor
            }

            @Override
            public String getCurrentUsername() {
                return "supervisor_sam";
            }
        };

        UserMapper userMapper = new UserMapper();

        userService = new UserService(
            userRepository,
            passwordEncoder,
            emailService,
            userMapper,
            securityFacade
        );
    }

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    @DisplayName("DEF-15 Invariant 1 & 2 Proof: Email is NOT sent before DB commit, and IS sent after successful commit")
    void registerUser_withinActiveTransaction_dispatchesEmailOnlyAfterCommit() {
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);

        UserCreateRequest request = new UserCreateRequest("operator_alice", "alice@wms.com", Role.ROLE_OPERATOR);
        userService.registerUser(request);

        // INVARIANT 1: Before commit, email is strictly NOT sent (no HikariCP holding during network SMTP)
        assertThat(emailSent.get())
            .as("Email must NOT be sent before database transaction commit")
            .isFalse();
        assertThat(emailSendAttempts.get()).isEqualTo(0);

        // Trigger commit phase
        for (TransactionSynchronization sync : TransactionSynchronizationManager.getSynchronizations()) {
            sync.afterCommit();
        }

        // INVARIANT 2: After successful commit, email sending is triggered
        assertThat(emailSent.get())
            .as("Email sending must be executed after database transaction commit")
            .isTrue();
        assertThat(emailSendAttempts.get()).isEqualTo(1);
        assertThat(capturedEmails).containsExactly("alice@wms.com");
    }

    @Test
    @DisplayName("DEF-15 Invariant 3 Proof: On transaction rollback, email is NEVER sent")
    void registerUser_onTransactionRollback_doesNotSendEmail() {
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);

        UserCreateRequest request = new UserCreateRequest("operator_bob", "bob@wms.com", Role.ROLE_OPERATOR);
        userService.registerUser(request);

        assertThat(emailSent.get()).isFalse();

        // Simulate transaction rollback (afterCommit is NOT invoked, only afterCompletion with ROLLED_BACK)
        for (TransactionSynchronization sync : TransactionSynchronizationManager.getSynchronizations()) {
            sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
        }

        // INVARIANT 3: On rollback, email is never sent
        assertThat(emailSent.get())
            .as("Email must NOT be sent if transaction was rolled back")
            .isFalse();
        assertThat(emailSendAttempts.get()).isEqualTo(0);
    }

    @Test
    @DisplayName("DEF-15 Invariant 4 Proof: SMTP failure does NOT roll back or corrupt already committed database state")
    void registerUser_onSmtpFailurePostCommit_preservesCommittedDatabaseState() {
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        smtpFailureSimulated.set(true); // SMTP server unreachable

        UserCreateRequest request = new UserCreateRequest("operator_charlie", "charlie@wms.com", Role.ROLE_OPERATOR);
        userService.registerUser(request);

        // User was persisted in repository
        assertThat(databaseUsers).containsKey("operator_charlie");
        User savedUser = databaseUsers.get("operator_charlie");
        assertThat(savedUser.getEmail()).isEqualTo("charlie@wms.com");

        // Simulate afterCommit execution when SMTP fails
        assertThatCode(() -> {
            for (TransactionSynchronization sync : TransactionSynchronizationManager.getSynchronizations()) {
                sync.afterCommit();
            }
        }).as("SMTP failure post-commit must be handled gracefully without throwing unhandled exceptions")
          .doesNotThrowAnyException();

        // INVARIANT 4: SMTP failure does not alter or undo committed database state
        assertThat(databaseUsers)
            .as("User must remain safely committed in database despite SMTP network error")
            .containsKey("operator_charlie");
        assertThat(databaseUsers.get("operator_charlie").getUsername()).isEqualTo("operator_charlie");
        assertThat(emailSendAttempts.get()).isEqualTo(1);
        assertThat(emailSent.get()).isFalse();
    }

    @Test
    @DisplayName("DEF-15 Fallback Proof: When invoked outside active transaction, email is dispatched directly")
    void registerUser_whenNoActiveTransaction_dispatchesEmailDirectly() {
        TransactionSynchronizationManager.setActualTransactionActive(false);

        UserCreateRequest request = new UserCreateRequest("operator_dave", "dave@wms.com", Role.ROLE_OPERATOR);
        userService.registerUser(request);

        assertThat(emailSent.get())
            .as("Email should be dispatched directly when no transaction synchronization is active")
            .isTrue();
        assertThat(databaseUsers).containsKey("operator_dave");
    }
}
