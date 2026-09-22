-- Upgrade an existing BRE Dashboard schema that still uses the legacy `enabled` flag.
-- Safe to skip on a fresh database created by 02-bre-dashboard-schema.sql.
-- Safe to re-run. Run only against the bre_dashboard database, never the transaction reporting DB.
USE bre_dashboard;

SET @has_account_status := (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'app_users'
      AND column_name = 'account_status'
);

SET @sql := IF(
    @has_account_status = 0,
    'ALTER TABLE app_users ADD COLUMN account_status VARCHAR(20) NULL AFTER job_title',
    'SELECT ''account_status already present'' AS info'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_enabled := (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'app_users'
      AND column_name = 'enabled'
);

SET @sql := IF(
    @has_enabled = 1,
    'UPDATE app_users SET account_status = CASE WHEN enabled = 1 THEN ''ACTIVE'' ELSE ''INACTIVE'' END WHERE account_status IS NULL',
    'UPDATE app_users SET account_status = ''ACTIVE'' WHERE account_status IS NULL'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

ALTER TABLE app_users
    MODIFY COLUMN account_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE';
