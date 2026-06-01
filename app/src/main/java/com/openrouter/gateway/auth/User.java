package com.openrouter.gateway.auth;

import com.openrouter.gateway.config.AesEncryptedStringConverter;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Represents a registered API user.
 * Passwords are stored as BCrypt hashes — never plaintext.
 */
@Entity
@Table(name = "users", uniqueConstraints = {
        @UniqueConstraint(name = "uk_users_email", columnNames = "email")
})
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Email
    @Column(nullable = false, length = 150)
    private String email;

    @NotBlank
    @Size(min = 60, max = 60) // BCrypt hash is always 60 chars
    @Column(nullable = false, length = 60)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role = Role.USER;

    @Column(nullable = false)
    private boolean active = true;

    // ── BYOK: per-user OpenRouter API key ─────────────────────────────────
    // Stored encrypted via AES-GCM. Never exposed via any API response.

    @Convert(converter = AesEncryptedStringConverter.class)
    @Column(name = "openrouter_key_encrypted", length = 512)
    private String openrouterKeyEncrypted;

    @Column(name = "openrouter_key_validated", nullable = false)
    private boolean openrouterKeyValidated = false;

    @Column(name = "openrouter_key_set_at")
    private LocalDateTime openrouterKeySetAt;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    // ── Constructors ──────────────────────────────────────────────────────

    public User() {}

    public User(String email, String passwordHash) {
        this.email = email;
        this.passwordHash = passwordHash;
    }

    // ── Getters / Setters ─────────────────────────────────────────────────

    public Long getId() { return id; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    public String getOpenrouterKeyEncrypted() { return openrouterKeyEncrypted; }
    public void setOpenrouterKeyEncrypted(String openrouterKeyEncrypted) {
        this.openrouterKeyEncrypted = openrouterKeyEncrypted;
    }

    public boolean isOpenrouterKeyValidated() { return openrouterKeyValidated; }
    public void setOpenrouterKeyValidated(boolean openrouterKeyValidated) {
        this.openrouterKeyValidated = openrouterKeyValidated;
    }

    public LocalDateTime getOpenrouterKeySetAt() { return openrouterKeySetAt; }
    public void setOpenrouterKeySetAt(LocalDateTime openrouterKeySetAt) {
        this.openrouterKeySetAt = openrouterKeySetAt;
    }

    public enum Role {
        USER, ADMIN
    }
}
