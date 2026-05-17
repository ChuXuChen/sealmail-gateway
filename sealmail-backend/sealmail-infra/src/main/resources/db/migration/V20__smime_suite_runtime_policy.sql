CREATE TABLE IF NOT EXISTS smime_suite_policy (
    id                     VARCHAR(64)  PRIMARY KEY,
    default_standard_suite VARCHAR(128) NOT NULL DEFAULT 'STANDARD_AES_256_CBC',
    default_gm_suite       VARCHAR(128) NOT NULL DEFAULT 'GM_SM4_CBC',
    created_at             TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version                BIGINT       NOT NULL DEFAULT 0
);
