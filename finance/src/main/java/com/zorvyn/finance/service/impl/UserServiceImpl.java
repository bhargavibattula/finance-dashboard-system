package com.zorvyn.finance.service.impl;

import com.zorvyn.finance.entity.User;
import com.zorvyn.finance.entity.enums.AuditAction;
import com.zorvyn.finance.entity.enums.Role;
import com.zorvyn.finance.entity.enums.UserStatus;
import com.zorvyn.finance.repository.UserRepository;
import com.zorvyn.finance.dto.request.CreateUserRequest;
import com.zorvyn.finance.dto.request.UpdateUserRequest;
import com.zorvyn.finance.dto.response.PagedResponse;
import com.zorvyn.finance.dto.response.UserResponse;
import com.zorvyn.finance.exception.BusinessException;
import com.zorvyn.finance.exception.ResourceNotFoundException;
import com.zorvyn.finance.service.AuditLogService;
import com.zorvyn.finance.service.UserService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * All user management operations.
 *
 * Design decisions worth noting:
 *
 * 1. PasswordEncoder is injected here, not in a controller. Plain-text
 *    passwords never travel past the service boundary.
 *
 * 2. Partial update pattern in updateUser() — we check each field for
 *    null independently and only call the setter when the caller supplied
 *    a value. This means the client can update just the name without
 *    resending the password.
 *
 * 3. deactivateUser() sets status = INACTIVE. It does not delete the User
 *    row. This preserves the foreign key references in financial_records
 *    (created_by, last_modified_by) and in audit_logs (actor_id).
 *    Deleting a user would orphan all their historical records.
 *
 * 4. Audit snapshots are simple key=value strings rather than full JSON.
 *    This is intentional — it keeps AuditLogService dependency-free from
 *    Jackson and keeps the audit entries human-readable without a parser.
 *    Full JSON snapshots are used only in FinancialRecordServiceImpl where
 *    the richer before/after diff is more valuable.
 *
 * 5. @Transactional(readOnly = true) on read methods — Hibernate skips
 *    dirty-checking on every loaded entity, reducing memory and CPU overhead.
 *    On PostgreSQL with a read-replica setup this also routes to the replica.
 */
@Service
public class UserServiceImpl implements UserService {

    private final UserRepository  userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;

    public UserServiceImpl(UserRepository  userRepository,
                           PasswordEncoder passwordEncoder,
                           AuditLogService auditLogService) {
        this.userRepository  = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditLogService = auditLogService;
    }

    // ----------------------------------------------------------------
    // Create
    // ----------------------------------------------------------------

    @Override
    @Transactional
    public UserResponse createUser(CreateUserRequest request, String ipAddress) {

        // Guard: reject before hitting the DB unique constraint
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BusinessException(
                    "An account with email '" + request.getEmail() + "' already exists");
        }

        // Build entity — constructor sets role=VIEWER, status=ACTIVE by default
        User user = new User(
                request.getFullName(),
                request.getEmail(),
                passwordEncoder.encode(request.getPassword())
        );

        // Override role if admin explicitly specified one
        if (request.getRole() != null) {
            user.setRole(request.getRole());
        }

        User saved = userRepository.save(user);

        auditLogService.log(
                saved.getId(),
                saved.getFullName(),
                AuditAction.USER_REGISTERED,
                "User",
                saved.getId(),
                null,
                snapshotUser(saved),
                ipAddress
        );

        return UserResponse.from(saved);
    }

    // ----------------------------------------------------------------
    // Read — single user
    // ----------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public UserResponse getUserById(UUID id) {
        return UserResponse.from(findOrThrow(id));
    }

    // ----------------------------------------------------------------
    // Read — paginated list with optional filters
    // ----------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<UserResponse> getAllUsers(Role       role,
                                                   UserStatus status,
                                                   Pageable   pageable) {
        // findAllWithFilters handles null gracefully — null = no filter
        Page<User> page = userRepository.findAllWithFilters(role, status, pageable);

        List<UserResponse> content = page.getContent()
                .stream()
                .map(UserResponse::from)
                .toList();

        return PagedResponse.of(page, content);
    }

    // ----------------------------------------------------------------
    // Update — partial: only apply non-null fields
    // ----------------------------------------------------------------

    @Override
    @Transactional
    public UserResponse updateUser(UUID id, UpdateUserRequest request, String ipAddress) {
        User user        = findOrThrow(id);
        String oldSnapshot = snapshotUser(user);

        if (request.getFullName() != null) {
            user.setFullName(request.getFullName());
        }
        if (request.getPassword() != null) {
            user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        }
        if (request.getRole() != null) {
            user.setRole(request.getRole());
        }

        User saved = userRepository.save(user);

        auditLogService.log(
                saved.getId(),
                saved.getFullName(),
                AuditAction.ROLE_CHANGED,   // covers any profile/role change
                "User",
                saved.getId(),
                oldSnapshot,
                snapshotUser(saved),
                ipAddress
        );

        return UserResponse.from(saved);
    }

    // ----------------------------------------------------------------
    // Deactivate — status flip, not a delete
    // ----------------------------------------------------------------

    @Override
    @Transactional
    public UserResponse deactivateUser(UUID id, String ipAddress) {
        User user        = findOrThrow(id);
        String oldSnapshot = snapshotUser(user);

        if (user.getStatus() == UserStatus.INACTIVE) {
            throw new BusinessException("User is already inactive");
        }

        user.setStatus(UserStatus.INACTIVE);
        User saved = userRepository.save(user);

        auditLogService.log(
                saved.getId(),
                saved.getFullName(),
                AuditAction.STATUS_CHANGED,
                "User",
                saved.getId(),
                oldSnapshot,
                snapshotUser(saved),
                ipAddress
        );

        return UserResponse.from(saved);
    }

    // ----------------------------------------------------------------
    // Private helpers
    // ----------------------------------------------------------------

    private User findOrThrow(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", id));
    }

    /**
     * Produces a compact, human-readable snapshot for the audit log.
     * Keeps AuditLogServiceImpl free from Jackson while still giving
     * the admin a clear before/after picture.
     */
    private String snapshotUser(User u) {
        return "fullName=" + u.getFullName()
                + ", email="  + u.getEmail()
                + ", role="   + u.getRole()
                + ", status=" + u.getStatus();
    }
}