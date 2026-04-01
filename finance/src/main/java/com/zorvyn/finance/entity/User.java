package com.zorvyn.finance.entity;
import com.zorvyn.finance.entity.enums.Role;
import com.zorvyn.finance.entity.enums.UserStatus;
import jakarta.persistence.*;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * Represents a system user.
 *
 * Implements Spring Security's UserDetails so the entity itself can be
 * handed directly to the authentication framework — no separate adapter
 * class needed, and no risk of the two getting out of sync.
 *
 * Soft-delete design: isDeleted is intentionally NOT on this entity.
 * User records are deactivated (status = INACTIVE) rather than deleted,
 * preserving referential integrity with financial_records and audit_logs.
 */
@Entity
@Table(
        name = "users",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_users_email", columnNames = "email")
        }
)
public class User extends BaseEntity implements UserDetails {

    @Column(nullable = false, length = 100)
    private String fullName;

    @Column(nullable = false, unique = true, length = 150)
    private String email;

    /**
     * Stores the BCrypt hash of the user's password.
     * The plain-text password never touches this field.
     */
    @Column(nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserStatus status;

    // ----------------------------------------------------------------
    // Constructors
    // ----------------------------------------------------------------

    protected User() {
        // JPA requires a no-arg constructor; protected discourages
        // accidental direct instantiation from outside the package.
    }

    /**
     * Use this constructor when registering a new user.
     * Defaults: role = VIEWER, status = ACTIVE.
     * Admins can change role and status separately via dedicated endpoints.
     */
    public User(String fullName, String email, String passwordHash) {
        this.fullName     = fullName;
        this.email        = email;
        this.passwordHash = passwordHash;
        this.role         = Role.VIEWER;
        this.status       = UserStatus.ACTIVE;
    }

    // ----------------------------------------------------------------
    // Spring Security — UserDetails contract
    // ----------------------------------------------------------------

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        // Prefix with ROLE_ so Spring Security's hasRole() works out of the box.
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    /**
     * Spring Security uses getUsername() as the unique identifier.
     * We use email as the username because it is guaranteed unique.
     */
    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return status == UserStatus.ACTIVE;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return status == UserStatus.ACTIVE;
    }

    // ----------------------------------------------------------------
    // Getters and setters
    // ----------------------------------------------------------------

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getEmail() {
        return email;
    }

    // Email is set at registration and should not change later.
    // If email-change support is added, it requires a verification flow.
    public void setEmail(String email) {
        this.email = email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public UserStatus getStatus() {
        return status;
    }

    public void setStatus(UserStatus status) {
        this.status = status;
    }
}
