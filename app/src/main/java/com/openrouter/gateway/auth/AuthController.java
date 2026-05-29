package com.openrouter.gateway.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

/**
 * Handles user registration and login.
 *
 * POST /api/auth/register  — creates a new user, returns JWT
 * POST /api/auth/login     — authenticates existing user, returns JWT
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public AuthController(UserRepository userRepository,
                          PasswordEncoder passwordEncoder,
                          JwtUtil jwtUtil) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    // ── Register ──────────────────────────────────────────────────────────

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody AuthRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new AuthResponse(null, "Email already registered"));
        }

        String hash = passwordEncoder.encode(request.password());
        User user = new User(request.email(), hash);
        userRepository.save(user);

        String token = jwtUtil.generateToken(user.getEmail());
        log.info("New user registered: {}", user.getEmail());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new AuthResponse(token, "Registration successful"));
    }

    // ── Login ─────────────────────────────────────────────────────────────

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody AuthRequest request) {
        return userRepository.findByEmail(request.email())
                .filter(user -> passwordEncoder.matches(request.password(), user.getPasswordHash()))
                .map(user -> {
                    String token = jwtUtil.generateToken(user.getEmail());
                    log.info("User logged in: {}", user.getEmail());
                    return ResponseEntity.ok(new AuthResponse(token, "Login successful"));
                })
                .orElse(ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(new AuthResponse(null, "Invalid email or password")));
    }

    // ── Change Password ───────────────────────────────────────────────────

    @PostMapping("/change-password")
    public ResponseEntity<AuthResponse> changePassword(
            @AuthenticationPrincipal String userEmail,
            @Valid @RequestBody ChangePasswordRequest request) {

        return userRepository.findByEmail(userEmail)
                .map(user -> {
                    if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
                        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                                .body(new AuthResponse(null, "Current password is incorrect"));
                    }
                    user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
                    userRepository.save(user);
                    log.info("Password changed for user: {}", userEmail);
                    return ResponseEntity.ok(new AuthResponse(null, "Password changed successfully"));
                })
                .orElse(ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(new AuthResponse(null, "User not found")));
    }

    // ── DTOs (Java 25 records) ────────────────────────────────────────────

    /**
     * Request DTO — immutable record, validated on input.
     */
    public record AuthRequest(
            @NotBlank @Email String email,
            @NotBlank @Size(min = 8, max = 100) String password
    ) {}

    /**
     * Response DTO — token is null on failure.
     */
    public record AuthResponse(
            String token,
            String message
    ) {}

    public record ChangePasswordRequest(
            @NotBlank String currentPassword,
            @NotBlank @Size(min = 8, max = 100) String newPassword
    ) {}
}
