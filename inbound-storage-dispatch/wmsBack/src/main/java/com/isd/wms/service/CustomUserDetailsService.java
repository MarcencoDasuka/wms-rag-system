package com.isd.wms.service;

import com.isd.wms.entity.User;
import com.isd.wms.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Collections;

/**
 * Spring Security {@link UserDetailsService} implementation that loads user details
 * by username or email.
 * <p>
 * This service is used during authentication to retrieve user credentials and roles.
 * It also enforces email verification: only users with verified email addresses
 * are allowed to authenticate.
 * </p>
 *
 * @see User
 * @see UserRepository
 * @see org.springframework.security.core.userdetails.UserDetailsService
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    /**
     * Locates the user based on the provided username or email and builds a
     * {@link UserDetails} object.
     * <p>
     * The identifier is first treated as a username; if not found, it is treated as
     * an email. If the user exists but has not verified their email, a
     * {@link DisabledException} is thrown.
     * </p>
     *
     * @param usernameOrEmail the username or email of the user
     * @return the UserDetails object with authorities
     * @throws UsernameNotFoundException if no user is found
     * @throws DisabledException if the email is not verified
     */
    @Override
    public @NonNull UserDetails loadUserByUsername(@NonNull String usernameOrEmail) throws UsernameNotFoundException {
        log.debug("Attempting to load user details for identifier: '{}'", usernameOrEmail);

        User user = userRepository.findByUsername(usernameOrEmail)
                .or(() -> userRepository.findByEmail(usernameOrEmail))
                .orElseThrow(() -> {
                    log.warn("Authentication failed: User identifier '{}' not found in database", usernameOrEmail);
                    return new UsernameNotFoundException("User not found: " + usernameOrEmail);
                });

        if (!user.isEmailVerified()) {
            log.warn("Authentication failed: User '{}' has not verified their email", user.getUsername());
            throw new DisabledException("Email not verified for user: " + user.getUsername());
        }

        log.debug("User '{}' found. Assigning authority/role: '{}'", user.getUsername(), user.getUserRole());

        return new org.springframework.security.core.userdetails.User(
                user.getUsername(),
                user.getPassword(),
                Collections.singletonList(new SimpleGrantedAuthority(user.getUserRole().name()))
        );
    }
}
