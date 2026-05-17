ALTER TABLE relay_policy
    DROP CONSTRAINT IF EXISTS chk_relay_policy_transport_security;

ALTER TABLE relay_policy
    DROP COLUMN IF EXISTS transport_security,
    DROP COLUMN IF EXISTS use_tls;
