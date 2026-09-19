-- Upgrade an existing OpsConsole schema from the legacy enabled flag.
-- Run once before deploying the user-approval build.
USE opsconsole;

ALTER TABLE app_users
    ADD COLUMN account_status VARCHAR(20) NULL AFTER job_title;

UPDATE app_users
SET account_status = CASE
    WHEN enabled = 1 THEN 'ACTIVE'
    ELSE 'INACTIVE'
END
WHERE account_status IS NULL;

ALTER TABLE app_users
    MODIFY COLUMN account_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE';
