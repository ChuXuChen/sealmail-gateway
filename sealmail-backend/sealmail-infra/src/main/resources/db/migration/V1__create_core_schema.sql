-- SealMail core schema managed by Flyway.
-- Hibernate validates this schema at runtime; it must not auto-create tables.

CREATE TABLE certificate (
    thumbprint              VARCHAR(128) NOT NULL,
    owner_email             VARCHAR(254) NOT NULL,
    pem_content             TEXT         NOT NULL,
    alias                   VARCHAR(255),
    not_before              TIMESTAMP    NOT NULL,
    not_after               TIMESTAMP    NOT NULL,
    key_usages              VARCHAR(1024),
    issuer_dn               VARCHAR(1024),
    subject_dn              VARCHAR(1024),
    serial_number           VARCHAR(128),
    subject_key_id          VARCHAR(128),
    private_key_data        TEXT,
    has_private_key         BOOLEAN      NOT NULL DEFAULT FALSE,
    algorithm               VARCHAR(32),
    trusted                 BOOLEAN      NOT NULL DEFAULT FALSE,
    revoked                 BOOLEAN      NOT NULL DEFAULT FALSE,
    revocation_reason       VARCHAR(512),
    revocation_date         TIMESTAMP,
    revocation_crl_reason   VARCHAR(32),
    is_ca                   BOOLEAN      NOT NULL DEFAULT FALSE,
    path_len_constraint     INTEGER,
    issuer_cert_id          VARCHAR(128),
    extended_key_usages     VARCHAR(1024),
    crl_dp_url              VARCHAR(1024),
    created_at              TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMP    NOT NULL DEFAULT NOW(),
    version                 BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (thumbprint)
);

CREATE INDEX idx_certificate_owner ON certificate (owner_email);
CREATE INDEX idx_certificate_trusted ON certificate (owner_email)
    WHERE trusted = TRUE AND revoked = FALSE;

