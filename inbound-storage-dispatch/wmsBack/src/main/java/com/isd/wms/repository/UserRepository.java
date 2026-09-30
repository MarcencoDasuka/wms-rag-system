package com.isd.wms.repository;

import com.isd.wms.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository for {@link User} entities.
 * <p>
 * Provides methods for finding users by username, email, verification token,
 * and retrieving active users or expired unverified users.
 * </p>
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * Finds a user by username.
     *
     * @param username the username
     * @return an Optional containing the user, if found
     */
    Optional<User> findByUsername(String username);

    /**
     * Finds a user by email.
     *
     * @param email the email
     * @return an Optional containing the user, if found
     */
    Optional<User> findByEmail(String email);

    /**
     * Finds a user by their verification token.
     *
     * @param verificationToken the token
     * @return an Optional containing the user, if found
     */
    Optional<User> findByVerificationToken(String verificationToken);

    /**
     * Finds all active users (isActive = true).
     *
     * @return list of active users
     */
    List<User> findAllByIsActiveTrue();

    /**
     * Finds all users whose email is not verified and whose verification token
     * has expired before the given time.
     * Used for scheduled cleanup of expired unverified accounts.
     *
     * @param now the current time
     * @return list of expired unverified users
     */
    List<User> findAllByEmailVerifiedFalseAndVerificationTokenExpiresAtBefore(LocalDateTime now);
}
