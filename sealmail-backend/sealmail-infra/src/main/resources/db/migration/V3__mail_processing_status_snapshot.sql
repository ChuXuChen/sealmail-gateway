ALTER TABLE mail_processing
    ADD COLUMN IF NOT EXISTS status_snapshot TEXT;
