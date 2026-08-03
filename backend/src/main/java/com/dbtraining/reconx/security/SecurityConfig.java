package com.dbtraining.reconx.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * ============================================================================
 * SecurityConfig — TICKET-ADV073 + TICKET-ADV074
 * ============================================================================
 * WHAT:    Spring Security filter chain. Production target: stateless JWT
 *          auth + method-level RBAC across ADMIN / TRADER / VIEWER /
 *          RECON_ANALYST roles.
 * HOW:     One SecurityFilterChain @Bean + PasswordEncoder @Bean +
 *          @EnableMethodSecurity. The JwtAuthenticationFilter is registered
 *          before UsernamePasswordAuthenticationFilter.
 * WHY:     Day 6 needs role-based protection on every endpoint, and the
 *          frontend uses bearer tokens issued at /auth/login.
 * OBSERVE: After Day-6 work is wired, GET /api/v1/trades without a token -> 401.
 * ============================================================================
 */
@Configuration
@org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtAuthenticationFilter jwtFilter) throws Exception {
        http
          .csrf(org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer::disable)
          .sessionManagement(s -> s.sessionCreationPolicy(org.springframework.security.config.http.SessionCreationPolicy.STATELESS))
          .authorizeHttpRequests(auth -> auth
            .requestMatchers("/auth/login","/actuator/health/**","/actuator/info",
                             "/actuator/prometheus","/swagger-ui.html","/swagger-ui/**",
                             "/v3/api-docs/**","/h2/**").permitAll()
            .requestMatchers(org.springframework.http.HttpMethod.GET,    "/v1/trades/**").hasAnyRole("VIEWER","TRADER","RECON_ANALYST","ADMIN")
            .requestMatchers(org.springframework.http.HttpMethod.POST,   "/v1/trades").hasAnyRole("TRADER","ADMIN")
            .requestMatchers(org.springframework.http.HttpMethod.PUT,    "/v1/trades/**").hasAnyRole("TRADER","ADMIN")
            .requestMatchers(org.springframework.http.HttpMethod.PATCH,  "/v1/trades/**").hasAnyRole("TRADER","ADMIN")
            .requestMatchers(org.springframework.http.HttpMethod.DELETE, "/v1/trades/**").hasRole("ADMIN")
            .requestMatchers("/v1/recon/**").hasAnyRole("RECON_ANALYST","ADMIN")
            .requestMatchers("/v1/audit/**").hasAnyRole("RECON_ANALYST","ADMIN")
            .anyRequest().authenticated())
          .headers(h -> h.frameOptions(org.springframework.security.config.annotation.web.configurers.HeadersConfigurer.FrameOptionsConfig::disable))
          .addFilterBefore(jwtFilter, org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
