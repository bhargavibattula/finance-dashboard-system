package com.zorvyn.finance.controller;

import com.zorvyn.finance.dto.request.CreateUserRequest;
import com.zorvyn.finance.dto.request.LoginRequest;
import com.zorvyn.finance.dto.request.RefreshTokenRequest;
import com.zorvyn.finance.dto.response.ApiResponse;
import com.zorvyn.finance.dto.response.UserResponse;
import com.zorvyn.finance.entity.User;
import com.zorvyn.finance.security.JwtUtil;
import com.zorvyn.finance.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Public authentication endpoints — no JWT required.
 *
 * Context-path /api is set in application.yml, so:
 *   @RequestMapping("/v1/auth") produces /api/v1/auth/**
 * This matches the permitAll() rules in SecurityConfig exactly.
 */
@RestController
@RequestMapping("/v1/auth")
public class AuthController {

    private final UserService           userService;
    private final AuthenticationManager authenticationManager;
    private final JwtUtil               jwtUtil;

    public AuthController(UserService           userService,
                          AuthenticationManager authenticationManager,
                          JwtUtil               jwtUtil) {
        this.userService           = userService;
        this.authenticationManager = authenticationManager;
        this.jwtUtil               = jwtUtil;
    }

    // POST /api/v1/auth/register
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<Map<String, Object>>> register(
            @Valid @RequestBody CreateUserRequest request,
            HttpServletRequest httpRequest) {

        UserResponse savedUser = userService.createUser(request, extractIp(httpRequest));

        // Authenticate to get the User entity as principal for token generation
        Authentication auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getEmail(), request.getPassword()));

        User   principal    = (User) auth.getPrincipal();
        String accessToken  = jwtUtil.generateAccessToken(principal);
        String refreshToken = jwtUtil.generateRefreshToken(principal);

        Map<String, Object> body = buildTokenBody(accessToken, refreshToken);
        body.put("user", savedUser);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.ok(body, "Account created successfully"));
    }

    // POST /api/v1/auth/login
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<Map<String, Object>>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest) {

        // authenticate() throws BadCredentialsException (wrong password → 401)
        // or DisabledException (INACTIVE account → 401) — both handled in GlobalExceptionHandler
        Authentication auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getEmail(), request.getPassword()));

        User   principal    = (User) auth.getPrincipal();
        String accessToken  = jwtUtil.generateAccessToken(principal);
        String refreshToken = jwtUtil.generateRefreshToken(principal);

        Map<String, Object> body = buildTokenBody(accessToken, refreshToken);
        body.put("email",    principal.getEmail());
        body.put("fullName", principal.getFullName());
        body.put("role",     principal.getRole());

        return ResponseEntity.ok(ApiResponse.ok(body, "Login successful"));
    }

    // POST /api/v1/auth/refresh
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<Map<String, Object>>> refresh(
            @Valid @RequestBody RefreshTokenRequest request) {

        String token = request.getRefreshToken();

        if (!jwtUtil.isRefreshToken(token) || !jwtUtil.isTokenValid(token)) {
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error(
                            "Refresh token is invalid or has expired. Please log in again."));
        }

        // Extract email + role from the refresh token's claims — no DB call needed
        String newAccessToken = jwtUtil.generateAccessTokenFromRefreshToken(token);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("accessToken", newAccessToken);
        body.put("tokenType",   "Bearer");
        body.put("expiresIn",   jwtUtil.getAccessTokenExpirySeconds());

        return ResponseEntity.ok(ApiResponse.ok(body, "Token refreshed successfully"));
    }

    // ----------------------------------------------------------------
    // Helpers
    // ----------------------------------------------------------------

    private Map<String, Object> buildTokenBody(String accessToken, String refreshToken) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("accessToken",  accessToken);
        body.put("refreshToken", refreshToken);
        body.put("tokenType",    "Bearer");
        body.put("expiresIn",    jwtUtil.getAccessTokenExpirySeconds());
        return body;
    }

    private String extractIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}