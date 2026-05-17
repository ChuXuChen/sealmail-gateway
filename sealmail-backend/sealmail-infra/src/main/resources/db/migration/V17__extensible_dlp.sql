ALTER TABLE dlp_pattern
    ADD COLUMN IF NOT EXISTS rule_type VARCHAR(32) NOT NULL DEFAULT 'REGEX',
    ADD COLUMN IF NOT EXISTS builtin_code VARCHAR(128),
    ADD COLUMN IF NOT EXISTS content_kinds TEXT,
    ADD COLUMN IF NOT EXISTS min_match_count INTEGER NOT NULL DEFAULT 1,
    ADD COLUMN IF NOT EXISTS max_evidence_count INTEGER NOT NULL DEFAULT 5,
    ADD COLUMN IF NOT EXISTS masking_strategy VARCHAR(32) NOT NULL DEFAULT 'DEFAULT';

ALTER TABLE dlp_pattern
    ALTER COLUMN regex DROP NOT NULL;

CREATE TABLE IF NOT EXISTS dlp_rule_group (
    id          VARCHAR(128) NOT NULL,
    name        VARCHAR(128) NOT NULL,
    description VARCHAR(512),
    enabled     BOOLEAN      NOT NULL DEFAULT TRUE,
    priority    INTEGER      NOT NULL DEFAULT 100,
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    version     BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS dlp_rule_group_item (
    id            VARCHAR(128) NOT NULL,
    rule_group_id VARCHAR(128) NOT NULL,
    rule_id       VARCHAR(128) NOT NULL,
    position      INTEGER      NOT NULL DEFAULT 0,
    created_at    TIMESTAMP    NOT NULL DEFAULT NOW(),
    PRIMARY KEY (id),
    CONSTRAINT fk_dlp_rule_group_item_group
        FOREIGN KEY (rule_group_id) REFERENCES dlp_rule_group(id) ON DELETE CASCADE,
    CONSTRAINT fk_dlp_rule_group_item_rule
        FOREIGN KEY (rule_id) REFERENCES dlp_pattern(id) ON DELETE CASCADE
);

CREATE UNIQUE INDEX idx_dlp_rule_group_item_unique
    ON dlp_rule_group_item (rule_group_id, rule_id);
CREATE INDEX idx_dlp_rule_group_item_group
    ON dlp_rule_group_item (rule_group_id, position);

CREATE TABLE IF NOT EXISTS dlp_policy (
    id                         VARCHAR(128) NOT NULL,
    name                       VARCHAR(128) NOT NULL,
    description                VARCHAR(512),
    mode                       VARCHAR(32)  NOT NULL DEFAULT 'ENFORCE',
    direction                  VARCHAR(16),
    sender_domains             TEXT,
    recipient_domains          TEXT,
    sender_address_patterns    TEXT,
    recipient_address_patterns TEXT,
    attachment_required        BOOLEAN      NOT NULL DEFAULT FALSE,
    enabled                    BOOLEAN      NOT NULL DEFAULT TRUE,
    priority                   INTEGER      NOT NULL DEFAULT 100,
    created_at                 TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at                 TIMESTAMP    NOT NULL DEFAULT NOW(),
    version                    BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS dlp_policy_rule_group (
    id            VARCHAR(128) NOT NULL,
    policy_id     VARCHAR(128) NOT NULL,
    rule_group_id VARCHAR(128) NOT NULL,
    position      INTEGER      NOT NULL DEFAULT 0,
    created_at    TIMESTAMP    NOT NULL DEFAULT NOW(),
    PRIMARY KEY (id),
    CONSTRAINT fk_dlp_policy_rule_group_policy
        FOREIGN KEY (policy_id) REFERENCES dlp_policy(id) ON DELETE CASCADE,
    CONSTRAINT fk_dlp_policy_rule_group_group
        FOREIGN KEY (rule_group_id) REFERENCES dlp_rule_group(id) ON DELETE CASCADE
);

CREATE UNIQUE INDEX idx_dlp_policy_rule_group_unique
    ON dlp_policy_rule_group (policy_id, rule_group_id);
CREATE INDEX idx_dlp_policy_rule_group_policy
    ON dlp_policy_rule_group (policy_id, position);

CREATE TABLE IF NOT EXISTS dlp_scan_event (
    id                     VARCHAR(128)  NOT NULL,
    message_id             VARCHAR(254),
    processing_id          VARCHAR(128),
    direction              VARCHAR(16),
    sender_email           VARCHAR(254),
    recipients             TEXT,
    subject                VARCHAR(1024),
    remote_address         VARCHAR(128),
    policy_ids             TEXT,
    rule_group_ids         TEXT,
    action                 VARCHAR(32)   NOT NULL,
    max_severity           INTEGER       NOT NULL DEFAULT 0,
    match_count            INTEGER       NOT NULL DEFAULT 0,
    extraction_warnings    TEXT,
    monitor_mode           BOOLEAN       NOT NULL DEFAULT FALSE,
    scan_duration_ms       BIGINT        NOT NULL DEFAULT 0,
    quarantine_id          VARCHAR(128),
    false_positive         BOOLEAN       NOT NULL DEFAULT FALSE,
    false_positive_at      TIMESTAMP,
    false_positive_by      VARCHAR(254),
    false_positive_comment VARCHAR(1024),
    created_at             TIMESTAMP     NOT NULL DEFAULT NOW(),
    PRIMARY KEY (id)
);

CREATE INDEX idx_dlp_scan_event_created ON dlp_scan_event (created_at DESC);
CREATE INDEX idx_dlp_scan_event_action ON dlp_scan_event (action);
CREATE INDEX idx_dlp_scan_event_message ON dlp_scan_event (message_id);
CREATE INDEX idx_dlp_scan_event_quarantine ON dlp_scan_event (quarantine_id);
CREATE INDEX idx_dlp_scan_event_sender ON dlp_scan_event (sender_email);

CREATE TABLE IF NOT EXISTS dlp_scan_evidence (
    id             VARCHAR(128) NOT NULL,
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
    created_at     TIMESTAMP    NOT NULL DEFAULT NOW(),
    PRIMARY KEY (id),
    CONSTRAINT fk_dlp_scan_evidence_event
        FOREIGN KEY (event_id) REFERENCES dlp_scan_event(id) ON DELETE CASCADE
);

CREATE INDEX idx_dlp_scan_evidence_event ON dlp_scan_evidence (event_id);
CREATE INDEX idx_dlp_scan_evidence_rule ON dlp_scan_evidence (rule_id, rule_name);
CREATE INDEX idx_dlp_scan_evidence_hash ON dlp_scan_evidence (match_hash);

ALTER TABLE dlp_quarantine_mail
    ADD COLUMN IF NOT EXISTS dlp_event_id VARCHAR(128),
    ADD COLUMN IF NOT EXISTS false_positive BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS false_positive_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS false_positive_by VARCHAR(254),
    ADD COLUMN IF NOT EXISTS false_positive_comment VARCHAR(1024);

CREATE INDEX idx_dlp_quarantine_event
    ON dlp_quarantine_mail (dlp_event_id);

ALTER TABLE dlp_quarantine_mail
    ADD CONSTRAINT fk_dlp_quarantine_event
        FOREIGN KEY (dlp_event_id) REFERENCES dlp_scan_event(id);
