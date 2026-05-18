-- SealMail clean baseline schema.
-- This migration is intentionally destructive relative to earlier development schemas:
-- start from an empty database/schema and migrate from V1.

CREATE TABLE certificate (
    thumbprint             VARCHAR(128) PRIMARY KEY,
    owner_email            VARCHAR(254) NOT NULL,
    pem_content            TEXT         NOT NULL,
    alias                  VARCHAR(255),
    not_before             TIMESTAMPTZ  NOT NULL,
    not_after              TIMESTAMPTZ  NOT NULL,
    key_usages             VARCHAR(1024),
    issuer_dn              VARCHAR(1024),
    subject_dn             VARCHAR(1024),
    serial_number          VARCHAR(128),
    subject_key_id         VARCHAR(128),
    private_key_secret_ref VARCHAR(1024),
    has_private_key        BOOLEAN      NOT NULL DEFAULT FALSE,
    algorithm              VARCHAR(32),
    trusted                BOOLEAN      NOT NULL DEFAULT FALSE,
    revoked                BOOLEAN      NOT NULL DEFAULT FALSE,
    revocation_reason      VARCHAR(512),
    revocation_date        TIMESTAMPTZ,
    revocation_crl_reason  VARCHAR(32),
    is_ca                  BOOLEAN      NOT NULL DEFAULT FALSE,
    path_len_constraint    INTEGER,
    issuer_cert_id         VARCHAR(128),
    extended_key_usages    VARCHAR(1024),
    crl_dp_url             VARCHAR(1024),
    imported_crl_pem       TEXT,
    created_at             TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version                BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT chk_certificate_validity
        CHECK (not_after > not_before),
    CONSTRAINT chk_certificate_private_key_secret_ref
        CHECK (private_key_secret_ref IS NULL OR btrim(private_key_secret_ref) <> ''),
    CONSTRAINT chk_certificate_path_len
        CHECK (path_len_constraint IS NULL OR path_len_constraint >= 0),
    CONSTRAINT fk_certificate_issuer
        FOREIGN KEY (issuer_cert_id) REFERENCES certificate (thumbprint) ON DELETE SET NULL
);

CREATE INDEX idx_certificate_owner ON certificate (owner_email);
CREATE INDEX idx_certificate_trusted ON certificate (owner_email)
    WHERE trusted = TRUE AND revoked = FALSE;
CREATE INDEX idx_certificate_issuer ON certificate (issuer_cert_id);

CREATE TABLE certificate_request (
    id                    VARCHAR(64) PRIMARY KEY,
    csr_pem               TEXT        NOT NULL,
    requested_owner_email VARCHAR(254),
    submitted_by          VARCHAR(128),
    submitter_ip          VARCHAR(64),
    submitted_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    status                VARCHAR(16) NOT NULL,
    decided_at            TIMESTAMPTZ,
    decided_by            VARCHAR(128),
    decision_comment      VARCHAR(512),
    issued_cert_id        VARCHAR(128),
    intermediate_ca_id    VARCHAR(128),
    version               BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT chk_certificate_request_status
        CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'ISSUED')),
    CONSTRAINT fk_certificate_request_issued_cert
        FOREIGN KEY (issued_cert_id) REFERENCES certificate (thumbprint) ON DELETE SET NULL,
    CONSTRAINT fk_certificate_request_intermediate_ca
        FOREIGN KEY (intermediate_ca_id) REFERENCES certificate (thumbprint) ON DELETE SET NULL
);

CREATE INDEX idx_certreq_status ON certificate_request (status);
CREATE INDEX idx_certreq_submitted_at ON certificate_request (submitted_at);

CREATE TABLE certificate_binding (
    id             VARCHAR(128) PRIMARY KEY,
    domain_name    VARCHAR(253) NOT NULL,
    owner_email    VARCHAR(254) NOT NULL,
    purpose        VARCHAR(32)  NOT NULL,
    certificate_id VARCHAR(128) NOT NULL,
    enabled        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version        BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT chk_certificate_binding_purpose
        CHECK (purpose IN ('ENCRYPTION', 'SIGNING')),
    CONSTRAINT fk_certificate_binding_certificate
        FOREIGN KEY (certificate_id) REFERENCES certificate (thumbprint) ON DELETE CASCADE
);

CREATE UNIQUE INDEX idx_certificate_binding_owner_purpose
    ON certificate_binding (owner_email, purpose);
CREATE INDEX idx_certificate_binding_domain
    ON certificate_binding (domain_name);
CREATE INDEX idx_certificate_binding_certificate
    ON certificate_binding (certificate_id);

CREATE TABLE domain_config (
    id                  VARCHAR(128) PRIMARY KEY,
    domain_name         VARCHAR(254) NOT NULL,
    is_local            BOOLEAN      NOT NULL DEFAULT FALSE,
    encryption_policy   VARCHAR(32)  NOT NULL DEFAULT 'ALLOW',
    signing_enabled     BOOLEAN      NOT NULL DEFAULT FALSE,
    dkim_enabled        BOOLEAN      NOT NULL DEFAULT FALSE,
    delivery_host       VARCHAR(255),
    delivery_port       INTEGER,
    preferred_algorithm VARCHAR(32),
    active              BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version             BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_domain_config_domain UNIQUE (domain_name),
    CONSTRAINT chk_domain_config_encryption_policy
        CHECK (encryption_policy IN ('NO_ENCRYPTION', 'ALLOW', 'MANDATORY')),
    CONSTRAINT chk_domain_config_delivery_port
        CHECK (delivery_port IS NULL OR delivery_port BETWEEN 1 AND 65535),
    CONSTRAINT chk_domain_config_preferred_algorithm
        CHECK (preferred_algorithm IS NULL OR preferred_algorithm IN ('AUTO', 'GM_ONLY', 'STANDARD_ONLY'))
);

