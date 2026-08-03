package com.dbtraining.reconx.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * ============================================================================
 * SecurityConfig — TICKET-ADV073 + TICKET-ADV074
 * ============================================================================
 * WHAT:    Stateless JWT filter chain plus URL-level RBAC across the
 *          ADMIN / TRADER / VIEWER / RECON_ANALYST roles.
 * HOW:     One SecurityFilterChain @Bean + PasswordEncoder @Bean +
 *          @EnableMethodSecurity. JwtAuthenticationFilter is registered before
 *          UsernamePasswordAuthenticationFilter, so a bearer token becomes an
 *          Authentication before the authorization rules are evaluated.
 * WHY:     Day 6 needs role-based protection on every endpoint, and the
 *          frontend uses bearer tokens issued at /auth/login.
 * OBSERVE: GET /api/v1/trades without a token -> 401; a VIEWER POST -> 403;
 *          a TRADER POST -> 201.
 *
 * NOTE: the `/api` context-path is set in application.yml, so the paths below
 *       are relative to it (`/v1/trades` resolves to `/api/v1/trades`).
 * ============================================================================
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           JwtAuthenticationFilter jwtFilter) throws Exception {
        http
                // The bearer token carries the credential, so there is no session
                // and no CSRF token — leaving CSRF on would 403 every POST.
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/auth/login",
                                "/actuator/health/**",
                                "/actuator/info",
                                "/actuator/prometheus",
                                "/swagger-ui.html",
                                "/swagger-ui/**",
                                "/v1/api-docs/**",
                                "/v3/api-docs/**",
                                "/h2/**"
                        ).permitAll()
                        .requestMatchers(HttpMethod.GET, "/v1/trades/**")
                                .hasAnyRole("VIEWER", "TRADER", "RECON_ANALYST", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/v1/trades")
                                .hasAnyRole("TRADER", "ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/v1/trades/**")
                                .hasAnyRole("TRADER", "ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/v1/trades/**")
                                .hasAnyRole("TRADER", "ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/v1/trades/**")
                                .hasRole("ADMIN")
                        .requestMatchers("/v1/recon/**").hasAnyRole("RECON_ANALYST", "ADMIN")
                        .requestMatchers("/v1/audit/**").hasAnyRole("RECON_ANALYST", "ADMIN")
                        .anyRequest().authenticated()
                )
                // The default entry point is Http403ForbiddenEntryPoint, which would
                // answer a missing token with 403 — indistinguishable from a genuine
                // authorization failure. A missing credential is a 401.
                .exceptionHandling(e -> e.authenticationEntryPoint(
                        new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .headers(h -> h.frameOptions(f -> f.disable()))   // for the /h2 dev console
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
