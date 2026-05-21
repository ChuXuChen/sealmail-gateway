-- Reset SealMail application data and create a SUPER_ADMIN account.
--
-- Intended for local/dev PostgreSQL databases only.
-- This keeps Flyway metadata so existing migrations are not re-run.

BEGIN;

DO $$
DECLARE
    table_list text;
BEGIN
    SELECT string_agg(format('%I.%I', schemaname, tablename), ', ')
    INTO table_list
    FROM pg_tables
    WHERE schemaname = current_schema()
      AND tablename <> 'flyway_schema_history';

    IF table_list IS NOT NULL THEN
        EXECUTE 'TRUNCATE TABLE ' || table_list || ' RESTART IDENTITY CASCADE';
    END IF;
END $$;

INSERT INTO user_account (
    id,
    username,
    email,
    password_hash,
    active,
    locked,
    failed_login_attempts,
    last_password_changed_at,
    token_invalid_before,
    created_at,
    updated_at,
    version
) VALUES (
    'admin',
    'admin',
    'admin@sealmail.local',
    '$2a$12$/FBYnsl2YU/I/Ltg1Y1O3..aowfQdBOEiZsFLh7emVI0zikKL6gwm',
    TRUE,
    FALSE,
    0,
    now(),
    now(),
    now(),
    now(),
    0
);

INSERT INTO user_account_role (user_account_id, role)
VALUES ('admin', 'SUPER_ADMIN');

-- Recreate singleton settings rows with schema defaults so the UI has baseline settings.
INSERT INTO mail_auth_policy (id) VALUES ('default');
INSERT INTO relay_policy (id) VALUES ('default');
INSERT INTO quarantine_policy (id) VALUES ('default');
INSERT INTO gm_edge_policy (id) VALUES ('default');
INSERT INTO smime_suite_policy (id) VALUES ('default');

COMMIT;
