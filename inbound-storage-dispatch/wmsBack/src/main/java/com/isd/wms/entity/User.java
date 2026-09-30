package com.isd.wms.entity;

import com.isd.wms.enums.Role;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.Hibernate;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Represents a system user.
 * <p>
 * Users have a username, email, encrypted password, and a role ({@link Role}).
 * The account can be active or inactive, and email verification is tracked via
 * a token with an expiry. This entity is used for authentication and authorisation
 * in the WMS.
 * </p>
 *
 * @see Role
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "user_seq")
    @SequenceGenerator(name = "user_seq", sequenceName = "users_sequence", allocationSize = 1)
    private Long id;

    @Column(length = 50, unique = true)
    private String username;

    @Column(nullable = false, length = 100, unique = true)
    private String email;

    @Column(nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(name = "user_role", nullable = false, length = 30)
    private Role userRole;

    @Column(name = "email_verified")
    private boolean emailVerified = false;

    @Column(name = "verification_token")
    private String verificationToken;

    @Column(name = "verification_token_expires_at")
    private LocalDateTime verificationTokenExpiresAt;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    public User(String username, String email, String password, Role userRole, Boolean emailVerified, String verificationToken, LocalDateTime verificationTokenExpiresAt) {
        this.username = username;
        this.email = email;
        this.password = password;
        this.userRole = userRole;
        this.emailVerified = emailVerified;
        this.verificationToken = verificationToken;
        this.verificationTokenExpiresAt = verificationTokenExpiresAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) return false;
        User user = (User) o;
        return id != null && Objects.equals(id, user.getId());
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
