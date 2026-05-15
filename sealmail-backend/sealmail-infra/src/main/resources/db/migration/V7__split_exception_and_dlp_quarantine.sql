DO $$
DECLARE
    schema_name text := current_schema();
BEGIN
    IF to_regclass(schema_name || '.quarantined_mail') IS NOT NULL
       AND to_regclass(schema_name || '.dlp_quarantine_mail') IS NULL THEN
        EXECUTE format('ALTER TABLE %I.quarantined_mail RENAME TO dlp_quarantine_mail', schema_name);
    END IF;
END $$;

ALTER TABLE dlp_quarantine_mail
    ALTER COLUMN status SET DEFAULT 'QUARANTINED';

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_name = 'dlp_quarantine_mail'
          AND column_name = 'raw_content'
    ) THEN
        ALTER TABLE dlp_quarantine_mail
            ADD COLUMN raw_content TEXT;
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_name = 'dlp_quarantine_mail'
          AND column_name = 'direction'
    ) THEN
        ALTER TABLE dlp_quarantine_mail
            ADD COLUMN direction VARCHAR(16);
    END IF;
END $$;

CREATE TABLE IF NOT EXISTS exception_mail (
    id               VARCHAR(128)  NOT NULL,
    message_id       VARCHAR(254)  NOT NULL,
    subject          VARCHAR(1024),
    sender_email     VARCHAR(254)  NOT NULL,
    recipients       VARCHAR(2048) NOT NULL,
    direction        VARCHAR(16),
    remote_address   VARCHAR(128),
    reason           VARCHAR(64)   NOT NULL,
    detail           VARCHAR(1024),
    blocked_by       VARCHAR(254)  NOT NULL DEFAULT 'system',
    block_comment    VARCHAR(1024),
    created_at       TIMESTAMP     NOT NULL DEFAULT NOW(),
    version          BIGINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

ALTER TABLE exception_mail
    ADD COLUMN IF NOT EXISTS direction VARCHAR(16),
    ADD COLUMN IF NOT EXISTS remote_address VARCHAR(128);

INSERT INTO exception_mail (
    id,
    message_id,
    subject,
    sender_email,
    recipients,
    direction,
    remote_address,
    reason,
    detail,
    blocked_by,
    block_comment,
    created_at,
    version
)
SELECT
    id,
    message_id,
    subject,
    sender_email,
    recipients,
    direction,
    remote_address,
    reason,
    detail,
    COALESCE(NULLIF(processed_by, ''), 'system'),
    COALESCE(process_comment, 'Exception mail blocked automatically'),
    created_at,
    version
FROM dlp_quarantine_mail
WHERE NOT (reason = 'POLICY_VIOLATION' AND detail LIKE 'DLP QUARANTINE%')
ON CONFLICT (id) DO NOTHING;

DELETE FROM dlp_quarantine_mail
WHERE NOT (reason = 'POLICY_VIOLATION' AND detail LIKE 'DLP QUARANTINE%');

ALTER TABLE dlp_quarantine_mail
    DROP COLUMN IF EXISTS mail_kind;

CREATE INDEX IF NOT EXISTS idx_dlp_quarantine_status ON dlp_quarantine_mail (status);
CREATE INDEX IF NOT EXISTS idx_dlp_quarantine_message ON dlp_quarantine_mail (message_id);
CREATE INDEX IF NOT EXISTS idx_exception_mail_message ON exception_mail (message_id);
CREATE INDEX IF NOT EXISTS idx_exception_mail_reason ON exception_mail (reason);
CREATE INDEX IF NOT EXISTS idx_exception_mail_created ON exception_mail (created_at DESC);
