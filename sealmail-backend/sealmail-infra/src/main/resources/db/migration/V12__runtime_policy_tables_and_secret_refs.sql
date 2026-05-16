ALTER TABLE mail_auth_config
    ADD COLUMN IF NOT EXISTS dkim_private_key_secret_ref VARCHAR(1024);

-- Keep existing dkim_private_key_pem values intact. Operators should migrate
-- existing DKIM keys to dkim_private_key_secret_ref before a future cleanup migration.

CREATE TABLE IF NOT EXISTS relay_policy (
    id                  VARCHAR(64)  PRIMARY KEY,
    enabled             BOOLEAN      NOT NULL DEFAULT FALSE,
    host                VARCHAR(254) NOT NULL DEFAULT 'localhost',
    port                INTEGER      NOT NULL DEFAULT 25,
    use_tls             BOOLEAN      NOT NULL DEFAULT FALSE,
    username            VARCHAR(254),
    password_secret_ref VARCHAR(1024),
    timeout_ms          INTEGER      NOT NULL DEFAULT 30000,
    envelope_from       VARCHAR(254),
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version             BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT chk_relay_policy_port CHECK (port BETWEEN 1 AND 65535),
    CONSTRAINT chk_relay_policy_timeout CHECK (timeout_ms > 0)
);

CREATE TABLE IF NOT EXISTS quarantine_policy (
    id                          VARCHAR(64) PRIMARY KEY,
    max_retention_days          INTEGER     NOT NULL DEFAULT 30,
    notification_enabled        BOOLEAN     NOT NULL DEFAULT FALSE,
    release_requires_encryption BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    version                     BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT chk_quarantine_policy_retention CHECK (max_retention_days > 0)
);

CREATE TABLE IF NOT EXISTS certificate_binding (
    id             VARCHAR(128)  PRIMARY KEY,
    domain_name    VARCHAR(253)  NOT NULL,
    owner_email    VARCHAR(254)  NOT NULL,
    purpose        VARCHAR(32)   NOT NULL,
    certificate_id VARCHAR(128)  NOT NULL,
    enabled        BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    version        BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT chk_certificate_binding_purpose CHECK (purpose IN ('ENCRYPTION', 'SIGNING'))
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_certificate_binding_owner_purpose
    ON certificate_binding (owner_email, purpose);
CREATE INDEX IF NOT EXISTS idx_certificate_binding_domain
    ON certificate_binding (domain_name);
CREATE INDEX IF NOT EXISTS idx_certificate_binding_certificate
    ON certificate_binding (certificate_id);
