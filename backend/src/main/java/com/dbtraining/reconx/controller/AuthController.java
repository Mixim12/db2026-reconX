package com.dbtraining.reconx.controller;

import com.dbtraining.reconx.dto.LoginRequest;
import com.dbtraining.reconx.dto.LoginResponse;
import com.dbtraining.reconx.repository.AppUserRepository;
import com.dbtraining.reconx.repository.entity.AppUser;
import com.dbtraining.reconx.security.JwtTokenProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;
import java.util.UUID;

/**
 * TICKET-ADV072 — POST /api/auth/login
 *
 * Verifies BCrypt password, returns a JWT carrying the user's role.
 */
@RestController
@RequestMapping("/auth")
@Tag(name = "auth")
public class AuthController {

    private final AppUserRepository users;
    private final PasswordEncoder encoder;
    private final JwtTokenProvider jwt;

    /** Hash of a throwaway secret, verified against when the email is unknown. */
    private final String unknownUserHash;

    public AuthController(AppUserRepository users, PasswordEncoder encoder, JwtTokenProvider jwt) {
        this.users = users;
        this.encoder = encoder;
        this.jwt = jwt;
        this.unknownUserHash = encoder.encode(UUID.randomUUID().toString());
    }

    @PostMapping("/login")
    @Operation(summary = "Exchange email + password for a JWT")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest req) {
        Optional<AppUser> found = users.findByEmail(req.email());

        // Hash every attempt, including the ones already doomed: skipping the
        // BCrypt work for an unknown email or a disabled account would answer
        // "does this user exist?" in the response latency.
        boolean passwordMatches = encoder.matches(
                req.password(),
                found.map(AppUser::getPasswordHash).orElse(unknownUserHash));

        // One message, one status, whether the email is unknown, the account is
        // switched off, or the password is wrong — the caller learns nothing.
        AppUser user = found
                .filter(candidate -> Boolean.TRUE.equals(candidate.getEnabled()))
                .filter(candidate -> passwordMatches)
                .orElseThrow(AuthController::invalidCredentials);

        return ResponseEntity.ok(new LoginResponse(
                jwt.generate(user.getEmail(), user.getRole()),
                "Bearer",
                jwt.expirationSeconds(),
                user.getRole()));
    }

    private static BadCredentialsException invalidCredentials() {
        return new BadCredentialsException("Invalid credentials");
    }
}
