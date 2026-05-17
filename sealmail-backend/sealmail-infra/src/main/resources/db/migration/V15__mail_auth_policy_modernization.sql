CREATE TABLE IF NOT EXISTS mail_auth_policy (
    id                     VARCHAR(64)  PRIMARY KEY,
    enabled                BOOLEAN      NOT NULL DEFAULT TRUE,
    authserv_id            VARCHAR(254) NOT NULL DEFAULT 'sealmail-gateway',
    trusted_proxy_mode     VARCHAR(32)  NOT NULL DEFAULT 'DISABLED',
    failure_default_action VARCHAR(32)  NOT NULL DEFAULT 'LOG_ONLY',
    created_at             TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version                BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT chk_mail_auth_policy_trusted_proxy_mode
        CHECK (trusted_proxy_mode IN ('DISABLED', 'TRUSTED_HEADERS', 'XFORWARD')),
    CONSTRAINT chk_mail_auth_policy_failure_action
        CHECK (failure_default_action IN ('LOG_ONLY', 'APPLY_POLICY', 'FORCE_QUARANTINE', 'FORCE_ALLOW'))
);

CREATE TABLE IF NOT EXISTS mail_auth_domain_policy (
    id                       VARCHAR(128) PRIMARY KEY,
    domain_name              VARCHAR(253) NOT NULL,
    enabled                  BOOLEAN      NOT NULL DEFAULT TRUE,
    dkim_signing_enabled     BOOLEAN      NOT NULL DEFAULT FALSE,
    dkim_selector            VARCHAR(128) NOT NULL DEFAULT 'sealmail',
    dkim_key_secret_ref      VARCHAR(1024),
    dkim_key_path            VARCHAR(1024),
    dkim_signed_headers      TEXT         NOT NULL DEFAULT '[]',
    spf_publish_enabled      BOOLEAN      NOT NULL DEFAULT TRUE,
    spf_use_a                BOOLEAN      NOT NULL DEFAULT TRUE,
    spf_use_mx               BOOLEAN      NOT NULL DEFAULT TRUE,
    spf_ip4                  TEXT         NOT NULL DEFAULT '[]',
    spf_ip6                  TEXT         NOT NULL DEFAULT '[]',
    spf_includes             TEXT         NOT NULL DEFAULT '[]',
    spf_all_policy           VARCHAR(8)   NOT NULL DEFAULT '~all',
    dmarc_publish_enabled    BOOLEAN      NOT NULL DEFAULT TRUE,
    dmarc_policy             VARCHAR(16)  NOT NULL DEFAULT 'none',
    dmarc_subdomain_policy   VARCHAR(16)  NOT NULL DEFAULT 'none',
    dmarc_adkim              VARCHAR(1)   NOT NULL DEFAULT 'r',
    dmarc_aspf               VARCHAR(1)   NOT NULL DEFAULT 'r',
    dmarc_pct                INTEGER      NOT NULL DEFAULT 100,
    dmarc_rua                VARCHAR(2048),
    dmarc_ruf                VARCHAR(2048),
    created_at               TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at               TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version                  BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_mail_auth_domain_policy_domain UNIQUE (domain_name),
    CONSTRAINT chk_mail_auth_domain_policy_dkim_key_ref
        CHECK (dkim_key_secret_ref IS NULL OR dkim_key_path IS NULL),
    CONSTRAINT chk_mail_auth_domain_policy_spf_all
        CHECK (spf_all_policy IN ('+all', '-all', '~all', '?all')),
    CONSTRAINT chk_mail_auth_domain_policy_dmarc_policy
        CHECK (dmarc_policy IN ('none', 'quarantine', 'reject')),
    CONSTRAINT chk_mail_auth_domain_policy_dmarc_sp
        CHECK (dmarc_subdomain_policy IN ('none', 'quarantine', 'reject')),
    CONSTRAINT chk_mail_auth_domain_policy_adkim
        CHECK (dmarc_adkim IN ('r', 's')),
    CONSTRAINT chk_mail_auth_domain_policy_aspf
        CHECK (dmarc_aspf IN ('r', 's')),
    CONSTRAINT chk_mail_auth_domain_policy_pct
        CHECK (dmarc_pct BETWEEN 0 AND 100)
);

CREATE INDEX IF NOT EXISTS idx_mail_auth_domain_policy_domain
    ON mail_auth_domain_policy (domain_name);

CREATE TABLE IF NOT EXISTS mail_auth_dns_probe (
    id                  VARCHAR(128) PRIMARY KEY,
    domain_name          VARCHAR(253) NOT NULL,
    record_type          VARCHAR(32)  NOT NULL,
    expected_name        VARCHAR(512) NOT NULL,
    expected_value_hash  VARCHAR(128),
    observed_value       TEXT,
    status               VARCHAR(32)  NOT NULL,
    detail               VARCHAR(1024),
    checked_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT chk_mail_auth_dns_probe_status
        CHECK (status IN ('MATCH', 'MISMATCH', 'NOT_FOUND', 'TEMPERROR', 'ERROR'))
);

CREATE INDEX IF NOT EXISTS idx_mail_auth_dns_probe_domain_checked
    ON mail_auth_dns_probe (domain_name, checked_at DESC);
CREATE INDEX IF NOT EXISTS idx_mail_auth_dns_probe_type_checked
    ON mail_auth_dns_probe (record_type, checked_at DESC);
