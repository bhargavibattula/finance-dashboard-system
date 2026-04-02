package com.zorvyn.finance.dto.request;

import com.zorvyn.finance.entity.enums.Role;
import jakarta.validation.constraints.Size;

/**
 * Request body for updating an existing user.
 * All fields are optional — only non-null fields are applied.
 */
public class UpdateUserRequest {

    @Size(min = 2, max = 100, message = "Full name must be 2–100 characters")
    private String fullName;

    // Password is optional — only updated when explicitly provided
    @Size(min = 8, message = "Password must be at least 8 characters")
    private String password;

    private Role role;

    public UpdateUserRequest() {}

    public String getFullName() { return fullName; }
    public void   setFullName(String v) { this.fullName = v; }

    public String getPassword() { return password; }
    public void   setPassword(String v) { this.password = v; }

    public Role   getRole()     { return role; }
    public void   setRole(Role v) { this.role = v; }
}