CREATE INDEX idx_domain_config_active ON domain_config (active) WHERE active = TRUE;

CREATE TABLE user_account (
    id                       VARCHAR(128) PRIMARY KEY,
    username                 VARCHAR(64)  NOT NULL,
    email                    VARCHAR(254) NOT NULL,
    password_hash            VARCHAR(256) NOT NULL,
    active                   BOOLEAN      NOT NULL DEFAULT TRUE,
    locked                   BOOLEAN      NOT NULL DEFAULT FALSE,
    failed_login_attempts    INTEGER      NOT NULL DEFAULT 0,
    lockout_expires_at       TIMESTAMPTZ,
    last_password_changed_at TIMESTAMPTZ,
    last_login_at            TIMESTAMPTZ,
    last_login_ip            VARCHAR(45),
    token_invalid_before     TIMESTAMPTZ,
    created_at               TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at               TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version                  BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_user_account_username UNIQUE (username),
    CONSTRAINT uk_user_account_email UNIQUE (email),
    CONSTRAINT chk_user_failed_login_attempts
        CHECK (failed_login_attempts >= 0)
);

CREATE TABLE user_account_role (
    user_account_id VARCHAR(128) NOT NULL,
    role            VARCHAR(64)  NOT NULL,
    PRIMARY KEY (user_account_id, role),
    CONSTRAINT fk_user_account_role_user
        FOREIGN KEY (user_account_id) REFERENCES user_account (id) ON DELETE CASCADE
);

CREATE TABLE user_managed_domain (
    user_account_id VARCHAR(128) NOT NULL,
    domain_name     VARCHAR(254) NOT NULL,
    PRIMARY KEY (user_account_id, domain_name),
    CONSTRAINT fk_user_managed_domain_user
        FOREIGN KEY (user_account_id) REFERENCES user_account (id) ON DELETE CASCADE
);

CREATE TABLE mail_processing (
    id               VARCHAR(128) PRIMARY KEY,
    message_id       VARCHAR(254) NOT NULL,
    direction        VARCHAR(16)  NOT NULL,
    sender_email     VARCHAR(254) NOT NULL,
    remote_host      VARCHAR(254),
    helo             VARCHAR(254),
    received_at      TIMESTAMPTZ  NOT NULL,
    routing_decision VARCHAR(2048),
    result           VARCHAR(16),
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version          BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT chk_mail_processing_direction
        CHECK (direction IN ('INBOUND', 'OUTBOUND')),
    CONSTRAINT chk_mail_processing_result
        CHECK (result IS NULL OR result IN ('SUCCESS', 'FAILED', 'EXCEPTION'))
);

CREATE INDEX idx_mail_proc_message ON mail_processing (message_id);
CREATE INDEX idx_mail_proc_result ON mail_processing (result);

CREATE TABLE mail_processing_recipient (
    mail_processing_id VARCHAR(128) NOT NULL,
    position           INTEGER      NOT NULL,
    recipient_email    VARCHAR(254) NOT NULL,
    PRIMARY KEY (mail_processing_id, position),
    CONSTRAINT fk_mail_processing_recipient_processing
        FOREIGN KEY (mail_processing_id) REFERENCES mail_processing (id) ON DELETE CASCADE
);

CREATE TABLE mail_processing_step (
    id                 VARCHAR(128) PRIMARY KEY,
    mail_processing_id VARCHAR(128) NOT NULL,
    step_name          VARCHAR(128) NOT NULL,
    completed          BOOLEAN      NOT NULL DEFAULT FALSE,
    success            BOOLEAN      NOT NULL DEFAULT FALSE,
    error_message      VARCHAR(1024),
    started_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    completed_at       TIMESTAMPTZ,
    CONSTRAINT fk_mail_processing_step_processing
        FOREIGN KEY (mail_processing_id) REFERENCES mail_processing (id) ON DELETE CASCADE
);

CREATE INDEX idx_mail_processing_step_processing
    ON mail_processing_step (mail_processing_id, started_at);

CREATE TABLE domain_event (
    event_id       VARCHAR(128)  PRIMARY KEY,
    aggregate_id   VARCHAR(254)  NOT NULL,
    aggregate_type VARCHAR(128)  NOT NULL,
    event_type     VARCHAR(128)  NOT NULL,
    event_data     VARCHAR(8192) NOT NULL,
    occurred_at    TIMESTAMPTZ   NOT NULL
);

CREATE INDEX idx_event_aggregate ON domain_event (aggregate_id, aggregate_type);
CREATE INDEX idx_event_type ON domain_event (event_type);
CREATE INDEX idx_event_occurred ON domain_event (occurred_at);

