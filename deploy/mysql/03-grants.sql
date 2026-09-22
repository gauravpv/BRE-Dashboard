-- Least-privilege grants on the BRE Dashboard application database only.
-- Run after 02-bre-dashboard-schema.sql. Do not grant here on the transaction reporting DB.

GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, INDEX
    ON bre_dashboard.* TO 'bre_dashboard'@'%';

FLUSH PRIVILEGES;
