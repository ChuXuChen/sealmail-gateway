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
@Table(name = "mail_auth_domain_policy")
public class MailAuthDomainPolicyEntity {

    @Id
    @Column(name = "id", length = 128)
    private String id;

    @Column(name = "domain_name", nullable = false, length = 253, unique = true)
    private String domainName;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "dkim_signing_enabled", nullable = false)
    private boolean dkimSigningEnabled;

    @Column(name = "dkim_selector", nullable = false, length = 128)
    private String dkimSelector;

    @Column(name = "dkim_key_secret_ref", length = 1024)
    private String dkimKeySecretRef;

    @Column(name = "dkim_key_path", length = 1024)
    private String dkimKeyPath;

    @Column(name = "dkim_signed_headers", nullable = false, columnDefinition = "TEXT")
    private String dkimSignedHeaders;

    @Column(name = "spf_publish_enabled", nullable = false)
    private boolean spfPublishEnabled;

    @Column(name = "spf_use_a", nullable = false)
    private boolean spfUseA;

    @Column(name = "spf_use_mx", nullable = false)
    private boolean spfUseMx;

    @Column(name = "spf_ip4", nullable = false, columnDefinition = "TEXT")
    private String spfIp4;

    @Column(name = "spf_ip6", nullable = false, columnDefinition = "TEXT")
    private String spfIp6;

    @Column(name = "spf_includes", nullable = false, columnDefinition = "TEXT")
    private String spfIncludes;

    @Column(name = "spf_all_policy", nullable = false, length = 8)
    private String spfAllPolicy;

    @Column(name = "dmarc_publish_enabled", nullable = false)
    private boolean dmarcPublishEnabled;

    @Column(name = "dmarc_policy", nullable = false, length = 16)
    private String dmarcPolicy;

    @Column(name = "dmarc_subdomain_policy", nullable = false, length = 16)
    private String dmarcSubdomainPolicy;

    @Column(name = "dmarc_adkim", nullable = false, length = 1)
    private String dmarcAdkim;

    @Column(name = "dmarc_aspf", nullable = false, length = 1)
    private String dmarcAspf;

    @Column(name = "dmarc_pct", nullable = false)
    private int dmarcPct;

    @Column(name = "dmarc_rua", length = 2048)
    private String dmarcRua;

    @Column(name = "dmarc_ruf", length = 2048)
    private String dmarcRuf;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version")
    private long version;
}
