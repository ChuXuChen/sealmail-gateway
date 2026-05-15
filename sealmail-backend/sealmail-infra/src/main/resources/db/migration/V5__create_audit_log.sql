CREATE TABLE IF NOT EXISTS audit_log (
    id                  VARCHAR(128)  NOT NULL,
    type                VARCHAR(64)   NOT NULL,
    user_id             VARCHAR(128),
    username            VARCHAR(64),
    ip_address          VARCHAR(45),
    resource_type       VARCHAR(64),
    resource_id         VARCHAR(128),
    action              VARCHAR(64),
    detail              VARCHAR(2048),
    success             BOOLEAN       NOT NULL DEFAULT TRUE,
    error_message       VARCHAR(1024),
    occurred_at         TIMESTAMP     NOT NULL DEFAULT NOW(),
    PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_audit_type ON audit_log (type);
CREATE INDEX IF NOT EXISTS idx_audit_user_id ON audit_log (user_id);
CREATE INDEX IF NOT EXISTS idx_audit_occurred_at ON audit_log (occurred_at);
CREATE INDEX IF NOT EXISTS idx_audit_resource ON audit_log (resource_type, resource_id);
