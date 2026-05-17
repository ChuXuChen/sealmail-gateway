ALTER TABLE domain_config
    ADD COLUMN IF NOT EXISTS delivery_host VARCHAR(255);

ALTER TABLE domain_config
    ADD COLUMN IF NOT EXISTS delivery_port INTEGER;
