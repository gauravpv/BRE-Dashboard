-- BRE Dashboard application schema. Run against the bre_dashboard database only.
-- From scratch (after 01): all JPA tables, account_status, system roles, default tab access.
-- Login/users live here. Do not run this against the transaction reporting database.
-- Re-runnable: CREATE TABLE IF NOT EXISTS, INSERT IGNORE / NOT EXISTS for seeds.
-- Does not insert users. First Administrator comes from BRE_DASHBOARD_BOOTSTRAP_* on app start.
USE bre_dashboard;

CREATE TABLE IF NOT EXISTS app_roles (
    id            BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    code          VARCHAR(50)  NOT NULL,
    name          VARCHAR(100) NOT NULL,
    description   VARCHAR(500),
    system_role   TINYINT(1)   NOT NULL DEFAULT 1,
    UNIQUE KEY uk_app_roles_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS app_users (
    id              BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    azure_ad_id     VARCHAR(64)   NOT NULL,
    email           VARCHAR(320)  NOT NULL,
    display_name    VARCHAR(200)  NOT NULL,
    job_title       VARCHAR(200),
    account_status  VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',
    role_id         BIGINT        NOT NULL,
    created_at      DATETIME(6)   NOT NULL,
    last_login_at   DATETIME(6),
    password_hash   VARCHAR(100),
    UNIQUE KEY uk_app_users_azure_ad_id (azure_ad_id),
    UNIQUE KEY uk_app_users_email (email),
    CONSTRAINT fk_app_users_role FOREIGN KEY (role_id) REFERENCES app_roles (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS role_tab_access (
    id       BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    role_id  BIGINT      NOT NULL,
    tab      VARCHAR(40) NOT NULL,
    allowed  TINYINT(1)  NOT NULL DEFAULT 0,
    UNIQUE KEY uk_role_tab (role_id, tab),
    CONSTRAINT fk_role_tab_access_role FOREIGN KEY (role_id) REFERENCES app_roles (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS user_activity_logs (
    id                  BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id             BIGINT        NOT NULL,
    actor_user_id       BIGINT,
    actor_display_name  VARCHAR(200)  NOT NULL,
    action              VARCHAR(40)   NOT NULL,
    detail              VARCHAR(2000),
    created_at          DATETIME(6)   NOT NULL,
    KEY idx_user_activity_user_created (user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS monitored_hosts (
    id                        BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name                      VARCHAR(255) NOT NULL,
    host                      VARCHAR(255) NOT NULL,
    port                      INT          NOT NULL,
    environment               VARCHAR(255) NOT NULL,
    region                    VARCHAR(255) NOT NULL,
    model_hub_environment_id  VARCHAR(255),
    actuator_path             VARCHAR(255) NOT NULL,
    enabled                   TINYINT(1)   NOT NULL DEFAULT 1
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS monitored_hosts_prod (
    id                        BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name                      VARCHAR(255) NOT NULL,
    host                      VARCHAR(255) NOT NULL,
    port                      INT          NOT NULL,
    environment               VARCHAR(255) NOT NULL,
    region                    VARCHAR(255) NOT NULL,
    model_hub_environment_id  VARCHAR(255),
    actuator_path             VARCHAR(255) NOT NULL,
    enabled                   TINYINT(1)   NOT NULL DEFAULT 1
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS health_snapshots (
    id                    BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    total                 INT         NOT NULL,
    up                    INT         NOT NULL,
    recorded_at           DATETIME(6) NOT NULL,
    avg_response_time_ms  INT         NOT NULL DEFAULT 0,
    KEY idx_health_snapshots_recorded_at (recorded_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS managed_servers (
    id        BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name      VARCHAR(120) NOT NULL,
    host      VARCHAR(255) NOT NULL,
    ssh_port  INT          NOT NULL DEFAULT 22,
    ssh_user  VARCHAR(64)  NOT NULL,
    enabled   TINYINT(1)   NOT NULL DEFAULT 1,
    UNIQUE KEY uk_managed_servers_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS managed_services (
    id              BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    server_id       BIGINT       NOT NULL,
    name            VARCHAR(120) NOT NULL,
    description     VARCHAR(255),
    category        VARCHAR(80)  NOT NULL,
    port            INT          NOT NULL,
    start_script    VARCHAR(512) NOT NULL,
    stop_script     VARCHAR(512) NOT NULL,
    restart_script  VARCHAR(512) NOT NULL,
    properties_path VARCHAR(512) NOT NULL,
    enabled         TINYINT(1)   NOT NULL DEFAULT 1,
    CONSTRAINT fk_managed_services_server FOREIGN KEY (server_id) REFERENCES managed_servers (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS admin_action_logs (
    id                  BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    actor_user_id       BIGINT        NOT NULL,
    actor_display_name  VARCHAR(200)  NOT NULL,
    service_id          BIGINT        NOT NULL,
    service_name        VARCHAR(120)  NOT NULL,
    action              VARCHAR(20)   NOT NULL,
    status              VARCHAR(20)   NOT NULL,
    message             VARCHAR(2000),
    created_at          DATETIME(6)   NOT NULL,
    KEY idx_admin_action_logs_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS system_activity_logs (
    id                  BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    type                VARCHAR(40)   NOT NULL,
    icon                VARCHAR(64)   NOT NULL,
    icon_bg_class       VARCHAR(120)  NOT NULL,
    icon_color_class    VARCHAR(120)  NOT NULL,
    message_prefix      VARCHAR(500)  NOT NULL,
    message_highlight   VARCHAR(500)  NOT NULL,
    message_suffix      VARCHAR(500),
    detail              VARCHAR(1000),
    created_at          DATETIME(6)   NOT NULL,
    KEY idx_system_activity_created (created_at),
    KEY idx_system_activity_type_created (type, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- System roles. The app also ensures these on startup; INSERT IGNORE is safe to re-run.
INSERT IGNORE INTO app_roles (code, name, description, system_role) VALUES
    ('ADMIN', 'Administrator', 'Full platform access', 1),
    ('TESTER', 'Tester', 'API testing and validation tools', 1),
    ('MONITORING', 'Monitoring', 'Dashboard and system health monitoring', 1);

-- Default tab matrix (AppTab enum names). Missing rows are also added by AuthDataInitializer.
INSERT INTO role_tab_access (role_id, tab, allowed)
SELECT r.id, m.tab, m.allowed
FROM app_roles r
INNER JOIN (
    SELECT 'ADMIN' AS code, 'DASHBOARD'     AS tab, 1 AS allowed UNION ALL
    SELECT 'ADMIN',         'HEALTH',              1 UNION ALL
    SELECT 'ADMIN',         'TRANSACTIONS',        1 UNION ALL
    SELECT 'ADMIN',         'TESTER',              1 UNION ALL
    SELECT 'ADMIN',         'ADMIN',               1 UNION ALL
    SELECT 'ADMIN',         'USERS',               1 UNION ALL
    SELECT 'ADMIN',         'DEV_UTILS',           1 UNION ALL
    SELECT 'TESTER',        'DASHBOARD',           1 UNION ALL
    SELECT 'TESTER',        'HEALTH',              1 UNION ALL
    SELECT 'TESTER',        'TRANSACTIONS',        0 UNION ALL
    SELECT 'TESTER',        'TESTER',              1 UNION ALL
    SELECT 'TESTER',        'ADMIN',               0 UNION ALL
    SELECT 'TESTER',        'USERS',               0 UNION ALL
    SELECT 'TESTER',        'DEV_UTILS',           1 UNION ALL
    SELECT 'MONITORING',    'DASHBOARD',           1 UNION ALL
    SELECT 'MONITORING',    'HEALTH',              1 UNION ALL
    SELECT 'MONITORING',    'TRANSACTIONS',        1 UNION ALL
    SELECT 'MONITORING',    'TESTER',              0 UNION ALL
    SELECT 'MONITORING',    'ADMIN',               0 UNION ALL
    SELECT 'MONITORING',    'USERS',               0 UNION ALL
    SELECT 'MONITORING',    'DEV_UTILS',           0
) m ON m.code = r.code
WHERE NOT EXISTS (
    SELECT 1
    FROM role_tab_access existing
    WHERE existing.role_id = r.id
      AND existing.tab = m.tab
);

-- Do not insert users here. First Administrator is created on app start from
-- BRE_DASHBOARD_BOOTSTRAP_EMAIL and BRE_DASHBOARD_BOOTSTRAP_PASSWORD.
