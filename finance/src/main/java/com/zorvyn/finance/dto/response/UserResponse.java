package com.zorvyn.finance.dto.response;

import com.zorvyn.finance.entity.User;
import com.zorvyn.finance.entity.enums.Role;
import com.zorvyn.finance.entity.enums.UserStatus;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Safe user projection returned by all user endpoints.
 * The User entity contains passwordHash — it must never be serialised
 * directly to JSON. This DTO is what crosses the service boundary.
 */
public class UserResponse {

    private UUID          id;
    private String        fullName;
    private String        email;
    private Role          role;
    private UserStatus    status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private UserResponse() {}

    public static UserResponse from(User user) {
        UserResponse r = new UserResponse();
        r.id        = user.getId();
        r.fullName  = user.getFullName();
        r.email     = user.getEmail();
        r.role      = user.getRole();
        r.status    = user.getStatus();
        r.createdAt = user.getCreatedAt();
        r.updatedAt = user.getUpdatedAt();
        return r;
    }

    public UUID          getId()        { return id; }
    public String        getFullName()  { return fullName; }
    public String        getEmail()     { return email; }
    public Role          getRole()      { return role; }
    public UserStatus    getStatus()    { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}