# MySQL deployment

Use the same OpsConsole build for UAT and PROD. Switch only the Spring profile and environment variables.

## 1. MySQL from scratch (PROD / UAT)

Edit passwords in `01-create-databases-and-users.sql`, then as a DBA run **in this order**:

```bash
mysql -u root -p < deploy/mysql/01-create-databases-and-users.sql
mysql -u root -p < deploy/mysql/02-opsconsole-schema.sql
mysql -u root -p < deploy/mysql/03-transaction-schema.sql
mysql -u root -p < deploy/mysql/04-grants.sql
mysql -u root -p < deploy/mysql/05-verification.sql
```

| Script | What it creates |
|--------|-----------------|
| `01` | Databases `opsconsole` and `bre_underwriting`, users `opsconsole` and `opsconsole_txn` |
| `02` | All 11 application tables, `account_status` on `app_users`, system roles, default tab matrix |
| `03` | Reporting tables `transaction_details`, `transaction_details_srcreq_otp`, and the two festival views |
| `04` | Least-privilege grants |
| `05` | Checks tables, `account_status`, roles, tab rows, and grants |

`02` does **not** insert users. On first app start, set `OPSCONSOLE_BOOTSTRAP_EMAIL` and `OPSCONSOLE_BOOTSTRAP_PASSWORD` so the first Administrator is created. Health hosts and SSH catalog are seeded by the app when those tables are empty.

`03-transaction-schema.sql` uses `CREATE TABLE IF NOT EXISTS`. If `bre_underwriting` already has live reporting objects, skip `03` and run only `04` so `opsconsole_txn` gets `SELECT`.

Do **not** run `06` on a fresh database. Use it only for an older OpsConsole schema that still has `app_users.enabled` and is missing `account_status`:

```bash
mysql -u root -p < deploy/mysql/06-add-user-approval-status.sql
```

Prod/UAT JPA is `ddl-auto: validate`. The app will not create or alter tables; the scripts above must be applied first.

## 2. Environment variables

| Variable | Purpose |
|----------|---------|
| `SPRING_PROFILES_ACTIVE` | `uat` or `prod`. Add `,azure` to enable Microsoft Entra login |
| `AZURE_CLIENT_ID` | Entra app (web) client id — dashboard login only, not Model Hub |
| `AZURE_TENANT_ID` | Entra tenant id |
| `AZURE_CLIENT_SECRET` | Entra app client secret |
| `OPSCONSOLE_DB_URL` | `jdbc:mysql://HOST:3306/opsconsole?useSSL=true&allowPublicKeyRetrieval=true&serverTimezone=UTC` |
| `OPSCONSOLE_DB_USERNAME` | `opsconsole` |
| `OPSCONSOLE_DB_PASSWORD` | application schema password |
| `TXN_DB_URL` | `jdbc:mysql://HOST:3306/bre_underwriting?useSSL=true&allowPublicKeyRetrieval=true&serverTimezone=UTC` |
| `TXN_DB_USERNAME` | `opsconsole_txn` |
| `TXN_DB_PASSWORD` | reporting schema password |
| `SERVER_SSL_ENABLED` | `true` (default on `uat`/`prod`) to terminate TLS in Tomcat; `false` when a load balancer handles HTTPS |
| `SERVER_SSL_KEYSTORE` | path to the `.p12` issued for the dashboard hostname (e.g. `bflbre-dashboard-prod.bajajfinserv.in`) |
| `SERVER_SSL_KEYSTORE_PASSWORD` | password for that `.p12` |
| `SERVER_SSL_KEY_ALIAS` | alias inside the `.p12` (only needed when it holds more than one key) |
| `SERVER_PORT` | HTTPS listen port; `uat`/`prod` default to `8443` (`8080` when TLS is terminated upstream) |
| `OPSCONSOLE_BOOTSTRAP_EMAIL` | first Administrator email (created only if no admin exists) |
| `OPSCONSOLE_BOOTSTRAP_PASSWORD` | first Administrator password (min 8 chars) |
| `OPS_SSH_KEY_PATH` | private key for live SSH admin |
| `BAJAJ_UAT_*` / `BAJAJ_PROD_*` | encryption keys, IV, Authorization, source, tokens |
| `OPSCONSOLE_SESSION_SECURE` | `true` behind HTTPS (prod profile already sets the cookie Secure flag) |
| `OPSCONSOLE_SESSION_TIMEOUT` | inactivity timeout; defaults to `30m` |

