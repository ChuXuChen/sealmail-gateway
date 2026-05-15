DO $$
DECLARE
    schema_name text := current_schema();
    table_name text;
BEGIN
    table_name := CASE
        WHEN to_regclass(schema_name || '.quarantined_mail') IS NOT NULL THEN 'quarantined_mail'
        WHEN to_regclass(schema_name || '.dlp_quarantine_mail') IS NOT NULL THEN 'dlp_quarantine_mail'
        ELSE NULL
    END;

    IF table_name IS NOT NULL THEN
        EXECUTE format('ALTER TABLE %I.%I ADD COLUMN IF NOT EXISTS raw_content TEXT', schema_name, table_name);
        EXECUTE format('ALTER TABLE %I.%I ADD COLUMN IF NOT EXISTS direction VARCHAR(16)', schema_name, table_name);
    END IF;
END $$;
