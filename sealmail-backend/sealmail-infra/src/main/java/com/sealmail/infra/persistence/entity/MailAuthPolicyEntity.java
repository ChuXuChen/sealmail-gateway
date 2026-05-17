package com.sealmail.infra.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Data;

import java.time.Instant;

@Data
@Entity
@Table(name = "mail_auth_policy")
public class MailAuthPolicyEntity {

    @Id
    @Column(name = "id", length = 64)
    private String id;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "authserv_id", nullable = false, length = 254)
    private String authservId;

    @Column(name = "trusted_proxy_mode", nullable = false, length = 32)
    private String trustedProxyMode;

    @Column(name = "failure_default_action", nullable = false, length = 32)
    private String failureDefaultAction;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version")
    private long version;
}
