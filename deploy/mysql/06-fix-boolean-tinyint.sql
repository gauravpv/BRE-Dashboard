-- Hibernate ddl-auto: validate expects TINYINT for Java boolean columns.
-- Some MySQL installs / older Hibernate creates store them as BIT instead.
-- Run on bre_dashboard only. Safe to re-run.

USE bre_dashboard;

ALTER TABLE app_roles
    MODIFY COLUMN system_role TINYINT(1) NOT NULL DEFAULT 1;

ALTER TABLE role_tab_access
    MODIFY COLUMN allowed TINYINT(1) NOT NULL DEFAULT 0;

ALTER TABLE monitored_hosts
    MODIFY COLUMN enabled TINYINT(1) NOT NULL DEFAULT 1;

ALTER TABLE monitored_hosts_prod
    MODIFY COLUMN enabled TINYINT(1) NOT NULL DEFAULT 1;

ALTER TABLE managed_servers
    MODIFY COLUMN enabled TINYINT(1) NOT NULL DEFAULT 1;

ALTER TABLE managed_services
    MODIFY COLUMN enabled TINYINT(1) NOT NULL DEFAULT 1;
