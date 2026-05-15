ALTER TABLE certificate
    ADD COLUMN IF NOT EXISTS imported_crl_pem TEXT;
