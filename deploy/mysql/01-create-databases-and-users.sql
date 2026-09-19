-- OpsConsole MySQL bootstrap (run as a DBA). Step 1 of a from-scratch install.
-- Replace CHANGE_ME_* passwords and host patterns before executing.
-- Next: 02-opsconsole-schema.sql, 03-transaction-schema.sql, 04-grants.sql, 05-verification.sql.

CREATE DATABASE IF NOT EXISTS opsconsole
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

CREATE DATABASE IF NOT EXISTS bre_underwriting
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

-- Application schema: users, monitors, admin catalog, audit logs.
CREATE USER IF NOT EXISTS 'opsconsole'@'%' IDENTIFIED BY 'CHANGE_ME_OPSCONSOLE_PASSWORD';

-- Reporting schema: EMI transaction analytics (SELECT only).
CREATE USER IF NOT EXISTS 'opsconsole_txn'@'%' IDENTIFIED BY 'CHANGE_ME_TXN_PASSWORD';
