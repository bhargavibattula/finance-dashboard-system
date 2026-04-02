package com.zorvyn.finance.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Intercepts every HTTP request and authenticates it from the JWT in the
 * Authorization header.
 *
 * Lifecycle per request:
 *
 *   1. Read the Authorization header.
 *      If it is absent or doesn't start with "Bearer ", skip this filter
 *      entirely — the request continues unauthenticated. Public endpoints
 *      (login, register) work this way.
 *
 *   2. Extract the raw token string (everything after "Bearer ").
 *
 *   3. Ask JwtUtil whether the token's signature and expiry are valid.
 *      If invalid, skip — the request continues unauthenticated and
 *      Spring Security will reject it at the authorization stage (401).
 *      We do NOT write a response here; that is SecurityConfig's job via
 *      the configured AuthenticationEntryPoint.
 *
 *   4. Extract the email from the token and load the User from the database.
 *      This re-load is intentional — it catches deactivated accounts even
 *      when they still hold a valid (not yet expired) token.
 *
 *   5. Set a UsernamePasswordAuthenticationToken in the SecurityContext.
 *      From this point on, every other component in the request pipeline can
 *      call SecurityContextHolder.getContext().getAuthentication() and get the
 *      full User entity back as the principal.
 *
 *   6. Continue the filter chain — the request reaches the controller.
 *
 * Why OncePerRequestFilter?
 * In some servlet configurations a single logical request can cause a filter
 * to run multiple times (e.g. on a forward or include). OncePerRequestFilter
 * uses a request attribute to guarantee exactly one execution per request.
 *
 * Why NOT catch exceptions here and write a JSON error response?
 * Clean separation of concerns. JwtAuthFilter only authenticates.
 * Error responses for unauthenticated/unauthorized requests are handled by
 * the AuthenticationEntryPoint and AccessDeniedHandler registered in
 * SecurityConfig. Mixing both in the filter creates duplicate response logic.
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtUtil                jwtUtil;
    private final UserDetailsServiceImpl userDetailsService;

    public JwtAuthFilter(JwtUtil jwtUtil, UserDetailsServiceImpl userDetailsService) {
        this.jwtUtil            = jwtUtil;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest  request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain         filterChain)
            throws ServletException, IOException {

        // Step 1 — read the Authorization header
        final String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);

        // If the header is missing or doesn't start with "Bearer ", pass through
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        // Step 2 — extract the raw token (strip "Bearer " prefix)
        final String token = authHeader.substring(BEARER_PREFIX.length());

        // Step 3 — validate signature and expiry
        if (!jwtUtil.isTokenValid(token)) {
            // Token is bad — pass through unauthenticated.
            // Spring Security will handle the 401 via AuthenticationEntryPoint.
            filterChain.doFilter(request, response);
            return;
        }

        // Step 4 — only authenticate if no auth is already in the context.
        // This guard prevents re-processing on forwards within the same request.
        if (SecurityContextHolder.getContext().getAuthentication() == null) {

            String      email       = jwtUtil.extractEmail(token);
            UserDetails userDetails = userDetailsService.loadUserByUsername(email);

            // isEnabled() checks status == ACTIVE (defined in User entity).
            // If the account was deactivated after this token was issued,
            // this check catches it and we leave the SecurityContext empty.
            if (userDetails.isEnabled()) {

                // Step 5 — set the authentication in the SecurityContext.
                // credentials = null (we have no password at this stage, only a token).
                // authorities come from User.getAuthorities() — "ROLE_ADMIN" etc.
                UsernamePasswordAuthenticationToken authToken =
                        new UsernamePasswordAuthenticationToken(
                                userDetails,
                                null,
                                userDetails.getAuthorities()
                        );

                // Attach request details (IP address, session ID) to the auth token.
                // Spring Security uses this for logging and audit purposes.
                authToken.setDetails(
                        new WebAuthenticationDetailsSource().buildDetails(request));

                SecurityContextHolder.getContext().setAuthentication(authToken);
            }
        }

        // Step 6 — continue the filter chain
        filterChain.doFilter(request, response);
    }
}