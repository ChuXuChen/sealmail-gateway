ALTER TABLE relay_policy
    ADD COLUMN IF NOT EXISTS transport_security VARCHAR(16);

UPDATE relay_policy
SET transport_security = CASE
    WHEN use_tls IS TRUE AND port = 465 THEN 'SMTPS'
    WHEN use_tls IS TRUE THEN 'STARTTLS'
    ELSE 'NONE'
END
WHERE transport_security IS NULL;

ALTER TABLE relay_policy
    ALTER COLUMN transport_security SET DEFAULT 'NONE',
    ALTER COLUMN transport_security SET NOT NULL;

ALTER TABLE relay_policy
    ADD CONSTRAINT chk_relay_policy_transport_security
        CHECK (transport_security IN ('NONE', 'STARTTLS', 'SMTPS'));
