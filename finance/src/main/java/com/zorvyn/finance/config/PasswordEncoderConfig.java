package com.zorvyn.finance.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Declares the two low-level security beans that other components
 * depend on at injection time.
 *
 * These live in their own @Configuration class instead of SecurityConfig
 * to avoid a circular dependency that Spring Boot 3 will refuse to start with:
 *
 *   SecurityConfig  →  UserDetailsServiceImpl  →  UserRepository
 *   SecurityConfig  →  AuthenticationManager   →  UserDetailsService
 *                                               →  PasswordEncoder
 *
 * Putting PasswordEncoder and AuthenticationManager here breaks the cycle.
 *
 * BCryptPasswordEncoder(12):
 *   Strength 10 is the default (fast for tests, fine for dev).
 *   Strength 12 doubles the hash time (~300ms on modern hardware) and
 *   is the commonly recommended production value — hard enough to brute-force,
 *   fast enough to not hurt UX.
 */
@Configuration
public class PasswordEncoderConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    /**
     * Exposes the AuthenticationManager so services can call
     * authenticationManager.authenticate() directly — the standard
     * approach for handling login in a stateless JWT setup.
     *
     * AuthenticationConfiguration is auto-configured by Spring Boot;
     * we just expose its manager as a named bean.
     */
    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration authConfig) throws Exception {
        return authConfig.getAuthenticationManager();
    }
}