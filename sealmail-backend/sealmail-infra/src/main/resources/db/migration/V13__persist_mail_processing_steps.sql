CREATE TABLE IF NOT EXISTS mail_processing_step (
    id                 VARCHAR(128)  PRIMARY KEY,
    mail_processing_id VARCHAR(128)  NOT NULL,
    step_name          VARCHAR(128)  NOT NULL,
    completed          BOOLEAN       NOT NULL DEFAULT FALSE,
    success            BOOLEAN       NOT NULL DEFAULT FALSE,
    error_message      VARCHAR(1024),
    started_at         TIMESTAMP     NOT NULL DEFAULT NOW(),
    completed_at       TIMESTAMP,
    CONSTRAINT fk_mail_processing_step_processing
        FOREIGN KEY (mail_processing_id) REFERENCES mail_processing (id)
        ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_mail_processing_step_processing
    ON mail_processing_step (mail_processing_id, started_at);
