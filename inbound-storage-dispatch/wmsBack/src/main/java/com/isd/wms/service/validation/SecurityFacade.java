package com.isd.wms.service.validation;

import com.isd.wms.entity.User;
import com.isd.wms.enums.Role;
import com.isd.wms.exception.UserNotFoundException;
import com.isd.wms.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Facade for accessing security context information in a convenient, testable way.
 * <p>
 * Provides methods to retrieve the currently authenticated username, the full
 * {@link User} entity, and to check if the current user has a specific role.
 * This centralises security-related operations and avoids duplicating
 * {@link org.springframework.security.core.context.SecurityContextHolder} calls.
 * </p>
 *
 * @see User
 * @see Role
 * @see org.springframework.security.core.Authentication
 */
@Component
@RequiredArgsConstructor
public class SecurityFacade {

    private final UserRepository userRepository;

    /**
     * Returns the username of the currently authenticated user.
     *
     * @return the username
     * @throws RuntimeException if no authentication is found or the user is not authenticated
     */
    public String getCurrentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new RuntimeException("No authenticated user found");
        }
        return authentication.getName();
    }

    /**
     * Returns the full {@link User} entity of the currently authenticated user.
     *
     * @return the User object
     * @throws UserNotFoundException if the user cannot be found in the database
     */
    public User getCurrentUser() {
        String username = getCurrentUsername();
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException(username));
    }

    /**
     * Checks if the current user has the specified role.
     *
     * @param roleName the role to check (e.g., {@link Role#ROLE_DEV})
     * @return true if the user has the role, false otherwise
     */
    public boolean hasRole(Role roleName) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return false;
        }
        return auth.getAuthorities().stream()
            .anyMatch(a -> Objects.equals(a.getAuthority(), roleName.name()));
    }
}
