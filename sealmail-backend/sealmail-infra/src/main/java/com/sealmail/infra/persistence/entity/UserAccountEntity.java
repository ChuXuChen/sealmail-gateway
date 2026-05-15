package com.sealmail.infra.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Data;

import java.time.Instant;

@Data
@Entity
@Table(name = "user_account", indexes = {
        @Index(name = "idx_user_username", columnList = "username", unique = true),
        @Index(name = "idx_user_email", columnList = "email", unique = true)
})
public class UserAccountEntity {

    @Id
    @Column(name = "id", length = 128)
    private String id;

    @Column(name = "username", nullable = false, length = 64)
    private String username;

    @Column(name = "email", nullable = false, length = 254)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 256)
    private String passwordHash;

    @Column(name = "roles", nullable = false, length = 1024)
    private String roles;

    @Column(name = "managed_domains", length = 2048)
    private String managedDomains;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "locked", nullable = false)
    private boolean locked;

    @Column(name = "failed_login_attempts", nullable = false)
    private int failedLoginAttempts;

    @Column(name = "lockout_expires_at")
    private Instant lockoutExpiresAt;

    @Column(name = "last_password_changed_at")
    private Instant lastPasswordChangedAt;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "last_login_ip", length = 45)
    private String lastLoginIp;

    @Column(name = "token_invalid_before")
    private Instant tokenInvalidBefore;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version")
    private long version;
}