## 3. Dashboard certificate

The `.p12` is the server certificate for the dashboard's own hostname, so embedded Tomcat terminates TLS with it. Point `SERVER_SSL_KEYSTORE` at the file and the app serves `https://bflbre-dashboard-prod.bajajfinserv.in:8443`. When `SERVER_SSL_ENABLED=true` (the default on `uat`/`prod`), set the keystore variables; set `SERVER_SSL_ENABLED=false` to run plain HTTP while a certificate is pending or when TLS is handled upstream.

If a load balancer or reverse proxy terminates TLS instead, leave `SERVER_SSL_ENABLED=false`, install the `.p12` there, and set `SERVER_PORT=8080`.

Check the certificate the app will present:

```bash
keytool -list -v -keystore /path/dashboard.p12 -storetype PKCS12
```

Fill `opsconsole.health.model-hub.oauth.username` and `password` in `application-uat.yml` or `application-prod.yml` (Azure AD account used to get the Model Hub bearer token).

## 4. Start the app

```bash
# UAT (password login)
set SPRING_PROFILES_ACTIVE=uat
mvn spring-boot:run

# PROD (password login)
set SPRING_PROFILES_ACTIVE=prod
java -jar target/opsconsole-0.0.1-SNAPSHOT.jar

# PROD with Microsoft sign-in (password login still available)
set SPRING_PROFILES_ACTIVE=prod
set AZURE_CLIENT_ID=...
set AZURE_TENANT_ID=...
set AZURE_CLIENT_SECRET=...
java -jar target/opsconsole-0.0.1-SNAPSHOT.jar

# PROD Microsoft-only (hides email/password)
set SPRING_PROFILES_ACTIVE=prod,azure
java -jar target/opsconsole-0.0.1-SNAPSHOT.jar
```

Change the bootstrap password immediately at `/account/password` if you are still on form login.

## 5. Microsoft Entra / ADID

This is dashboard sign-in. It is separate from Model Hub `opsconsole.health.model-hub.oauth` credentials.

1. In Azure, register a **Web** app. Redirect URI must be exactly:

   `https://bflbre-dashboard-prod.bajajfinserv.in/login/oauth2/code/azure`

   Not `/login/oauth2/code/` (the `azure` suffix is required). If the site is reached on port 8443 with no TLS proxy, include `:8443`.

2. Enable **ID tokens** on the Web platform. Grant delegated Microsoft Graph permissions **openid**, **profile**, and **email**.

3. Set `AZURE_CLIENT_ID`, `AZURE_TENANT_ID`, and `AZURE_CLIENT_SECRET`. The login page **Sign in with Microsoft** button then calls `/oauth2/authorization/azure`. You do not need the `azure` Spring profile for that. Add `,azure` only if you want to turn off email/password.

4. Existing users keep their roles; new Entra users remain Pending until an administrator assigns a role and approves them.

If the callback is `http://…` or an internal hostname, the load balancer must send `X-Forwarded-Proto` and `X-Forwarded-Host`. `uat`/`prod` already set `server.forward-headers-strategy: native`.

### First-time Entra access approval

1. A first Microsoft sign-in stores the Entra object id, email, and full name with status **Pending**.
2. The login is denied and no application session is created.
3. An Administrator opens **User Admin**, selects a role, and clicks **Approve & Activate**.
4. On the next sign-in, the user sees only the tabs enabled for that role under **Roles & Tab Access**.

OpsConsole allows one active session per user. A newer login expires the previous session. Disabling a user, changing their role, or using **Force logout** revokes active sessions.
