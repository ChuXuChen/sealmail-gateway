-- Runtime DKIM key material is represented by path or secret reference only.
-- Historical migrations may create this legacy plaintext column on a fresh database;
-- drop it forward here without modifying published migrations.
ALTER TABLE mail_auth_config
    DROP COLUMN IF EXISTS dkim_private_key_pem;
