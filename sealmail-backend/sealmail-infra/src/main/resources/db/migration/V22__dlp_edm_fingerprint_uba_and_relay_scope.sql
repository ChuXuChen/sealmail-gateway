ALTER TABLE relay_policy
    ADD COLUMN IF NOT EXISTS allow_unconfigured_external_recipient_domains BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE IF NOT EXISTS dlp_edm_dataset (
    id            VARCHAR(128) NOT NULL,
    name          VARCHAR(128) NOT NULL,
    description   VARCHAR(512),
    enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
    value_count   BIGINT       NOT NULL DEFAULT 0,
    created_at    TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMP    NOT NULL DEFAULT NOW(),
    version       BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS dlp_edm_value (
    id          VARCHAR(128) NOT NULL,
    dataset_id  VARCHAR(128) NOT NULL,
    value_hash  VARCHAR(64)  NOT NULL,
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    PRIMARY KEY (id),
    CONSTRAINT fk_dlp_edm_value_dataset
        FOREIGN KEY (dataset_id) REFERENCES dlp_edm_dataset(id) ON DELETE CASCADE
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_dlp_edm_value_unique
    ON dlp_edm_value (dataset_id, value_hash);
CREATE INDEX IF NOT EXISTS idx_dlp_edm_value_hash
    ON dlp_edm_value (value_hash);

CREATE TABLE IF NOT EXISTS dlp_fingerprint_library (
    id             VARCHAR(128) NOT NULL,
    name           VARCHAR(128) NOT NULL,
    description    VARCHAR(512),
    enabled        BOOLEAN      NOT NULL DEFAULT TRUE,
    document_count BIGINT       NOT NULL DEFAULT 0,
    chunk_count    BIGINT       NOT NULL DEFAULT 0,
    created_at     TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMP    NOT NULL DEFAULT NOW(),
    version        BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS dlp_fingerprint_chunk (
    id            VARCHAR(128) NOT NULL,
    library_id    VARCHAR(128) NOT NULL,
    document_id   VARCHAR(128) NOT NULL,
    document_name VARCHAR(255),
    chunk_hash    VARCHAR(64)  NOT NULL,
    created_at    TIMESTAMP    NOT NULL DEFAULT NOW(),
    PRIMARY KEY (id),
    CONSTRAINT fk_dlp_fingerprint_chunk_library
        FOREIGN KEY (library_id) REFERENCES dlp_fingerprint_library(id) ON DELETE CASCADE
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_dlp_fingerprint_chunk_unique
    ON dlp_fingerprint_chunk (library_id, document_id, chunk_hash);
CREATE INDEX IF NOT EXISTS idx_dlp_fingerprint_chunk_hash
    ON dlp_fingerprint_chunk (chunk_hash);

CREATE TABLE IF NOT EXISTS dlp_uba_sender_baseline (
    sender_email         VARCHAR(254) NOT NULL,
    total_messages       BIGINT       NOT NULL DEFAULT 0,
    outbound_messages    BIGINT       NOT NULL DEFAULT 0,
    external_domains     TEXT,
    active_hours         TEXT,
    max_attachment_bytes BIGINT       NOT NULL DEFAULT 0,
    dlp_hit_count        BIGINT       NOT NULL DEFAULT 0,
    high_risk_count      BIGINT       NOT NULL DEFAULT 0,
    last_risk_level      VARCHAR(16)  NOT NULL DEFAULT 'LOW',
    last_risk_reasons    TEXT,
    first_seen_at        TIMESTAMP    NOT NULL DEFAULT NOW(),
    last_seen_at         TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMP    NOT NULL DEFAULT NOW(),
    PRIMARY KEY (sender_email)
);

ALTER TABLE dlp_scan_event
    ADD COLUMN IF NOT EXISTS uba_risk_level VARCHAR(16) NOT NULL DEFAULT 'LOW',
    ADD COLUMN IF NOT EXISTS uba_risk_reasons TEXT,
    ADD COLUMN IF NOT EXISTS uba_action_upgraded BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX IF NOT EXISTS idx_dlp_scan_event_uba_risk
    ON dlp_scan_event (uba_risk_level);
