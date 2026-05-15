CREATE TABLE IF NOT EXISTS mail_auth_config (
    id                             VARCHAR(64)  NOT NULL,
    enabled                        BOOLEAN      NOT NULL DEFAULT TRUE,
    authserv_id                    VARCHAR(254) NOT NULL DEFAULT 'sealmail-gateway',
    skip_private_relay             BOOLEAN      NOT NULL DEFAULT TRUE,
    dkim_enabled                   BOOLEAN      NOT NULL DEFAULT TRUE,
    dkim_selector                  VARCHAR(128) NOT NULL DEFAULT 'sealmail',
    dkim_private_key_path          VARCHAR(1024),
    dkim_private_key_pem           TEXT,
    dkim_signed_headers            TEXT         NOT NULL DEFAULT '[]',
    spf_enabled                    BOOLEAN      NOT NULL DEFAULT TRUE,
    spf_max_dns_lookups            INTEGER      NOT NULL DEFAULT 10,
    spf_use_a                      BOOLEAN      NOT NULL DEFAULT TRUE,
    spf_use_mx                     BOOLEAN      NOT NULL DEFAULT TRUE,
    spf_ip4                        TEXT         NOT NULL DEFAULT '[]',
    spf_ip6                        TEXT         NOT NULL DEFAULT '[]',
    spf_includes                   TEXT         NOT NULL DEFAULT '[]',
    spf_all_policy                 VARCHAR(8)   NOT NULL DEFAULT '~all',
    dmarc_enabled                  BOOLEAN      NOT NULL DEFAULT TRUE,
    dmarc_policy                   VARCHAR(16)  NOT NULL DEFAULT 'quarantine',
    dmarc_adkim                    VARCHAR(1)   NOT NULL DEFAULT 'r',
    dmarc_aspf                     VARCHAR(1)   NOT NULL DEFAULT 'r',
    dmarc_pct                      INTEGER      NOT NULL DEFAULT 100,
    dmarc_rua                      VARCHAR(2048),
    dmarc_ruf                      VARCHAR(2048),
    dmarc_failure_action           VARCHAR(32)  NOT NULL DEFAULT 'LOG_ONLY',
    dmarc_quarantine_reject_policy BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at                     TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at                     TIMESTAMP    NOT NULL DEFAULT NOW(),
    version                        BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS dlp_pattern (
    id          VARCHAR(128) NOT NULL,
    name        VARCHAR(128) NOT NULL,
    description VARCHAR(512),
    regex       TEXT         NOT NULL,
    action      VARCHAR(32)  NOT NULL,
    severity    INTEGER      NOT NULL DEFAULT 5,
    priority    INTEGER      NOT NULL DEFAULT 100,
    enabled     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    version     BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS dlp_selection (
    id          VARCHAR(128) NOT NULL,
    scope_type  VARCHAR(32)  NOT NULL,
    scope_value VARCHAR(254),
    enabled     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    version     BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS certificate_request (
    id                    VARCHAR(64)  NOT NULL,
    csr_pem               TEXT         NOT NULL,
    requested_owner_email VARCHAR(254),
    submitted_by          VARCHAR(128),
    submitter_ip          VARCHAR(64),
    submitted_at          TIMESTAMP    NOT NULL DEFAULT NOW(),
    status                VARCHAR(16)  NOT NULL,
    decided_at            TIMESTAMP,
    decided_by            VARCHAR(128),
    decision_comment      VARCHAR(512),
    issued_cert_id        VARCHAR(128),
    intermediate_ca_id    VARCHAR(128),
    version               BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

ALTER TABLE certificate
    ADD COLUMN IF NOT EXISTS key_usages VARCHAR(1024),
    ADD COLUMN IF NOT EXISTS private_key_data TEXT,
    ADD COLUMN IF NOT EXISTS has_private_key BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS algorithm VARCHAR(32),
    ADD COLUMN IF NOT EXISTS revocation_date TIMESTAMP,
    ADD COLUMN IF NOT EXISTS revocation_crl_reason VARCHAR(32),
    ADD COLUMN IF NOT EXISTS is_ca BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS path_len_constraint INTEGER,
    ADD COLUMN IF NOT EXISTS issuer_cert_id VARCHAR(128),
    ADD COLUMN IF NOT EXISTS extended_key_usages VARCHAR(1024),
    ADD COLUMN IF NOT EXISTS crl_dp_url VARCHAR(1024);

ALTER TABLE certificate
    ALTER COLUMN serial_number DROP NOT NULL;

ALTER TABLE domain_config
    ADD COLUMN IF NOT EXISTS preferred_algorithm VARCHAR(32);

ALTER TABLE user_account
    ADD COLUMN IF NOT EXISTS token_invalid_before TIMESTAMP;

ALTER TABLE domain_event
    ALTER COLUMN event_data TYPE VARCHAR(8192) USING event_data::VARCHAR(8192);

ALTER TABLE mail_processing
    ALTER COLUMN recipients TYPE VARCHAR(2048) USING recipients::VARCHAR(2048),
    ALTER COLUMN routing_decision TYPE VARCHAR(2048) USING routing_decision::VARCHAR(2048);

ALTER TABLE dlp_quarantine_mail
    ALTER COLUMN recipients TYPE VARCHAR(2048) USING recipients::VARCHAR(2048);

ALTER TABLE exception_mail
    ALTER COLUMN recipients TYPE VARCHAR(2048) USING recipients::VARCHAR(2048);

ALTER TABLE mail_auth_config
    ADD COLUMN IF NOT EXISTS enabled BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS skip_private_relay BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS dkim_private_key_path VARCHAR(1024),
    ADD COLUMN IF NOT EXISTS dkim_private_key_pem TEXT,
    ADD COLUMN IF NOT EXISTS dkim_signed_headers TEXT NOT NULL DEFAULT '[]',
    ADD COLUMN IF NOT EXISTS spf_use_a BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS spf_use_mx BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS spf_ip4 TEXT NOT NULL DEFAULT '[]',
    ADD COLUMN IF NOT EXISTS spf_ip6 TEXT NOT NULL DEFAULT '[]',
    ADD COLUMN IF NOT EXISTS spf_includes TEXT NOT NULL DEFAULT '[]',
    ADD COLUMN IF NOT EXISTS spf_all_policy VARCHAR(8) NOT NULL DEFAULT '~all',
    ADD COLUMN IF NOT EXISTS dmarc_policy VARCHAR(16) NOT NULL DEFAULT 'quarantine',
    ADD COLUMN IF NOT EXISTS dmarc_adkim VARCHAR(1) NOT NULL DEFAULT 'r',
    ADD COLUMN IF NOT EXISTS dmarc_aspf VARCHAR(1) NOT NULL DEFAULT 'r',
    ADD COLUMN IF NOT EXISTS dmarc_pct INTEGER NOT NULL DEFAULT 100,
    ADD COLUMN IF NOT EXISTS dmarc_rua VARCHAR(2048),
    ADD COLUMN IF NOT EXISTS dmarc_ruf VARCHAR(2048),
    ADD COLUMN IF NOT EXISTS dmarc_failure_action VARCHAR(32) NOT NULL DEFAULT 'LOG_ONLY',
    ADD COLUMN IF NOT EXISTS dmarc_quarantine_reject_policy BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE dlp_pattern
    ADD COLUMN IF NOT EXISTS description VARCHAR(512),
    ADD COLUMN IF NOT EXISTS regex TEXT NOT NULL DEFAULT '',
    ADD COLUMN IF NOT EXISTS action VARCHAR(32) NOT NULL DEFAULT 'WARN',
    ADD COLUMN IF NOT EXISTS severity INTEGER NOT NULL DEFAULT 5,
    ADD COLUMN IF NOT EXISTS priority INTEGER NOT NULL DEFAULT 100,
    ADD COLUMN IF NOT EXISTS enabled BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE dlp_selection
    ADD COLUMN IF NOT EXISTS scope_value VARCHAR(254),
    ADD COLUMN IF NOT EXISTS enabled BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE certificate_request
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

CREATE INDEX IF NOT EXISTS idx_certreq_status ON certificate_request (status);
CREATE INDEX IF NOT EXISTS idx_certreq_submitted_at ON certificate_request (submitted_at);
