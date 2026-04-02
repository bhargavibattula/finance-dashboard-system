package com.zorvyn.finance.service;

import com.zorvyn.finance.entity.enums.Role;
import com.zorvyn.finance.entity.enums.UserStatus;
import com.zorvyn.finance.dto.request.CreateUserRequest;
import com.zorvyn.finance.dto.request.UpdateUserRequest;
import com.zorvyn.finance.dto.response.PagedResponse;
import com.zorvyn.finance.dto.response.UserResponse;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

/**
 * Contract for all user management operations.
 * Controllers depend only on this interface, never on the implementation.
 */
public interface UserService {

    /**
     * Create a new user.
     * Throws BusinessException if the email is already registered.
     * Default role is VIEWER if none is specified in the request.
     * Default status is ACTIVE.
     *
     * @param request   validated request body
     * @param ipAddress originating IP, forwarded to the audit log
     */
    UserResponse createUser(CreateUserRequest request, String ipAddress);

    /**
     * Fetch a user by their primary key.
     * Throws ResourceNotFoundException if no user exists with that id.
     */
    UserResponse getUserById(UUID id);

    /**
     * Paginated list of all users, with optional filters.
     * Passing null for role or status means "no filter on that field".
     */
    PagedResponse<UserResponse> getAllUsers(Role role, UserStatus status, Pageable pageable);

    /**
     * Update a user's name, password, or role.
     * Only non-null fields in the request are applied.
     * Throws ResourceNotFoundException if the user does not exist.
     *
     * @param ipAddress forwarded to the audit log
     */
    UserResponse updateUser(UUID id, UpdateUserRequest request, String ipAddress);

    /**
     * Set a user's status to INACTIVE.
     * The user record is retained in full — all their financial records
     * and audit entries remain intact and queryable.
     * Throws ResourceNotFoundException if the user does not exist.
     *
     * @param ipAddress forwarded to the audit log
     */
    UserResponse deactivateUser(UUID id, String ipAddress);
}