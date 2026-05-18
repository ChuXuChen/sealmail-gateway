ALTER TABLE domain_config
    ADD COLUMN IF NOT EXISTS delivery_transport_profile VARCHAR(64) NOT NULL DEFAULT 'SMTP_CLEAR',
    ADD COLUMN IF NOT EXISTS decryption_mode VARCHAR(64) NOT NULL DEFAULT 'GATEWAY_TERMINATED';

UPDATE domain_config
SET delivery_transport_profile = CASE delivery_port
    WHEN 587 THEN 'SMTP_STARTTLS_STANDARD'
    WHEN 465 THEN 'SMTP_IMPLICIT_TLS_STANDARD'
    WHEN 2525 THEN 'SMTP_STARTTLS_GM'
    WHEN 2465 THEN 'SMTP_IMPLICIT_TLS_GM'
    ELSE 'SMTP_CLEAR'
END
WHERE delivery_transport_profile IS NULL OR delivery_transport_profile = 'SMTP_CLEAR';

ALTER TABLE domain_config
    DROP CONSTRAINT IF EXISTS chk_domain_config_delivery_transport_profile,
    ADD CONSTRAINT chk_domain_config_delivery_transport_profile
        CHECK (delivery_transport_profile IN ('SMTP_CLEAR', 'SMTP_STARTTLS_STANDARD', 'SMTP_IMPLICIT_TLS_STANDARD', 'SMTP_STARTTLS_GM', 'SMTP_IMPLICIT_TLS_GM')),
    DROP CONSTRAINT IF EXISTS chk_domain_config_decryption_mode,
    ADD CONSTRAINT chk_domain_config_decryption_mode
        CHECK (decryption_mode IN ('GATEWAY_TERMINATED', 'END_TO_END_PASSTHROUGH'));

CREATE TABLE IF NOT EXISTS managed_key (
    key_id              VARCHAR(128) PRIMARY KEY,
    owner_email         VARCHAR(254) NOT NULL,
    algorithm           VARCHAR(64)  NOT NULL,
    purpose             VARCHAR(32)  NOT NULL,
    provider            VARCHAR(64)  NOT NULL,
    provider_ref        VARCHAR(1024) NOT NULL,
    certificate_id      VARCHAR(128),
    status              VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE',
    last_used_at        TIMESTAMPTZ,
    rotated_from_key_id VARCHAR(128),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    version             BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT chk_managed_key_purpose
        CHECK (purpose IN ('SMIME', 'CA_SIGNING')),
    CONSTRAINT chk_managed_key_status
        CHECK (status IN ('ACTIVE', 'DISABLED'))
);

CREATE INDEX IF NOT EXISTS idx_managed_key_owner_purpose
    ON managed_key (owner_email, purpose);
CREATE INDEX IF NOT EXISTS idx_managed_key_certificate
    ON managed_key (certificate_id);
CREATE UNIQUE INDEX IF NOT EXISTS idx_managed_key_active_certificate
    ON managed_key (certificate_id)
    WHERE certificate_id IS NOT NULL AND status = 'ACTIVE';

INSERT INTO managed_key (
    key_id,
    owner_email,
    algorithm,
    purpose,
    provider,
    provider_ref,
    certificate_id,
    status,
    created_at,
    updated_at
)
SELECT
    CASE
        WHEN c.private_key_secret_ref LIKE 'keystore:certificate:%'
            THEN substring(c.private_key_secret_ref from length('keystore:certificate:') + 1)
        WHEN c.private_key_secret_ref LIKE 'managed-key:%'
            THEN substring(c.private_key_secret_ref from length('managed-key:') + 1)
        ELSE c.private_key_secret_ref
    END,
    c.owner_email,
    COALESCE(c.algorithm, 'UNKNOWN'),
    CASE WHEN c.is_ca THEN 'CA_SIGNING' ELSE 'SMIME' END,
    'LOCAL_PKCS12',
    CASE
        WHEN c.private_key_secret_ref LIKE 'keystore:certificate:%'
            THEN 'certificate:' || substring(c.private_key_secret_ref from length('keystore:certificate:') + 1)
        ELSE c.private_key_secret_ref
    END,
    c.thumbprint,
    'ACTIVE',
    c.created_at,
    now()
FROM certificate c
WHERE c.private_key_secret_ref IS NOT NULL
  AND btrim(c.private_key_secret_ref) <> ''
  AND c.private_key_secret_ref NOT LIKE 'managed-key:%'
ON CONFLICT (key_id) DO NOTHING;

UPDATE certificate c
SET private_key_secret_ref = 'managed-key:' ||
    CASE
        WHEN c.private_key_secret_ref LIKE 'keystore:certificate:%'
            THEN substring(c.private_key_secret_ref from length('keystore:certificate:') + 1)
        ELSE c.private_key_secret_ref
    END
WHERE c.private_key_secret_ref IS NOT NULL
  AND btrim(c.private_key_secret_ref) <> ''
  AND c.private_key_secret_ref NOT LIKE 'managed-key:%';
