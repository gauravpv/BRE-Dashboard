# MySQL deployment

Use the same BRE Dashboard build for UAT and PROD. Switch only the Spring profile and environment variables.

BRE Dashboard uses **two different databases**. Keep them separate:

| Database | Owned by these scripts? | What it holds |
|----------|-------------------------|---------------|
| **bre_dashboard** (`BRE_DASHBOARD_DB_*`) | Yes | Application data: login users, roles, tab access, health hosts, SSH catalog, activity logs |
| **Transaction reporting** (`TXN_DB_*`) | No | Existing EMI transaction tables/views on another MySQL. The app only **SELECTs**. |

Do not create `bre_underwriting`, transaction tables, or festival views from this repo. Point `TXN_DB_URL` at the host and schema that already have them.

Application login is **not** a third database. Users and roles are tables inside **bre_dashboard**. Microsoft Entra is an identity provider, not a database.

## 1. MySQL from scratch (PROD / UAT) — BRE Dashboard only

Edit passwords in `01-create-databases-and-users.sql`, then as a DBA run **in this order**:

```bash
mysql -u root -p < deploy/mysql/01-create-databases-and-users.sql
mysql -u root -p < deploy/mysql/02-bre-dashboard-schema.sql
mysql -u root -p < deploy/mysql/03-grants.sql
mysql -u root -p < deploy/mysql/04-verification.sql
```

| Script | What it creates |
|--------|-----------------|
| `01` | Database `bre_dashboard` and user `bre_dashboard` |
| `02` | All 11 application tables, `account_status` on `app_users`, system roles, default tab matrix |
| `03` | Least-privilege grants on `bre_dashboard.*` |
| `04` | Checks tables, `account_status`, roles, tab rows, and grants |

`02` does **not** insert users. On first app start, set `BRE_DASHBOARD_BOOTSTRAP_EMAIL` and `BRE_DASHBOARD_BOOTSTRAP_PASSWORD` so the first Administrator is created. Health hosts and SSH catalog are seeded by the app when those tables are empty.

Do **not** run `05` on a fresh database. Use it only for an older BRE Dashboard schema that still has `app_users.enabled` and is missing `account_status`:

```bash
mysql -u root -p < deploy/mysql/05-add-user-approval-status.sql
```

Prod/UAT JPA is `ddl-auto: validate`. The app will not create or alter tables; the scripts above must be applied first.

## 2. Transaction reporting (existing database)

The Transaction Analytics page reads views/tables that already exist, typically:

- `festival_summary_detailed_mins_new`
- `festival_summary_detailed_new`
- `transaction_details_srcreq_otp`

Set `TXN_DB_URL`, `TXN_DB_USERNAME`, `TXN_DB_PASSWORD`, and if needed `TXN_DB_SCHEMA` (default `bre_underwriting`). Use a **read-only** account on that server. Example (run on the **reporting** MySQL, not on `bre_dashboard`):

```sql
-- Adjust user, host, and object names to match the existing reporting database.
GRANT SELECT ON bre_underwriting.transaction_details_srcreq_otp TO 'bre_dashboard_txn'@'%';
GRANT SELECT ON bre_underwriting.festival_summary_detailed_mins_new TO 'bre_dashboard_txn'@'%';
GRANT SELECT ON bre_underwriting.festival_summary_detailed_new TO 'bre_dashboard_txn'@'%';
FLUSH PRIVILEGES;
```

The app never writes to that database. If `TXN_DB_URL` is unset, the page shows Offline.

## 3. Environment variables

| Variable | Purpose |
|----------|---------|
| `SPRING_PROFILES_ACTIVE` | `uat` or `prod`. Entra login is wired only on `prod` |
| `AZURE_CLIENT_ID` | Entra app (web) client id — **prod only**, not Model Hub |
| `AZURE_TENANT_ID` | Entra tenant id (**prod only**) |
| `AZURE_CLIENT_SECRET` | Entra app client secret (**prod only**) |
| `BRE_DASHBOARD_AUTH_MODE` | `dev` (password login, default) or `azure` (Microsoft-only). Used on prod after Entra is live |
| `BRE_DASHBOARD_DB_URL` | `jdbc:mysql://HOST:3306/bre_dashboard?useSSL=true&allowPublicKeyRetrieval=true&serverTimezone=UTC` |
| `BRE_DASHBOARD_DB_USERNAME` | `bre_dashboard` |
| `BRE_DASHBOARD_DB_PASSWORD` | BRE Dashboard application schema password |
| `TXN_DB_URL` | JDBC URL of the **existing** reporting MySQL (not the bre_dashboard database) |
| `TXN_DB_USERNAME` | Read-only user on that reporting database |
| `TXN_DB_PASSWORD` | Reporting schema password |
| `TXN_DB_SCHEMA` | Reporting schema name (default `bre_underwriting`) |
| `SERVER_SSL_ENABLED` | `true` (default on `uat`/`prod`) to terminate TLS in Tomcat; `false` when a load balancer handles HTTPS |
| `SERVER_SSL_KEYSTORE` | path to the `.p12` issued for the dashboard hostname (e.g. `bflbre-dashboard-prod.bajajfinserv.in`) |
| `SERVER_SSL_KEYSTORE_PASSWORD` | password for that `.p12` |
| `SERVER_SSL_KEY_ALIAS` | alias inside the `.p12` (only needed when it holds more than one key) |
| `SERVER_PORT` | HTTPS listen port; `uat`/`prod` default to `8443` (`8080` when TLS is terminated upstream) |
| `BRE_DASHBOARD_BOOTSTRAP_EMAIL` | first Administrator email (created only if no admin exists) |
| `BRE_DASHBOARD_BOOTSTRAP_PASSWORD` | first Administrator password (min 8 chars) |
| `BRE_DASHBOARD_SSH_KEY_PATH` | private key for live SSH admin |
| `MODELHUB_OAUTH_USERNAME` | Azure AD account used to obtain the Model Hub bearer token |
| `MODELHUB_OAUTH_PASSWORD` | password for that Model Hub OAuth account |
| `BAJAJ_UAT_*` / `BAJAJ_PROD_*` | encryption keys, IV, Authorization, source, tokens |
| `BRE_DASHBOARD_SESSION_SECURE` | `true` behind HTTPS (prod profile already sets the cookie Secure flag) |
| `BRE_DASHBOARD_SESSION_TIMEOUT` | inactivity timeout; defaults to `30m` |

