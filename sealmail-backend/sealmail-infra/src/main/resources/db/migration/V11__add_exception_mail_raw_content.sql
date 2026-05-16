ALTER TABLE exception_mail
    ADD COLUMN IF NOT EXISTS raw_content TEXT;
