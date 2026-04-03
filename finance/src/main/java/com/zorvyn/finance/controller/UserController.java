package com.zorvyn.finance.controller;
import com.zorvyn.finance.dto.request.CreateUserRequest;
import com.zorvyn.finance.dto.request.UpdateUserRequest;
import com.zorvyn.finance.dto.response.ApiResponse;
import com.zorvyn.finance.dto.response.PagedResponse;
import com.zorvyn.finance.dto.response.UserResponse;
import com.zorvyn.finance.entity.User;
import com.zorvyn.finance.entity.enums.Role;
import com.zorvyn.finance.entity.enums.UserStatus;
import com.zorvyn.finance.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * User management endpoints.
 *
 * URL structure: /api/v1/users/**
 *
 * Role rules (enforced both in SecurityConfig URL patterns and
 * @PreAuthorize for fine-grained method-level control):
 *   POST   /v1/users           → ADMIN only (create user)
 *   GET    /v1/users           → ADMIN only (list all users)
 *   GET    /v1/users/{id}      → ADMIN only (get any user)
 *   GET    /v1/users/me        → ALL authenticated (get own profile)
 *   PUT    /v1/users/{id}      → ADMIN only (update any user)
 *   PUT    /v1/users/me        → ALL authenticated (update own profile)
 *   PUT    /v1/users/{id}/deactivate → ADMIN only
 *
 * @AuthenticationPrincipal User currentUser — Spring injects the User
 * entity that JwtAuthFilter placed in the SecurityContext. This is the
 * full User entity (not just a username string) because
 * UserDetailsServiceImpl returns it directly.
 */
@RestController
@RequestMapping("/v1/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    // ----------------------------------------------------------------
    // POST /api/v1/users  — ADMIN only
    // ----------------------------------------------------------------

    /**
     * Admin creates a user with any role.
     * Returns 201 Created with the new UserResponse.
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<UserResponse>> createUser(
            @Valid @RequestBody CreateUserRequest request,
            HttpServletRequest httpRequest) {

        UserResponse created = userService.createUser(request, extractIp(httpRequest));
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.ok(created, "User created successfully"));
    }

    // ----------------------------------------------------------------
    // GET /api/v1/users  — ADMIN only, paginated + optional filters
    // ----------------------------------------------------------------

    /**
     * Returns a paginated list of all users.
     *
     * Query params:
     *   role     — filter by Role enum value (VIEWER / ANALYST / ADMIN); optional
     *   status   — filter by UserStatus (ACTIVE / INACTIVE); optional
     *   page     — zero-based page index (default 0)
     *   size     — page size (default 20, max 100)
     *   sortBy   — field to sort on (default "createdAt")
     *   sortDir  — "asc" or "desc" (default "desc")
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<PagedResponse<UserResponse>>> getAllUsers(
            @RequestParam(required = false) Role       role,
            @RequestParam(required = false) UserStatus status,
            @RequestParam(defaultValue = "0")   int    page,
            @RequestParam(defaultValue = "20")  int    size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {

        size = Math.min(size, 100);  // hard cap — prevent oversized requests
        Sort sort = sortDir.equalsIgnoreCase("asc")
                ? Sort.by(sortBy).ascending()
                : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);

        PagedResponse<UserResponse> result = userService.getAllUsers(role, status, pageable);
        return ResponseEntity.ok(ApiResponse.ok(result, "Users fetched successfully"));
    }

    // ----------------------------------------------------------------
    // GET /api/v1/users/me  — ALL authenticated users
    // ----------------------------------------------------------------

    /**
     * Returns the profile of the currently authenticated user.
     * Uses the User entity injected by Spring Security — no DB call needed.
     */
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> getMyProfile(
            @AuthenticationPrincipal User currentUser) {

        // Re-fetch to get fresh data — the principal was loaded at login time
        UserResponse profile = userService.getUserById(currentUser.getId());
        return ResponseEntity.ok(ApiResponse.ok(profile, "Profile fetched successfully"));
    }

    // ----------------------------------------------------------------
    // GET /api/v1/users/{id}  — ADMIN only
    // ----------------------------------------------------------------

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<UserResponse>> getUserById(@PathVariable UUID id) {

        UserResponse user = userService.getUserById(id);
        return ResponseEntity.ok(ApiResponse.ok(user, "User fetched successfully"));
    }

    // ----------------------------------------------------------------
    // PUT /api/v1/users/me  — ALL authenticated users (own profile)
    // ----------------------------------------------------------------

    /**
     * The authenticated user updates their own profile (name or password).
     * Role changes are not permitted here — use PUT /v1/users/{id} as admin.
     */
    @PutMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> updateMyProfile(
            @Valid @RequestBody UpdateUserRequest request,
            @AuthenticationPrincipal User currentUser,
            HttpServletRequest httpRequest) {

        // Strip role from the request — users cannot promote themselves
        request.setRole(null);

        UserResponse updated = userService.updateUser(
                currentUser.getId(), request, extractIp(httpRequest));
        return ResponseEntity.ok(ApiResponse.ok(updated, "Profile updated successfully"));
    }

    // ----------------------------------------------------------------
    // PUT /api/v1/users/{id}  — ADMIN only (update any user)
    // ----------------------------------------------------------------

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<UserResponse>> updateUser(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateUserRequest request,
            HttpServletRequest httpRequest) {

        UserResponse updated = userService.updateUser(id, request, extractIp(httpRequest));
        return ResponseEntity.ok(ApiResponse.ok(updated, "User updated successfully"));
    }

    // ----------------------------------------------------------------
    // PUT /api/v1/users/{id}/deactivate  — ADMIN only
    // ----------------------------------------------------------------

    /**
     * Deactivates a user account (status → INACTIVE).
     * The user record and all their data are retained.
     * A deactivated user cannot log in even with a valid token
     * (isEnabled() returns false → JwtAuthFilter skips SecurityContext population).
     */
    @PutMapping("/{id}/deactivate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<UserResponse>> deactivateUser(
            @PathVariable UUID id,
            HttpServletRequest httpRequest) {

        UserResponse deactivated = userService.deactivateUser(id, extractIp(httpRequest));
        return ResponseEntity.ok(ApiResponse.ok(deactivated, "User deactivated successfully"));
    }

    // ----------------------------------------------------------------
    // Helper
    // ----------------------------------------------------------------

    private String extractIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}