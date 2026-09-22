-- Sanity checks after BRE Dashboard schema + grants.
-- Does not query the transaction reporting database.

SELECT table_name
FROM information_schema.tables
WHERE table_schema = 'bre_dashboard'
ORDER BY table_name;

SELECT column_name, column_type, is_nullable, column_default
FROM information_schema.columns
WHERE table_schema = 'bre_dashboard'
  AND table_name = 'app_users'
  AND column_name IN ('account_status', 'azure_ad_id', 'password_hash', 'role_id')
ORDER BY column_name;

SELECT code, name, system_role FROM bre_dashboard.app_roles ORDER BY code;

SELECT r.code, a.tab, a.allowed
FROM bre_dashboard.role_tab_access a
JOIN bre_dashboard.app_roles r ON r.id = a.role_id
ORDER BY r.code, a.tab;

SHOW GRANTS FOR 'bre_dashboard'@'%';
