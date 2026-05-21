-- Create or repair the local SUPER_ADMIN account without clearing application data.
--
-- Intended for local/dev PostgreSQL databases only.
-- Login after running this script:
--   username: admin
--   password: SealMail

BEGIN;

DO $$
DECLARE
    target_id text;
BEGIN
    SELECT id
    INTO target_id
    FROM user_account
    WHERE username = 'admin'
       OR email = 'admin@sealmail.local'
    ORDER BY CASE WHEN username = 'admin' THEN 0 ELSE 1 END
    LIMIT 1;

    IF target_id IS NULL THEN
        target_id := 'admin';
        INSERT INTO user_account (
            id,
            username,
            email,
            password_hash,
            active,
            locked,
            failed_login_attempts,
            lockout_expires_at,
            last_password_changed_at,
            token_invalid_before,
            created_at,
            updated_at,
            version
        ) VALUES (
            target_id,
            'admin',
            'admin@sealmail.local',
            '$2a$12$/FBYnsl2YU/I/Ltg1Y1O3..aowfQdBOEiZsFLh7emVI0zikKL6gwm',
            TRUE,
            FALSE,
            0,
            NULL,
            now(),
            now(),
            now(),
            now(),
            0
        );
    ELSE
        UPDATE user_account
        SET username = 'admin',
            email = 'admin@sealmail.local',
            password_hash = '$2a$12$/FBYnsl2YU/I/Ltg1Y1O3..aowfQdBOEiZsFLh7emVI0zikKL6gwm',
            active = TRUE,
            locked = FALSE,
            failed_login_attempts = 0,
            lockout_expires_at = NULL,
            last_password_changed_at = now(),
            token_invalid_before = now(),
            updated_at = now()
        WHERE id = target_id;
    END IF;

    INSERT INTO user_account_role (user_account_id, role)
    VALUES (target_id, 'SUPER_ADMIN')
    ON CONFLICT (user_account_id, role) DO NOTHING;
END $$;

COMMIT;
