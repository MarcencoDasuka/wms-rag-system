package com.isd.wms.service;

import com.isd.wms.dto.user.UserCreateRequest;
import com.isd.wms.dto.user.UserResponse;
import com.isd.wms.dto.user.UserUpdateRequest;
import com.isd.wms.enums.Role;
import com.isd.wms.entity.User;
import com.isd.wms.mapper.UserMapper;
import com.isd.wms.repository.UserRepository;
import com.isd.wms.service.validation.SecurityFacade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for managing user accounts.
 * <p>
 * Handles user registration, email verification, account updates, deletion
 * (soft deactivation), and retrieval of active users. Registration sends a
 * verification email with a token; upon verification, the user sets a password.
 * </p>
 * <p>
 * Only users with DEV role can update other users' details. Supervisors can
 * only deactivate operator accounts. Deactivation of one's own account is prohibited.
 * </p>
 * <p>
 * A scheduled job cleans up unverified accounts whose tokens have expired.
 * </p>
 *
 * @see User
 * @see EmailService
 * @see SecurityFacade
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final UserMapper userMapper;
    private final SecurityFacade securityFacade;

    /**
     * Registers a new user or reactivates a deactivated account.
     * <p>
     * If the username or email already exists but the account is inactive,
     * it is reactivated and a new verification email is sent.
     * </p>
     *
     * @param request the registration request
     * @throws AccessDeniedException if attempting to create a DEV account
     * @throws RuntimeException if the username/email is already taken by an active account
     */
    @Transactional
    public void registerUser(UserCreateRequest request) {
        if (request.userRole() == Role.ROLE_DEV) {
            log.warn("Security block: Attempt to create a DEV account via API.");
            throw new AccessDeniedException("Creating DEV accounts via API is strictly prohibited.");
        }

        Optional<User> existingUserOpt = userRepository.findByUsername(request.username());
        Optional<User> existingEmailOpt = userRepository.findByEmail(request.email());

        String verificationToken = UUID.randomUUID().toString();
        String temporaryPassword = UUID.randomUUID().toString();

        User userToSave;

        if (existingUserOpt.isPresent()) {
            User user = existingUserOpt.get();
            if (user.getIsActive()) {
                throw new RuntimeException("This username is already taken by an active user.");
            }
            if (existingEmailOpt.isPresent() && !existingEmailOpt.get().getId().equals(user.getId())) {
                throw new RuntimeException("This email belongs to a different deactivated account. " +
                    "Please use a unique combination.");
            }
            userToSave = user;
            log.info("Reactivating deactivated account for username: {}", request.username());

        } else if (existingEmailOpt.isPresent()) {
            User user = existingEmailOpt.get();
            if (user.getIsActive()) {
                throw new RuntimeException("This email is already registered to an active user.");
            }
            userToSave = user;
            log.info("Reactivating deactivated account for email: {}", request.email());

        } else {
            userToSave = new User(
                request.username(),
                request.email(),
                passwordEncoder.encode(temporaryPassword),
                request.userRole(),
                false,
                verificationToken,
                LocalDateTime.now().plusHours(24)
            );
            userToSave.setIsActive(true);
            userRepository.save(userToSave);

            emailService.sendVerificationEmail(request.email(), request.username(), verificationToken);
            log.info("User '{}' registered, verification email sent to {}", request.username(), request.email());
            return;
        }

        userToSave.setUsername(request.username());
        userToSave.setEmail(request.email());
        userToSave.setPassword(passwordEncoder.encode(temporaryPassword));
        userToSave.setUserRole(request.userRole());
        userToSave.setIsActive(true);
        userToSave.setEmailVerified(false);
        userToSave.setVerificationToken(verificationToken);
        userToSave.setVerificationTokenExpiresAt(LocalDateTime.now().plusHours(24));

        userRepository.save(userToSave);

        emailService.sendVerificationEmail(request.email(), request.username(), verificationToken);
        log.info("User '{}' successfully reactivated, new verification email sent to {}",
            request.username(), request.email());
    }

    @Transactional
    public void updateUser(Long userId, UserUpdateRequest request) {

        if (!securityFacade.hasRole(Role.ROLE_DEV)) {
            log.warn("Security block: Non-DEV user attempted to update user ID {}", userId);
            throw new AccessDeniedException("Only Developers are allowed to update user information.");
        }

        User existingUser = userRepository.findById(userId)
            .orElseThrow(() -> new RuntimeException("User not found"));

        if (!existingUser.getUsername().equals(request.username())) {
            Optional<User> found = userRepository.findByUsername(request.username());
            if (found.isPresent()) {
                if (found.get().getIsActive()) {
                    throw new RuntimeException("This username is already taken by another active user.");
                } else {
                    throw new RuntimeException("This username belongs to a deactivated account. Please choose another.");
                }
            }
        }

        if (request.userRole() == Role.ROLE_DEV && existingUser.getUserRole() != Role.ROLE_DEV) {
            log.warn("Security block: Attempt to escalate a user to ROLE_DEV.");
            throw new AccessDeniedException("Promoting an account to ROLE_DEV is strictly prohibited.");
        }

        existingUser.setUsername(request.username());
        existingUser.setUserRole(request.userRole());

        log.info("User ID {} successfully updated. New username: {}, New role: {}",
            userId, request.username(), request.userRole());

        userRepository.save(existingUser);
    }

    /**
     * Verifies the user's email using a token and sets a new password.
     *
     * @param rawToken the verification token
     * @param newPassword the new password to set
     * @return true if verification succeeded, false if the token expired
     * @throws RuntimeException if the token is invalid
     */
    @Transactional
    public boolean verifyEmail(String rawToken, String newPassword) {
        String cleanToken = rawToken != null ? rawToken.trim() : "";

        log.info("Attempting to verify email with token: [{}]", cleanToken);

        User user = userRepository.findByVerificationToken(cleanToken)
            .orElseThrow(() -> {
                log.error("Token not found in database: [{}]", cleanToken);
                return new RuntimeException("Invalid verification token.");
            });

        if (user.getVerificationTokenExpiresAt().isBefore(LocalDateTime.now())) {
            String username = user.getUsername();
            userRepository.delete(user);
            log.info("Expired token. Deleted unverified user '{}'", username);
            return false;
        }

        user.setEmailVerified(true);
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setVerificationToken(null);
        user.setVerificationTokenExpiresAt(null);
        userRepository.save(user);

        log.info("Email verified for user '{}'", user.getUsername());

        return true;
    }

    /**
     * Scheduled job to delete expired unverified accounts.
     */
    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void cleanUpExpiredUnverifiedUsers() {
        List<User> expiredUsers =
            userRepository.findAllByEmailVerifiedFalseAndVerificationTokenExpiresAtBefore(LocalDateTime.now());

        if (!expiredUsers.isEmpty()) {
            userRepository.deleteAll(expiredUsers);

            log.info("Background Job: Successfully deleted {} expired unverified accounts.", expiredUsers.size());
        }
    }

    /**
     * Soft‑deletes (deactivates) a user account.
     *
     * @param userId the ID of the user to deactivate
     * @throws AccessDeniedException if the current user lacks permission
     * @throws RuntimeException if trying to delete own account
     */
    @Transactional
    public void deleteUser(Long userId) {
        User targetUser = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (targetUser.getUsername().equals(securityFacade.getCurrentUsername())) {
            throw new RuntimeException("You cannot ban your own account.");
        }

        boolean isDev = securityFacade.hasRole(Role.ROLE_DEV);
        boolean isSupervisor = securityFacade.hasRole(Role.ROLE_SUPERVISOR);

        if (!isDev && !isSupervisor) {
            log.warn("Security block: Unauthorized user attempted to ban user ID {}", userId);
            throw new AccessDeniedException("You do not have permission to ban users.");
        }

        if (isSupervisor && !isDev && targetUser.getUserRole() != Role.ROLE_OPERATOR) {
            log.warn("Security block: SUPERVISOR attempted to ban a non-OPERATOR account.");
            throw new AccessDeniedException("Supervisors are only allowed to ban operator accounts.");
        }

        targetUser.setIsActive(false);
        userRepository.save(targetUser);

        log.info("User '{}' (ID: {}) was successfully deleted by {}",
                targetUser.getUsername(), userId, securityFacade.getCurrentUsername());
    }

    public List<UserResponse> getAllUsers() {
        return userRepository.findAllByIsActiveTrue().stream()
                .map(userMapper::toResponse)
                .toList();
    }
}
