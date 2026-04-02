package com.zorvyn.finance.repository;
import org.springframework.stereotype.Repository;
import com.zorvyn.finance.entity.User;
import com.zorvyn.finance.entity.enums.Role;
import com.zorvyn.finance.entity.enums.UserStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    // ----------------------------------------------------------------
    // Lookup by email — used by Spring Security and AuthService
    // ----------------------------------------------------------------

    /**
     * Find a user by their email address.
     *
     * Spring derives the SQL:
     *   SELECT * FROM users WHERE email = ?
     *
     * Returns Optional so callers are forced to handle the "not found"
     * case explicitly — no accidental NullPointerExceptions.
     */
    Optional<User> findByEmail(String email);

    /**
     * Check whether an email is already registered.
     * Used during registration to return a clear error before attempting
     * a save that would fail with a unique constraint violation.
     *
     * Derived SQL:  SELECT COUNT(*) > 0 FROM users WHERE email = ?
     */
    boolean existsByEmail(String email);

    // ----------------------------------------------------------------
    // Admin user management — paginated list with optional filters
    // ----------------------------------------------------------------

    /**
     * List all users, with optional filtering by role and/or status.
     *
     * Why @Query instead of a derived name?
     * The derived equivalent would be something like:
     *   findByRoleAndStatus / findByRole / findByStatus / findAll
     * — four separate methods that the service would have to choose
     * between depending on which params are present. A single JPQL
     * query with null-safe conditions is cleaner.
     *
     * (:role IS NULL OR u.role = :role) means: if the caller passes
     * null for role, this condition is always true and is effectively
     * ignored — so the same method handles "all roles" and "one role".
     */
    @Query("""
            SELECT u FROM User u
            WHERE (:role   IS NULL OR u.role   = :role)
              AND (:status IS NULL OR u.status = :status)
            ORDER BY u.createdAt DESC
            """)
    Page<User> findAllWithFilters(
            @Param("role")   Role role,
            @Param("status") UserStatus status,
            Pageable pageable
    );

    /**
     * Search users by full name or email — for the admin user list search box.
     * LOWER() on both sides makes the match case-insensitive without
     * requiring a case-insensitive collation on the database column.
     */
    @Query("""
            SELECT u FROM User u
            WHERE LOWER(u.fullName) LIKE LOWER(CONCAT('%', :keyword, '%'))
               OR LOWER(u.email)    LIKE LOWER(CONCAT('%', :keyword, '%'))
            ORDER BY u.createdAt DESC
            """)
    Page<User> searchByKeyword(@Param("keyword") String keyword, Pageable pageable);

    // ----------------------------------------------------------------
    // Counts used by the admin dashboard
    // ----------------------------------------------------------------

    /** Total registered users regardless of status. */
    long countByRole(Role role);

    /** Count users in a specific status — e.g. how many are ACTIVE. */
    long countByStatus(UserStatus status);
}