YAML files are already inside the JAR. Copy **no** `application*.yml` onto the server unless you want a thin overlay for secrets. Do not copy `application-local.yml`. Optional extra files beside the JAR: `dashboard.p12` (if Tomcat terminates TLS) and the SSH private key (if live SSH admin is used).

## 4. Dashboard certificate

The `.p12` is the server certificate for the dashboard's own hostname, so embedded Tomcat terminates TLS with it. Point `SERVER_SSL_KEYSTORE` at the file and the app serves `https://bflbre-dashboard-prod.bajajfinserv.in:8443`. When `SERVER_SSL_ENABLED=true` (the default on `uat`/`prod`), set the keystore variables; set `SERVER_SSL_ENABLED=false` to run plain HTTP while a certificate is pending or when TLS is handled upstream.

If a load balancer or reverse proxy terminates TLS instead, leave `SERVER_SSL_ENABLED=false`, install the `.p12` there, and set `SERVER_PORT=8080`.

Check the certificate the app will present:

```bash
keytool -list -v -keystore /path/dashboard.p12 -storetype PKCS12
```

## 5. Start the app

```bash
# UAT (password login; Entra is not configured on this profile)
set SPRING_PROFILES_ACTIVE=uat
java -jar bre-dashboard-0.0.1-SNAPSHOT.jar

# PROD (password login + Sign in with Microsoft when AZURE_* is set)
set SPRING_PROFILES_ACTIVE=prod
set AZURE_CLIENT_ID=...
set AZURE_TENANT_ID=...
set AZURE_CLIENT_SECRET=...
java -jar bre-dashboard-0.0.1-SNAPSHOT.jar

# PROD Microsoft-only (hides email/password)
set SPRING_PROFILES_ACTIVE=prod
set BRE_DASHBOARD_AUTH_MODE=azure
java -jar bre-dashboard-0.0.1-SNAPSHOT.jar
```

Change the bootstrap password immediately at `/account/password` if you are still on form login.

## 6. Microsoft Entra / ADID

This is dashboard sign-in. It is separate from Model Hub `bredashboard.health.model-hub.oauth` credentials.

1. In Azure, register a **Web** app. Redirect URI must be exactly:

   `https://bflbre-dashboard-prod.bajajfinserv.in/login/oauth2/code/azure`

   Not `/login/oauth2/code/` (the `azure` suffix is required). If the site is reached on port 8443 with no TLS proxy, include `:8443`.

2. Enable **ID tokens** on the Web platform. Grant delegated Microsoft Graph permissions **openid**, **profile**, and **email**.

3. On the **prod** server only, set `AZURE_CLIENT_ID`, `AZURE_TENANT_ID`, and `AZURE_CLIENT_SECRET`. `SPRING_PROFILES_ACTIVE=prod` is enough; there is no separate `azure` profile. The login page **Sign in with Microsoft** button then calls `/oauth2/authorization/azure`. Set `BRE_DASHBOARD_AUTH_MODE=azure` when you want to turn off email/password. UAT stays on password login.

4. Existing users keep their roles; new Entra users remain Pending until an administrator assigns a role and approves them.

If the callback is `http://…` or an internal hostname, the load balancer must send `X-Forwarded-Proto` and `X-Forwarded-Host`. `uat`/`prod` already set `server.forward-headers-strategy: native`.

### First-time Entra access approval

1. A first Microsoft sign-in stores the Entra object id, email, and full name with status **Pending**.
2. The login is denied and no application session is created.
3. An Administrator opens **User Admin**, selects a role, and clicks **Approve & Activate**.
4. On the next sign-in, the user sees only the tabs enabled for that role under **Roles & Tab Access**.

BRE Dashboard allows one active session per user. A newer login expires the previous session. Disabling a user, changing their role, or using **Force logout** revokes active sessions.
