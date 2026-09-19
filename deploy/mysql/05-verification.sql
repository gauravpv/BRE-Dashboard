-- Sanity checks after schema + grants.

SELECT table_name
FROM information_schema.tables
WHERE table_schema = 'opsconsole'
ORDER BY table_name;

SELECT column_name, column_type, is_nullable, column_default
FROM information_schema.columns
WHERE table_schema = 'opsconsole'
  AND table_name = 'app_users'
  AND column_name IN ('account_status', 'azure_ad_id', 'password_hash', 'role_id')
ORDER BY column_name;

SELECT code, name, system_role FROM opsconsole.app_roles ORDER BY code;

SELECT r.code, a.tab, a.allowed
FROM opsconsole.role_tab_access a
JOIN opsconsole.app_roles r ON r.id = a.role_id
ORDER BY r.code, a.tab;

SELECT table_name, table_type
FROM information_schema.tables
WHERE table_schema = 'bre_underwriting'
  AND table_name IN (
        'transaction_details',
        'transaction_details_srcreq_otp',
        'festival_summary_detailed_mins_new',
        'festival_summary_detailed_new'
      )
ORDER BY table_name;

SHOW GRANTS FOR 'opsconsole'@'%';
SHOW GRANTS FOR 'opsconsole_txn'@'%';
