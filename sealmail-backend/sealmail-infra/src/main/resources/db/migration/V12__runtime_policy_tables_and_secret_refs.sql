ALTER TABLE mail_auth_config
    ADD COLUMN IF NOT EXISTS dkim_private_key_secret_ref VARCHAR(1024);

UPDATE mail_auth_config
SET dkim_private_key_pem = NULL
WHERE dkim_private_key_pem IS NOT NULL;

ALTER TABLE mail_auth_config
    DROP COLUMN IF EXISTS dkim_private_key_pem;

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
