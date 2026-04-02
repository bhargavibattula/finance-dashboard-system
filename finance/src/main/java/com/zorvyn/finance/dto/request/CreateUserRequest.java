package com.zorvyn.finance.dto.request;

import com.zorvyn.finance.entity.enums.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for creating a new user (admin operation).
 * The password arrives as plain text and is encoded in the service.
 */
public class CreateUserRequest {

    @NotBlank(message = "Full name is required")
    @Size(min = 2, max = 100, message = "Full name must be 2–100 characters")
    private String fullName;

    @NotBlank(message = "Email is required")
    @Email(message = "Must be a valid email address")
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 8, message = "Password must be at least 8 characters")
    private String password;

    // Role is optional — defaults to VIEWER in the service if not supplied
    private Role role;

    public CreateUserRequest() {}

    public String getFullName() { return fullName; }
    public void   setFullName(String v) { this.fullName = v; }

    public String getEmail()    { return email; }
    public void   setEmail(String v) { this.email = v; }

    public String getPassword() { return password; }
    public void   setPassword(String v) { this.password = v; }

    public Role   getRole()     { return role; }
    public void   setRole(Role v) { this.role = v; }
}