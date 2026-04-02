package com.zorvyn.finance.security;

import com.zorvyn.finance.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bridges Spring Security's authentication pipeline with our User entity.
 *
 * Spring Security calls loadUserByUsername(email) during:
 *   1. AuthenticationManager.authenticate() — triggered by our login endpoint.
 *   2. JwtAuthFilter.doFilterInternal() — on every protected request, after
 *      the token is validated, to load the fresh User from the database.
 *
 * Why re-load from DB on every request in JwtAuthFilter?
 * The JWT contains the user's email but not their current status or role.
 * If an admin deactivates a user mid-session, the user's token is still
 * cryptographically valid — we only catch the deactivation by checking the
 * DB. Loading fresh on every request adds one indexed SELECT (email column
 * has a unique index) and closes this gap.
 *
 * User already implements UserDetails (see User.java) so we return it
 * directly — no wrapper or adapter needed.
 *
 * @Transactional(readOnly = true) — this is a DB read. Hibernate skips
 * dirty-checking and can use a read-only connection if configured.
 */
@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;

    public UserDetailsServiceImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email)
            throws UsernameNotFoundException {

        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "No account found with email: " + email));
    }
}