CREATE TABLE audit_log (
    id            VARCHAR(128) PRIMARY KEY,
    type          VARCHAR(64)  NOT NULL,
    user_id       VARCHAR(128),
    username      VARCHAR(64),
    ip_address    VARCHAR(45),
    resource_type VARCHAR(64),
    resource_id   VARCHAR(128),
    action        VARCHAR(64),
    detail        VARCHAR(2048),
    success       BOOLEAN      NOT NULL DEFAULT TRUE,
    error_message VARCHAR(1024),
    occurred_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_audit_type ON audit_log (type);
CREATE INDEX idx_audit_user_id ON audit_log (user_id);
CREATE INDEX idx_audit_occurred_at ON audit_log (occurred_at);
CREATE INDEX idx_audit_resource ON audit_log (resource_type, resource_id);

CREATE TABLE mail_raw_content (
    id           VARCHAR(128) PRIMARY KEY,
    content      TEXT         NOT NULL,
    sha256       VARCHAR(64)  NOT NULL,
    size_bytes   BIGINT       NOT NULL,
    content_type VARCHAR(128) NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT chk_mail_raw_content_sha256
        CHECK (sha256 ~ '^[0-9a-f]{64}$'),
    CONSTRAINT chk_mail_raw_content_size
        CHECK (size_bytes >= 0)
);

CREATE INDEX idx_mail_raw_content_sha256 ON mail_raw_content (sha256);
CREATE INDEX idx_mail_raw_content_created ON mail_raw_content (created_at);

CREATE TABLE dlp_pattern (
    id                 VARCHAR(128) PRIMARY KEY,
    name               VARCHAR(128) NOT NULL,
    description        VARCHAR(512),
    regex              TEXT,
    rule_type          VARCHAR(32)  NOT NULL DEFAULT 'PATTERN',
    builtin_code       VARCHAR(128),
    content_kinds      TEXT,
    min_match_count    INTEGER      NOT NULL DEFAULT 1,
    max_evidence_count INTEGER      NOT NULL DEFAULT 5,
    masking_strategy   VARCHAR(32)  NOT NULL DEFAULT 'DEFAULT',
    action             VARCHAR(32)  NOT NULL,
    severity           INTEGER      NOT NULL DEFAULT 5,
    priority           INTEGER      NOT NULL DEFAULT 100,
    enabled            BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version            BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT chk_dlp_pattern_rule_type
        CHECK (rule_type IN ('PATTERN', 'EDM', 'FINGERPRINT', 'REGEX', 'KEYWORD', 'BUILTIN', 'DICTIONARY', 'COMPOSITE')),
    CONSTRAINT chk_dlp_pattern_masking_strategy
        CHECK (masking_strategy IN ('DEFAULT', 'PARTIAL', 'FULL', 'HASH_ONLY', 'EMAIL', 'SECRET')),
    CONSTRAINT chk_dlp_pattern_action
        CHECK (action IN ('WARN', 'MUST_ENCRYPT', 'QUARANTINE', 'BLOCK')),
    CONSTRAINT chk_dlp_pattern_min_match_count
        CHECK (min_match_count >= 1),
    CONSTRAINT chk_dlp_pattern_max_evidence_count
        CHECK (max_evidence_count BETWEEN 1 AND 100),
    CONSTRAINT chk_dlp_pattern_severity
        CHECK (severity BETWEEN 1 AND 10)
);

CREATE INDEX idx_dlp_pattern_enabled_priority
    ON dlp_pattern (enabled, priority, name);

CREATE TABLE dlp_selection (
    id           VARCHAR(128) PRIMARY KEY,
    scope_type   VARCHAR(32)  NOT NULL,
    scope_value  VARCHAR(254),
    all_patterns BOOLEAN      NOT NULL DEFAULT FALSE,
    enabled      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version      BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT chk_dlp_selection_scope_type
        CHECK (scope_type IN ('GLOBAL', 'SENDER_DOMAIN', 'RECIPIENT_DOMAIN')),
    CONSTRAINT chk_dlp_selection_scope_value
        CHECK ((scope_type = 'GLOBAL' AND scope_value IS NULL)
            OR (scope_type <> 'GLOBAL' AND scope_value IS NOT NULL))
);

CREATE TABLE dlp_selection_pattern (
    selection_id VARCHAR(128) NOT NULL,
    position     INTEGER      NOT NULL,
    pattern_id   VARCHAR(128) NOT NULL,
    PRIMARY KEY (selection_id, position),
    CONSTRAINT fk_dlp_selection_pattern_selection
        FOREIGN KEY (selection_id) REFERENCES dlp_selection (id) ON DELETE CASCADE,
    CONSTRAINT fk_dlp_selection_pattern_pattern
        FOREIGN KEY (pattern_id) REFERENCES dlp_pattern (id) ON DELETE CASCADE
);

CREATE INDEX idx_dlp_selection_pattern_pattern
    ON dlp_selection_pattern (pattern_id);

CREATE TABLE dlp_rule_group (
    id          VARCHAR(128) PRIMARY KEY,
    name        VARCHAR(128) NOT NULL,
    description VARCHAR(512),
    enabled     BOOLEAN      NOT NULL DEFAULT TRUE,
    priority    INTEGER      NOT NULL DEFAULT 100,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version     BIGINT       NOT NULL DEFAULT 0
);

CREATE TABLE dlp_rule_group_item (
    id            VARCHAR(128) PRIMARY KEY,
    rule_group_id VARCHAR(128) NOT NULL,
    rule_id       VARCHAR(128) NOT NULL,
    position      INTEGER      NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT fk_dlp_rule_group_item_group
        FOREIGN KEY (rule_group_id) REFERENCES dlp_rule_group (id) ON DELETE CASCADE,
    CONSTRAINT fk_dlp_rule_group_item_rule
        FOREIGN KEY (rule_id) REFERENCES dlp_pattern (id) ON DELETE CASCADE
);

CREATE UNIQUE INDEX idx_dlp_rule_group_item_unique
    ON dlp_rule_group_item (rule_group_id, rule_id);
CREATE INDEX idx_dlp_rule_group_item_group
    ON dlp_rule_group_item (rule_group_id, position);

CREATE TABLE dlp_policy (
    id                  VARCHAR(128) PRIMARY KEY,
    name                VARCHAR(128) NOT NULL,
    description         VARCHAR(512),
    mode                VARCHAR(32)  NOT NULL DEFAULT 'ENFORCE',
    direction           VARCHAR(16),
    attachment_required BOOLEAN      NOT NULL DEFAULT FALSE,
    enabled             BOOLEAN      NOT NULL DEFAULT TRUE,
    priority            INTEGER      NOT NULL DEFAULT 100,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version             BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT chk_dlp_policy_mode
        CHECK (mode IN ('MONITOR', 'ENFORCE')),
    CONSTRAINT chk_dlp_policy_direction
        CHECK (direction IS NULL OR direction IN ('INBOUND', 'OUTBOUND'))
);

CREATE INDEX idx_dlp_policy_enabled_priority
    ON dlp_policy (enabled, priority, name);

CREATE TABLE dlp_policy_sender_domain (
    policy_id   VARCHAR(128) NOT NULL,
    position    INTEGER      NOT NULL,
    domain_name VARCHAR(254) NOT NULL,
    PRIMARY KEY (policy_id, position),
    CONSTRAINT fk_dlp_policy_sender_domain_policy
        FOREIGN KEY (policy_id) REFERENCES dlp_policy (id) ON DELETE CASCADE
);

CREATE TABLE dlp_policy_recipient_domain (
    policy_id   VARCHAR(128) NOT NULL,
    position    INTEGER      NOT NULL,
    domain_name VARCHAR(254) NOT NULL,
    PRIMARY KEY (policy_id, position),
    CONSTRAINT fk_dlp_policy_recipient_domain_policy
        FOREIGN KEY (policy_id) REFERENCES dlp_policy (id) ON DELETE CASCADE
);

CREATE TABLE dlp_policy_sender_address_pattern (
    policy_id       VARCHAR(128) NOT NULL,
    position        INTEGER      NOT NULL,
    address_pattern VARCHAR(512) NOT NULL,
    PRIMARY KEY (policy_id, position),
    CONSTRAINT fk_dlp_policy_sender_address_policy
        FOREIGN KEY (policy_id) REFERENCES dlp_policy (id) ON DELETE CASCADE
);

CREATE TABLE dlp_policy_recipient_address_pattern (
    policy_id       VARCHAR(128) NOT NULL,
    position        INTEGER      NOT NULL,
    address_pattern VARCHAR(512) NOT NULL,
    PRIMARY KEY (policy_id, position),
    CONSTRAINT fk_dlp_policy_recipient_address_policy
        FOREIGN KEY (policy_id) REFERENCES dlp_policy (id) ON DELETE CASCADE
);

CREATE TABLE dlp_policy_rule_group (
    id            VARCHAR(128) PRIMARY KEY,
    policy_id     VARCHAR(128) NOT NULL,
    rule_group_id VARCHAR(128) NOT NULL,
    position      INTEGER      NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT fk_dlp_policy_rule_group_policy
        FOREIGN KEY (policy_id) REFERENCES dlp_policy (id) ON DELETE CASCADE,
    CONSTRAINT fk_dlp_policy_rule_group_group
        FOREIGN KEY (rule_group_id) REFERENCES dlp_rule_group (id) ON DELETE CASCADE
);

CREATE UNIQUE INDEX idx_dlp_policy_rule_group_unique
    ON dlp_policy_rule_group (policy_id, rule_group_id);
CREATE INDEX idx_dlp_policy_rule_group_policy
    ON dlp_policy_rule_group (policy_id, position);

CREATE TABLE dlp_scan_event (
    id                     VARCHAR(128) PRIMARY KEY,
    message_id             VARCHAR(254),
    processing_id          VARCHAR(128),
    direction              VARCHAR(16),
    sender_email           VARCHAR(254),
    subject                VARCHAR(1024),
    remote_address         VARCHAR(128),
    action                 VARCHAR(32)  NOT NULL,
    max_severity           INTEGER      NOT NULL DEFAULT 0,
    match_count            INTEGER      NOT NULL DEFAULT 0,
    monitor_mode           BOOLEAN      NOT NULL DEFAULT FALSE,
    scan_duration_ms       BIGINT       NOT NULL DEFAULT 0,
    uba_risk_level         VARCHAR(16)  NOT NULL DEFAULT 'LOW',
    uba_action_upgraded    BOOLEAN      NOT NULL DEFAULT FALSE,
    quarantine_id          VARCHAR(128),
    false_positive         BOOLEAN      NOT NULL DEFAULT FALSE,
    false_positive_at      TIMESTAMPTZ,
    false_positive_by      VARCHAR(254),
    false_positive_comment VARCHAR(1024),
    created_at             TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT chk_dlp_scan_event_direction
        CHECK (direction IS NULL OR direction IN ('INBOUND', 'OUTBOUND')),
    CONSTRAINT chk_dlp_scan_event_action
        CHECK (action IN ('WARN', 'MUST_ENCRYPT', 'QUARANTINE', 'BLOCK')),
    CONSTRAINT chk_dlp_scan_event_severity
        CHECK (max_severity >= 0),
    CONSTRAINT chk_dlp_scan_event_match_count
        CHECK (match_count >= 0),
    CONSTRAINT chk_dlp_scan_event_duration
        CHECK (scan_duration_ms >= 0),
    CONSTRAINT chk_dlp_scan_event_uba_risk
        CHECK (uba_risk_level IN ('LOW', 'MEDIUM', 'HIGH'))
);

CREATE INDEX idx_dlp_scan_event_created ON dlp_scan_event (created_at DESC);
CREATE INDEX idx_dlp_scan_event_action ON dlp_scan_event (action);
CREATE INDEX idx_dlp_scan_event_message ON dlp_scan_event (message_id);
CREATE INDEX idx_dlp_scan_event_quarantine ON dlp_scan_event (quarantine_id);
CREATE INDEX idx_dlp_scan_event_sender ON dlp_scan_event (sender_email);
CREATE INDEX idx_dlp_scan_event_uba_risk ON dlp_scan_event (uba_risk_level);

CREATE TABLE dlp_scan_event_recipient (
    event_id        VARCHAR(128) NOT NULL,
    position        INTEGER      NOT NULL,
    recipient_email VARCHAR(254) NOT NULL,
    PRIMARY KEY (event_id, position),
    CONSTRAINT fk_dlp_scan_event_recipient_event
        FOREIGN KEY (event_id) REFERENCES dlp_scan_event (id) ON DELETE CASCADE
);

CREATE TABLE dlp_scan_event_policy (
    event_id  VARCHAR(128) NOT NULL,
    position  INTEGER      NOT NULL,
    policy_id VARCHAR(128) NOT NULL,
    PRIMARY KEY (event_id, position),
    CONSTRAINT fk_dlp_scan_event_policy_event
        FOREIGN KEY (event_id) REFERENCES dlp_scan_event (id) ON DELETE CASCADE
);

CREATE TABLE dlp_scan_event_rule_group (
    event_id      VARCHAR(128) NOT NULL,
    position      INTEGER      NOT NULL,
    rule_group_id VARCHAR(128) NOT NULL,
    PRIMARY KEY (event_id, position),
    CONSTRAINT fk_dlp_scan_event_rule_group_event
        FOREIGN KEY (event_id) REFERENCES dlp_scan_event (id) ON DELETE CASCADE
);

CREATE TABLE dlp_scan_event_extraction_warning (
    event_id VARCHAR(128)  NOT NULL,
    position INTEGER       NOT NULL,
    warning  VARCHAR(1024) NOT NULL,
    PRIMARY KEY (event_id, position),
    CONSTRAINT fk_dlp_scan_event_warning_event
        FOREIGN KEY (event_id) REFERENCES dlp_scan_event (id) ON DELETE CASCADE
);

CREATE TABLE dlp_scan_event_uba_risk_reason (
    event_id    VARCHAR(128)  NOT NULL,
    position    INTEGER       NOT NULL,
    risk_reason VARCHAR(1024) NOT NULL,
    PRIMARY KEY (event_id, position),
    CONSTRAINT fk_dlp_scan_event_uba_reason_event
        FOREIGN KEY (event_id) REFERENCES dlp_scan_event (id) ON DELETE CASCADE
);

CREATE TABLE dlp_scan_evidence (
    id             VARCHAR(128) PRIMARY KEY,
    event_id       VARCHAR(128) NOT NULL,
    rule_id        VARCHAR(128),
    rule_name      VARCHAR(128) NOT NULL,
    rule_type      VARCHAR(32)  NOT NULL,
    part_id        VARCHAR(128) NOT NULL,
    part_kind      VARCHAR(32)  NOT NULL,
    file_name      VARCHAR(512),
    content_type   VARCHAR(128),
    masked_snippet VARCHAR(1024),
    match_hash     VARCHAR(128) NOT NULL,
    start_offset   INTEGER      NOT NULL DEFAULT 0,
    end_offset     INTEGER      NOT NULL DEFAULT 0,
    severity       INTEGER      NOT NULL DEFAULT 0,
    action         VARCHAR(32)  NOT NULL,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT fk_dlp_scan_evidence_event
        FOREIGN KEY (event_id) REFERENCES dlp_scan_event (id) ON DELETE CASCADE,
    CONSTRAINT fk_dlp_scan_evidence_rule
        FOREIGN KEY (rule_id) REFERENCES dlp_pattern (id) ON DELETE SET NULL,
    CONSTRAINT chk_dlp_scan_evidence_rule_type
        CHECK (rule_type IN ('PATTERN', 'EDM', 'FINGERPRINT', 'REGEX', 'KEYWORD', 'BUILTIN', 'DICTIONARY', 'COMPOSITE')),
    CONSTRAINT chk_dlp_scan_evidence_part_kind
        CHECK (part_kind IN ('SUBJECT', 'HEADERS', 'BODY_TEXT', 'BODY_HTML', 'ATTACHMENT_TEXT', 'ATTACHMENT_PDF', 'ATTACHMENT_ZIP_ENTRY', 'ATTACHMENT_METADATA')),
    CONSTRAINT chk_dlp_scan_evidence_action
        CHECK (action IN ('WARN', 'MUST_ENCRYPT', 'QUARANTINE', 'BLOCK')),
    CONSTRAINT chk_dlp_scan_evidence_offsets
        CHECK (start_offset >= 0 AND end_offset >= start_offset),
    CONSTRAINT chk_dlp_scan_evidence_severity
        CHECK (severity >= 0)
);

CREATE INDEX idx_dlp_scan_evidence_event ON dlp_scan_evidence (event_id);
CREATE INDEX idx_dlp_scan_evidence_rule ON dlp_scan_evidence (rule_id, rule_name);
CREATE INDEX idx_dlp_scan_evidence_hash ON dlp_scan_evidence (match_hash);

CREATE TABLE mail_auth_policy (
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

CREATE TABLE mail_auth_domain_policy (
    id                     VARCHAR(128) PRIMARY KEY,
    domain_name            VARCHAR(253) NOT NULL,
    enabled                BOOLEAN      NOT NULL DEFAULT TRUE,
    dkim_signing_enabled   BOOLEAN      NOT NULL DEFAULT FALSE,
    dkim_selector          VARCHAR(128) NOT NULL DEFAULT 'sealmail',
    dkim_key_secret_ref    VARCHAR(1024),
    dkim_key_path          VARCHAR(1024),
    dkim_signed_headers    TEXT         NOT NULL DEFAULT '[]',
    spf_publish_enabled    BOOLEAN      NOT NULL DEFAULT TRUE,
    spf_use_a              BOOLEAN      NOT NULL DEFAULT TRUE,
    spf_use_mx             BOOLEAN      NOT NULL DEFAULT TRUE,
    spf_ip4                TEXT         NOT NULL DEFAULT '[]',
    spf_ip6                TEXT         NOT NULL DEFAULT '[]',
    spf_includes           TEXT         NOT NULL DEFAULT '[]',
    spf_all_policy         VARCHAR(8)   NOT NULL DEFAULT '~all',
    dmarc_publish_enabled  BOOLEAN      NOT NULL DEFAULT TRUE,
    dmarc_policy           VARCHAR(16)  NOT NULL DEFAULT 'none',
    dmarc_subdomain_policy VARCHAR(16)  NOT NULL DEFAULT 'none',
    dmarc_adkim            VARCHAR(1)   NOT NULL DEFAULT 'r',
    dmarc_aspf             VARCHAR(1)   NOT NULL DEFAULT 'r',
    dmarc_pct              INTEGER      NOT NULL DEFAULT 100,
    dmarc_rua              VARCHAR(2048),
    dmarc_ruf              VARCHAR(2048),
    created_at             TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version                BIGINT       NOT NULL DEFAULT 0,
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

CREATE INDEX idx_mail_auth_domain_policy_domain
    ON mail_auth_domain_policy (domain_name);

CREATE TABLE mail_auth_dns_probe (
    id                  VARCHAR(128) PRIMARY KEY,
    domain_name         VARCHAR(253) NOT NULL,
    record_type         VARCHAR(32)  NOT NULL,
    expected_name       VARCHAR(512) NOT NULL,
    expected_value_hash VARCHAR(128),
    observed_value      TEXT,
    status              VARCHAR(32)  NOT NULL,
    detail              VARCHAR(1024),
    checked_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT chk_mail_auth_dns_probe_status
        CHECK (status IN ('MATCH', 'MISMATCH', 'NOT_FOUND', 'TEMPERROR', 'ERROR'))
);

CREATE INDEX idx_mail_auth_dns_probe_domain_checked
    ON mail_auth_dns_probe (domain_name, checked_at DESC);
CREATE INDEX idx_mail_auth_dns_probe_type_checked
    ON mail_auth_dns_probe (record_type, checked_at DESC);

CREATE TABLE relay_policy (
    id                                                 VARCHAR(64)  PRIMARY KEY,
    enabled                                            BOOLEAN      NOT NULL DEFAULT FALSE,
    host                                               VARCHAR(254) NOT NULL DEFAULT 'localhost',
    port                                               INTEGER      NOT NULL DEFAULT 25,
    username                                           VARCHAR(254),
    password_secret_ref                                VARCHAR(1024),
    timeout_ms                                         INTEGER      NOT NULL DEFAULT 30000,
    envelope_from                                      VARCHAR(254),
    allow_unconfigured_external_recipient_domains      BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at                                         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at                                         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version                                            BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT chk_relay_policy_port
        CHECK (port BETWEEN 1 AND 65535),
    CONSTRAINT chk_relay_policy_timeout
        CHECK (timeout_ms > 0)
);

CREATE TABLE quarantine_policy (
    id                          VARCHAR(64) PRIMARY KEY,
    max_retention_days          INTEGER     NOT NULL DEFAULT 30,
    notification_enabled        BOOLEAN     NOT NULL DEFAULT FALSE,
    release_requires_encryption BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    version                     BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT chk_quarantine_policy_retention
        CHECK (max_retention_days > 0)
);

CREATE TABLE gm_edge_policy (
    id                                  VARCHAR(64)   PRIMARY KEY,
    enabled                             BOOLEAN       NOT NULL DEFAULT FALSE,
    inbound_enabled                     BOOLEAN       NOT NULL DEFAULT TRUE,
    inbound_bind_address                VARCHAR(128)  NOT NULL DEFAULT '0.0.0.0',
    inbound_starttls_port               INTEGER       NOT NULL DEFAULT 2525,
    inbound_implicit_tls_port           INTEGER       NOT NULL DEFAULT 2465,
    inbound_backlog                     INTEGER       NOT NULL DEFAULT 128,
    inbound_max_connections             INTEGER       NOT NULL DEFAULT 1024,
    outbound_enabled                    BOOLEAN       NOT NULL DEFAULT TRUE,
    outbound_bind_address               VARCHAR(128)  NOT NULL DEFAULT '127.0.0.1',
    outbound_smart_host_port            INTEGER       NOT NULL DEFAULT 2526,
    outbound_backlog                    INTEGER       NOT NULL DEFAULT 128,
    outbound_max_connections            INTEGER       NOT NULL DEFAULT 512,
    postfix_host                        VARCHAR(254)  NOT NULL DEFAULT '127.0.0.1',
    postfix_port                        INTEGER       NOT NULL DEFAULT 2530,
    tls_protocols                       VARCHAR(512)  NOT NULL DEFAULT 'TLCPv1.1,TLCP,TLSv1.3',
    tls_cipher_suites                   VARCHAR(2048) NOT NULL DEFAULT 'TLS_SM4_GCM_SM3,TLS_SM4_CCM_SM3',
    tls_key_store_path                  VARCHAR(1024),
    tls_key_store_password_secret_ref   VARCHAR(1024),
    tls_key_store_type                  VARCHAR(32)   NOT NULL DEFAULT 'PKCS12',
    tls_trust_store_path                VARCHAR(1024),
    tls_trust_store_password_secret_ref VARCHAR(1024),
    tls_trust_store_type                VARCHAR(32)   NOT NULL DEFAULT 'PKCS12',
    tls_trust_all                       BOOLEAN       NOT NULL DEFAULT FALSE,
    connect_timeout_ms                  INTEGER       NOT NULL DEFAULT 10000,
    read_timeout_ms                     INTEGER       NOT NULL DEFAULT 60000,
    max_message_size_bytes              INTEGER       NOT NULL DEFAULT 52428800,
    max_line_length_bytes               INTEGER       NOT NULL DEFAULT 16384,
    max_recipients                      INTEGER       NOT NULL DEFAULT 100,
    routes_json                         TEXT          NOT NULL DEFAULT '[]',
    created_at                          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at                          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    version                             BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT chk_gm_edge_inbound_starttls_port
        CHECK (inbound_starttls_port BETWEEN 1 AND 65535),
    CONSTRAINT chk_gm_edge_inbound_implicit_tls_port
        CHECK (inbound_implicit_tls_port BETWEEN 1 AND 65535),
    CONSTRAINT chk_gm_edge_outbound_smart_host_port
        CHECK (outbound_smart_host_port BETWEEN 1 AND 65535),
    CONSTRAINT chk_gm_edge_postfix_port
        CHECK (postfix_port BETWEEN 1 AND 65535),
    CONSTRAINT chk_gm_edge_inbound_listener_ports
        CHECK (inbound_starttls_port <> inbound_implicit_tls_port),
    CONSTRAINT chk_gm_edge_inbound_backlog
        CHECK (inbound_backlog > 0),
    CONSTRAINT chk_gm_edge_outbound_backlog
        CHECK (outbound_backlog > 0),
    CONSTRAINT chk_gm_edge_inbound_max_connections
        CHECK (inbound_max_connections > 0),
    CONSTRAINT chk_gm_edge_outbound_max_connections
        CHECK (outbound_max_connections > 0),
    CONSTRAINT chk_gm_edge_connect_timeout
        CHECK (connect_timeout_ms > 0),
    CONSTRAINT chk_gm_edge_read_timeout
        CHECK (read_timeout_ms > 0),
    CONSTRAINT chk_gm_edge_message_size
        CHECK (max_message_size_bytes > 0),
    CONSTRAINT chk_gm_edge_line_length
        CHECK (max_line_length_bytes > 0),
    CONSTRAINT chk_gm_edge_max_recipients
        CHECK (max_recipients > 0)
);

CREATE TABLE smime_suite_policy (
    id                     VARCHAR(64)  PRIMARY KEY,
    default_standard_suite VARCHAR(128) NOT NULL DEFAULT 'STANDARD_AES_256_CBC',
    default_gm_suite       VARCHAR(128) NOT NULL DEFAULT 'GM_SM4_CBC',
    created_at             TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version                BIGINT       NOT NULL DEFAULT 0
);

CREATE TABLE dlp_quarantine_mail (
    id                     VARCHAR(128)  PRIMARY KEY,
    message_id             VARCHAR(254)  NOT NULL,
    subject                VARCHAR(1024),
    sender_email           VARCHAR(254)  NOT NULL,
    recipients             VARCHAR(2048) NOT NULL,
    direction              VARCHAR(16),
    remote_address         VARCHAR(128),
    reason                 VARCHAR(64)   NOT NULL,
    detail                 VARCHAR(1024),
    raw_content_id         VARCHAR(128),
    status                 VARCHAR(32)   NOT NULL DEFAULT 'QUARANTINED',
    created_at             TIMESTAMPTZ   NOT NULL DEFAULT now(),
    resolved_at            TIMESTAMPTZ,
    processed_by           VARCHAR(254),
    process_comment        VARCHAR(1024),
    dlp_event_id           VARCHAR(128),
    false_positive         BOOLEAN       NOT NULL DEFAULT FALSE,
    false_positive_at      TIMESTAMPTZ,
    false_positive_by      VARCHAR(254),
    false_positive_comment VARCHAR(1024),
    version                BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT fk_dlp_quarantine_raw_content
        FOREIGN KEY (raw_content_id) REFERENCES mail_raw_content (id) ON DELETE SET NULL,
    CONSTRAINT fk_dlp_quarantine_event
        FOREIGN KEY (dlp_event_id) REFERENCES dlp_scan_event (id) ON DELETE SET NULL,
    CONSTRAINT chk_dlp_quarantine_direction
        CHECK (direction IS NULL OR direction IN ('INBOUND', 'OUTBOUND')),
    CONSTRAINT chk_dlp_quarantine_reason
        CHECK (reason IN ('CERTIFICATE_MISSING', 'CERTIFICATE_REVOKED', 'ENCRYPTION_FAILED', 'DECRYPTION_FAILED', 'SIGNATURE_INVALID', 'EMAIL_AUTH_FAILED', 'DOMAIN_NOT_CONFIGURED', 'POLICY_VIOLATION', 'SCAN_ERROR')),
    CONSTRAINT chk_dlp_quarantine_status
        CHECK (status IN ('QUARANTINED', 'RELEASING', 'RELEASED', 'REJECTED'))
);

CREATE INDEX idx_dlp_quarantine_status ON dlp_quarantine_mail (status);
CREATE INDEX idx_dlp_quarantine_message ON dlp_quarantine_mail (message_id);
CREATE INDEX idx_dlp_quarantine_event ON dlp_quarantine_mail (dlp_event_id);
CREATE INDEX idx_dlp_quarantine_raw_content ON dlp_quarantine_mail (raw_content_id);

ALTER TABLE dlp_scan_event
    ADD CONSTRAINT fk_dlp_scan_event_quarantine
        FOREIGN KEY (quarantine_id) REFERENCES dlp_quarantine_mail (id) ON DELETE SET NULL;

CREATE TABLE exception_mail (
    id             VARCHAR(128)  PRIMARY KEY,
    message_id     VARCHAR(254)  NOT NULL,
    subject        VARCHAR(1024),
    sender_email   VARCHAR(254)  NOT NULL,
    recipients     VARCHAR(2048) NOT NULL,
    direction      VARCHAR(16),
    remote_address VARCHAR(128),
    reason         VARCHAR(64)   NOT NULL,
    detail         VARCHAR(1024),
    blocked_by     VARCHAR(254)  NOT NULL DEFAULT 'system',
    block_comment  VARCHAR(1024),
    raw_content_id VARCHAR(128),
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    version        BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT fk_exception_mail_raw_content
        FOREIGN KEY (raw_content_id) REFERENCES mail_raw_content (id) ON DELETE SET NULL,
    CONSTRAINT chk_exception_mail_direction
        CHECK (direction IS NULL OR direction IN ('INBOUND', 'OUTBOUND')),
    CONSTRAINT chk_exception_mail_reason
        CHECK (reason IN ('CERTIFICATE_MISSING', 'CERTIFICATE_REVOKED', 'ENCRYPTION_FAILED', 'DECRYPTION_FAILED', 'SIGNATURE_INVALID', 'EMAIL_AUTH_FAILED', 'DOMAIN_NOT_CONFIGURED', 'POLICY_VIOLATION', 'SCAN_ERROR'))
);

CREATE INDEX idx_exception_mail_message ON exception_mail (message_id);
CREATE INDEX idx_exception_mail_reason ON exception_mail (reason);
CREATE INDEX idx_exception_mail_created ON exception_mail (created_at DESC);
CREATE INDEX idx_exception_mail_raw_content ON exception_mail (raw_content_id);

CREATE TABLE dlp_edm_dataset (
    id          VARCHAR(128) PRIMARY KEY,
    name        VARCHAR(128) NOT NULL,
    description VARCHAR(512),
    enabled     BOOLEAN      NOT NULL DEFAULT TRUE,
    value_count BIGINT       NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version     BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT chk_dlp_edm_dataset_value_count
        CHECK (value_count >= 0)
);

CREATE TABLE dlp_edm_value (
    id         VARCHAR(128) PRIMARY KEY,
    dataset_id VARCHAR(128) NOT NULL,
    value_hash VARCHAR(64)  NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT fk_dlp_edm_value_dataset
        FOREIGN KEY (dataset_id) REFERENCES dlp_edm_dataset (id) ON DELETE CASCADE,
    CONSTRAINT chk_dlp_edm_value_hash
        CHECK (value_hash ~ '^[0-9a-f]{64}$')
);

CREATE UNIQUE INDEX idx_dlp_edm_value_unique
    ON dlp_edm_value (dataset_id, value_hash);
CREATE INDEX idx_dlp_edm_value_hash
    ON dlp_edm_value (value_hash);

CREATE TABLE dlp_fingerprint_library (
    id             VARCHAR(128) PRIMARY KEY,
    name           VARCHAR(128) NOT NULL,
    description    VARCHAR(512),
    enabled        BOOLEAN      NOT NULL DEFAULT TRUE,
    document_count BIGINT       NOT NULL DEFAULT 0,
    chunk_count    BIGINT       NOT NULL DEFAULT 0,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version        BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT chk_dlp_fingerprint_document_count
        CHECK (document_count >= 0),
    CONSTRAINT chk_dlp_fingerprint_chunk_count
        CHECK (chunk_count >= 0)
);

CREATE TABLE dlp_fingerprint_chunk (
    id            VARCHAR(128) PRIMARY KEY,
    library_id    VARCHAR(128) NOT NULL,
    document_id   VARCHAR(128) NOT NULL,
    document_name VARCHAR(255),
    chunk_hash    VARCHAR(64)  NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT fk_dlp_fingerprint_chunk_library
        FOREIGN KEY (library_id) REFERENCES dlp_fingerprint_library (id) ON DELETE CASCADE,
    CONSTRAINT chk_dlp_fingerprint_chunk_hash
        CHECK (chunk_hash ~ '^[0-9a-f]{64}$')
);

CREATE UNIQUE INDEX idx_dlp_fingerprint_chunk_unique
    ON dlp_fingerprint_chunk (library_id, document_id, chunk_hash);
CREATE INDEX idx_dlp_fingerprint_chunk_hash
    ON dlp_fingerprint_chunk (chunk_hash);

CREATE TABLE dlp_uba_sender_baseline (
    sender_email         VARCHAR(254) PRIMARY KEY,
    total_messages       BIGINT       NOT NULL DEFAULT 0,
    outbound_messages    BIGINT       NOT NULL DEFAULT 0,
    external_domains     TEXT,
    active_hours         TEXT,
    max_attachment_bytes BIGINT       NOT NULL DEFAULT 0,
    dlp_hit_count        BIGINT       NOT NULL DEFAULT 0,
    high_risk_count      BIGINT       NOT NULL DEFAULT 0,
    last_risk_level      VARCHAR(16)  NOT NULL DEFAULT 'LOW',
    last_risk_reasons    TEXT,
    first_seen_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    last_seen_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT chk_dlp_uba_total_messages
        CHECK (total_messages >= 0),
    CONSTRAINT chk_dlp_uba_outbound_messages
        CHECK (outbound_messages >= 0),
    CONSTRAINT chk_dlp_uba_attachment_bytes
        CHECK (max_attachment_bytes >= 0),
    CONSTRAINT chk_dlp_uba_hit_count
        CHECK (dlp_hit_count >= 0),
    CONSTRAINT chk_dlp_uba_high_risk_count
        CHECK (high_risk_count >= 0),
    CONSTRAINT chk_dlp_uba_risk_level
        CHECK (last_risk_level IN ('LOW', 'MEDIUM', 'HIGH'))
);
