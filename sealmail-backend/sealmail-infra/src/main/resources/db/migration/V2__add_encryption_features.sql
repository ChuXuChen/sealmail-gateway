-- V2: Add encryption management features.
-- Kept idempotent so existing databases can be baselined safely.

-- Add private key support to certificate table
ALTER TABLE certificate
    ADD COLUMN IF NOT EXISTS private_key_data TEXT,
    ADD COLUMN IF NOT EXISTS has_private_key BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS algorithm VARCHAR(32);

-- Add preferred algorithm to domain config
ALTER TABLE domain_config
    ADD COLUMN IF NOT EXISTS preferred_algorithm VARCHAR(32) DEFAULT 'AUTO';

-- Create managed key table
CREATE TABLE IF NOT EXISTS managed_key (
    id                  VARCHAR(128)  NOT NULL,
    alias               VARCHAR(255),
    owner_email         VARCHAR(254)  NOT NULL,
    algorithm           VARCHAR(32)   NOT NULL,
    key_type            VARCHAR(32)   NOT NULL,
    pem_content         TEXT          NOT NULL,
    associated_cert_thumbprint VARCHAR(128),
    created_at          TIMESTAMP     NOT NULL DEFAULT NOW(),
    PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_managed_key_owner ON managed_key (owner_email);
CREATE INDEX IF NOT EXISTS idx_managed_key_cert ON managed_key (associated_cert_thumbprint);
