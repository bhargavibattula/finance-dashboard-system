package com.zorvyn.finance.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.zorvyn.finance.dto.response.ApiResponse;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final UserDetailsServiceImpl userDetailsService;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter,
                          UserDetailsServiceImpl userDetailsService) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.userDetailsService = userDetailsService;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

        http
                .csrf(AbstractHttpConfigurer::disable)

                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            ObjectMapper mapper = new ObjectMapper()
                                    .registerModule(new JavaTimeModule());
                            response.getWriter().write(
                                    mapper.writeValueAsString(
                                            ApiResponse.error("Authentication required. " +
                                                    "Please provide a valid Bearer token.")));
                        })
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            ObjectMapper mapper = new ObjectMapper()
                                    .registerModule(new JavaTimeModule());
                            response.getWriter().write(
                                    mapper.writeValueAsString(
                                            ApiResponse.error("You do not have permission " +
                                                    "to perform this action.")));
                        })
                )

                .authorizeHttpRequests(auth -> auth

                        // ✅ PUBLIC ENDPOINTS
                        .requestMatchers(
                                "/v1/auth/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/v3/api-docs/**"
                        ).permitAll()

                        // ADMIN
                        .requestMatchers(HttpMethod.GET,  "/v1/users").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET,  "/v1/users/{id}").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT,  "/v1/users/{id}/role").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT,  "/v1/users/{id}/status").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT,  "/v1/users/{id}/deactivate").hasRole("ADMIN")
                        .requestMatchers("/v1/audit-logs/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/v1/records/**").hasRole("ADMIN")

                        // ANALYST + ADMIN
                        .requestMatchers(HttpMethod.POST, "/v1/records/**")
                        .hasAnyRole("ANALYST", "ADMIN")
                        .requestMatchers(HttpMethod.PUT,  "/v1/records/**")
                        .hasAnyRole("ANALYST", "ADMIN")
                        .requestMatchers(HttpMethod.GET,  "/v1/records/export")
                        .hasAnyRole("ANALYST", "ADMIN")
                        .requestMatchers("/v1/dashboard/monthly-trend",
                                "/v1/dashboard/weekly-trend")
                        .hasAnyRole("ANALYST", "ADMIN")

                        // ALL AUTHENTICATED
                        .anyRequest().authenticated()
                )

                .userDetailsService(userDetailsService)

                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}