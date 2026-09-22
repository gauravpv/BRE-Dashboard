# MySQL deployment

Use the same BRE Dashboard JAR for UAT and PROD. Put **one filled YAML** next to the JAR. Do not use environment variables.

BRE Dashboard uses **two different databases**. Keep them separate:

| Database | Owned by these scripts? | What it holds |
|----------|-------------------------|---------------|
| **bre_dashboard** (`BRE_DASHBOARD_DB_*`) | Yes | Application data: login users, roles, tab access, health hosts, SSH catalog, activity logs |
| **Transaction reporting** (`TXN_DB_*`) | No | Existing EMI transaction tables/views on another MySQL. The app only **SELECTs**. |

Do not create `bre_underwriting`, transaction tables, or festival views from this repo. Point the transaction JDBC URL in the YAML next to the JAR at the host and schema that already have them.

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

`02` does **not** insert users. Fill `bredashboard.auth.bootstrap-email` and `bootstrap-password` in the YAML next to the JAR so the first Administrator is created on first start. Health hosts and SSH catalog are seeded by the app when those tables are empty.

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

Set `bredashboard.transaction-dashboard.jdbc-url`, `username`, `password`, and if needed `schema` (default `bre_underwriting`) in the YAML next to the JAR. Use a **read-only** account on that server. Example (run on the **reporting** MySQL, not on `bre_dashboard`):

```sql
-- Adjust user, host, and object names to match the existing reporting database.
GRANT SELECT ON bre_underwriting.transaction_details_srcreq_otp TO 'bre_dashboard_txn'@'%';
GRANT SELECT ON bre_underwriting.festival_summary_detailed_mins_new TO 'bre_dashboard_txn'@'%';
GRANT SELECT ON bre_underwriting.festival_summary_detailed_new TO 'bre_dashboard_txn'@'%';
FLUSH PRIVILEGES;
```

The app never writes to that database. If the reporting JDBC URL is unset, the page shows Offline.

## 3. YAML next to the JAR (no environment variables)

Copy **one** file from this repo, fill every `CHANGE_ME`, and place it in the **same directory as the JAR**. Name it `application.yml` (Spring Boot loads that name automatically).

| Server | Copy this template | Rename to |
|--------|--------------------|-----------|
| PROD | [deploy/server/application.yml](server/application.yml) | `application.yml` |
| UAT | [deploy/server/application-uat.yml](server/application-uat.yml) | `application.yml` |

```
bre-dashboard-0.0.1-SNAPSHOT.jar
application.yml
dashboard.p12          (only if server.ssl.enabled is true)
ssh-key                (only if you use live SSH admin)
```

Start **from that directory**:

```bash
java -jar bre-dashboard-0.0.1-SNAPSHOT.jar
```

`spring.profiles.active` in that file selects `prod` or `uat`. Do not copy `application-local.yml`. Do not copy the YAML files from `src/main/resources`. Do not commit a filled copy.

If a load balancer terminates TLS, set `server.ssl.enabled: false` and `server.port: 8080` in that YAML.

## 4. Dashboard certificate

The `.p12` is the server certificate for the dashboard's own hostname. In the YAML next to the JAR set `server.ssl.key-store` (for example `dashboard.p12` in the same folder), `key-store-password`, and `key-alias`. The app then serves `https://bflbre-dashboard-prod.bajajfinserv.in:8443`. If a load balancer terminates TLS, set `server.ssl.enabled: false` and `server.port: 8080`.

Check the certificate:

```bash
keytool -list -v -keystore dashboard.p12 -storetype PKCS12
```

## 5. Start the app

From the directory that contains the JAR and `application.yml`:

```bash
java -jar bre-dashboard-0.0.1-SNAPSHOT.jar
```

On prod, after Entra works, set `bredashboard.auth.mode: azure` in that YAML to hide password login.

Change the bootstrap password immediately at `/account/password` if you are still on form login.

## 6. Microsoft Entra / ADID

This is dashboard sign-in. It is separate from Model Hub `bredashboard.health.model-hub.oauth` credentials.

1. In Azure, register a **Web** app. Redirect URI must be exactly:

   `https://bflbre-dashboard-prod.bajajfinserv.in/login/oauth2/code/azure`

   Not `/login/oauth2/code/` (the `azure` suffix is required). If the site is reached on port 8443 with no TLS proxy, include `:8443`.

2. Enable **ID tokens** on the Web platform. Grant delegated Microsoft Graph permissions **openid**, **profile**, and **email**.

3. On the **prod** YAML only, fill `bredashboard.auth.azure.client-id`, `client-secret`, and `tenant-id`. The login page **Sign in with Microsoft** button then calls `/oauth2/authorization/azure`. Set `bredashboard.auth.mode: azure` when you want to turn off email/password. UAT stays on password login.

4. Existing users keep their roles; new Entra users remain Pending until an administrator assigns a role and approves them.

If the callback is `http://…` or an internal hostname, the load balancer must send `X-Forwarded-Proto` and `X-Forwarded-Host`. `uat`/`prod` already set `server.forward-headers-strategy: native`.

### First-time Entra access approval

1. A first Microsoft sign-in stores the Entra object id, email, and full name with status **Pending**.
2. The login is denied and no application session is created.
3. An Administrator opens **User Admin**, selects a role, and clicks **Approve & Activate**.
4. On the next sign-in, the user sees only the tabs enabled for that role under **Roles & Tab Access**.

BRE Dashboard allows one active session per user. A newer login expires the previous session. Disabling a user, changing their role, or using **Force logout** revokes active sessions.