CREATE TABLE domain_config (
    id                  VARCHAR(128) NOT NULL,
    domain_name         VARCHAR(254) NOT NULL,
    is_local            BOOLEAN      NOT NULL DEFAULT FALSE,
    encryption_policy   VARCHAR(32)  NOT NULL DEFAULT 'ALLOW',
    signing_enabled     BOOLEAN      NOT NULL DEFAULT FALSE,
    dkim_enabled        BOOLEAN      NOT NULL DEFAULT FALSE,
    preferred_algorithm VARCHAR(32),
    active              BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP    NOT NULL DEFAULT NOW(),
    version             BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

CREATE UNIQUE INDEX idx_domain_config_domain ON domain_config (domain_name);
CREATE INDEX idx_domain_config_active ON domain_config (active) WHERE active = TRUE;

CREATE TABLE dlp_quarantine_mail (
    id              VARCHAR(128)  NOT NULL,
    message_id      VARCHAR(254)  NOT NULL,
    subject         VARCHAR(1024),
    sender_email    VARCHAR(254)  NOT NULL,
    recipients      VARCHAR(2048) NOT NULL,
    direction       VARCHAR(16),
    remote_address  VARCHAR(128),
    reason          VARCHAR(64)   NOT NULL,
    detail          VARCHAR(1024),
    raw_content     TEXT,
    status          VARCHAR(32)   NOT NULL DEFAULT 'QUARANTINED',
    created_at      TIMESTAMP     NOT NULL DEFAULT NOW(),
    resolved_at     TIMESTAMP,
    processed_by    VARCHAR(254),
    process_comment VARCHAR(1024),
    version         BIGINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

CREATE INDEX idx_dlp_quarantine_status ON dlp_quarantine_mail (status);
CREATE INDEX idx_dlp_quarantine_message ON dlp_quarantine_mail (message_id);

CREATE TABLE exception_mail (
    id              VARCHAR(128)  NOT NULL,
    message_id      VARCHAR(254)  NOT NULL,
    subject         VARCHAR(1024),
    sender_email    VARCHAR(254)  NOT NULL,
    recipients      VARCHAR(2048) NOT NULL,
    direction       VARCHAR(16),
    remote_address  VARCHAR(128),
    reason          VARCHAR(64)   NOT NULL,
    detail          VARCHAR(1024),
    blocked_by      VARCHAR(254)  NOT NULL DEFAULT 'system',
    block_comment   VARCHAR(1024),
    created_at      TIMESTAMP     NOT NULL DEFAULT NOW(),
    version         BIGINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

CREATE INDEX idx_exception_mail_message ON exception_mail (message_id);
CREATE INDEX idx_exception_mail_reason ON exception_mail (reason);
CREATE INDEX idx_exception_mail_created ON exception_mail (created_at DESC);

CREATE TABLE user_account (
    id                       VARCHAR(128) NOT NULL,
    username                 VARCHAR(64)  NOT NULL,
    email                    VARCHAR(254) NOT NULL,
    password_hash            VARCHAR(256) NOT NULL,
    roles                    VARCHAR(1024) NOT NULL,
    managed_domains          VARCHAR(2048),
    active                   BOOLEAN      NOT NULL DEFAULT TRUE,
    locked                   BOOLEAN      NOT NULL DEFAULT FALSE,
    failed_login_attempts    INTEGER      NOT NULL DEFAULT 0,
    lockout_expires_at       TIMESTAMP,
    last_password_changed_at TIMESTAMP,
    last_login_at            TIMESTAMP,
    last_login_ip            VARCHAR(45),
    token_invalid_before     TIMESTAMP,
    created_at               TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at               TIMESTAMP    NOT NULL DEFAULT NOW(),
    version                  BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

CREATE UNIQUE INDEX idx_user_email ON user_account (email);
CREATE UNIQUE INDEX idx_user_username ON user_account (username);

CREATE TABLE mail_processing (
    id               VARCHAR(128)  NOT NULL,
    message_id       VARCHAR(254)  NOT NULL,
    direction        VARCHAR(16)   NOT NULL,
    sender_email     VARCHAR(254)  NOT NULL,
    recipients       VARCHAR(2048) NOT NULL,
    remote_host      VARCHAR(254),
    helo             VARCHAR(254),
    received_at      TIMESTAMP     NOT NULL,
    routing_decision VARCHAR(2048),
    result           VARCHAR(16),
    created_at       TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP     NOT NULL DEFAULT NOW(),
    version          BIGINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

CREATE INDEX idx_mail_proc_message ON mail_processing (message_id);
CREATE INDEX idx_mail_proc_result ON mail_processing (result);

CREATE TABLE domain_event (
    event_id       VARCHAR(128)  NOT NULL,
    aggregate_id   VARCHAR(254)  NOT NULL,
    aggregate_type VARCHAR(128)  NOT NULL,
    event_type     VARCHAR(128)  NOT NULL,
    event_data     VARCHAR(8192) NOT NULL,
    occurred_at    TIMESTAMP     NOT NULL,
    PRIMARY KEY (event_id)
);

CREATE INDEX idx_event_aggregate ON domain_event (aggregate_id, aggregate_type);
CREATE INDEX idx_event_type ON domain_event (event_type);
CREATE INDEX idx_event_occurred ON domain_event (occurred_at);

CREATE TABLE mail_auth_config (
    id                             VARCHAR(64)  NOT NULL,
    enabled                        BOOLEAN      NOT NULL DEFAULT TRUE,
    authserv_id                    VARCHAR(254) NOT NULL,
    skip_private_relay             BOOLEAN      NOT NULL DEFAULT TRUE,
    dkim_enabled                   BOOLEAN      NOT NULL DEFAULT TRUE,
    dkim_selector                  VARCHAR(128) NOT NULL,
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

CREATE TABLE dlp_pattern (
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

CREATE TABLE dlp_selection (
    id          VARCHAR(128) NOT NULL,
    scope_type  VARCHAR(32)  NOT NULL,
    scope_value VARCHAR(254),
    enabled     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    version     BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

CREATE TABLE certificate_request (
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

CREATE INDEX idx_certreq_status ON certificate_request (status);
CREATE INDEX idx_certreq_submitted_at ON certificate_request (submitted_at);
