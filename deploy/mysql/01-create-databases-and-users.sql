-- BRE Dashboard MySQL bootstrap (run as a DBA). Step 1 of a from-scratch install.
-- This file creates only the BRE Dashboard application database (users/auth, health,
-- admin catalog, activity). It does not create or alter the transaction reporting DB.
-- Replace CHANGE_ME_* passwords and host patterns before executing.
-- Next: 02-bre-dashboard-schema.sql, 03-grants.sql, 04-verification.sql.

CREATE DATABASE IF NOT EXISTS bre_dashboard
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

-- Application schema: login/users, roles, monitors, admin catalog, audit logs.
CREATE USER IF NOT EXISTS 'bre_dashboard'@'%' IDENTIFIED BY 'CHANGE_ME_BRE_DASHBOARD_PASSWORD';
