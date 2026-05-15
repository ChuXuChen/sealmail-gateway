ALTER TABLE user_account
    ADD COLUMN IF NOT EXISTS username VARCHAR(64);

ALTER TABLE user_account
    ADD COLUMN IF NOT EXISTS password_hash VARCHAR(256);

ALTER TABLE user_account
    ADD COLUMN IF NOT EXISTS roles VARCHAR(1024);

ALTER TABLE user_account
    ADD COLUMN IF NOT EXISTS managed_domains VARCHAR(2048);

ALTER TABLE user_account
    ADD COLUMN IF NOT EXISTS locked BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE user_account
    ADD COLUMN IF NOT EXISTS failed_login_attempts INTEGER NOT NULL DEFAULT 0;

ALTER TABLE user_account
    ADD COLUMN IF NOT EXISTS lockout_expires_at TIMESTAMP;

ALTER TABLE user_account
    ADD COLUMN IF NOT EXISTS last_password_changed_at TIMESTAMP;

ALTER TABLE user_account
    ADD COLUMN IF NOT EXISTS last_login_at TIMESTAMP;

ALTER TABLE user_account
    ADD COLUMN IF NOT EXISTS last_login_ip VARCHAR(45);

UPDATE user_account
SET username = COALESCE(username, split_part(email, '@', 1))
WHERE username IS NULL;

UPDATE user_account
SET password_hash = COALESCE(password_hash, '{noop}disabled')
WHERE password_hash IS NULL;

UPDATE user_account
SET roles = COALESCE(roles, 'AUDITOR')
WHERE roles IS NULL;

ALTER TABLE user_account
    ALTER COLUMN username SET NOT NULL;

ALTER TABLE user_account
    ALTER COLUMN password_hash SET NOT NULL;

ALTER TABLE user_account
    ALTER COLUMN roles SET NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS idx_user_username ON user_account (username);
