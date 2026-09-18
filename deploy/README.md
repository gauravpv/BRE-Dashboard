# MySQL deployment

Use the same OpsConsole build for UAT and PROD. Switch only the Spring profile and environment variables.

## 1. Create databases and users

Edit passwords in `01-create-databases-and-users.sql`, then as a DBA:

```bash
mysql -u root -p < deploy/mysql/01-create-databases-and-users.sql
mysql -u root -p < deploy/mysql/02-opsconsole-schema.sql
mysql -u root -p < deploy/mysql/03-transaction-schema.sql
mysql -u root -p < deploy/mysql/04-grants.sql
mysql -u root -p < deploy/mysql/05-verification.sql
```

`03-transaction-schema.sql` creates `bre_underwriting.transaction_details`, `transaction_details_srcreq_otp`, and the two festival summary views. If those objects already exist in the reporting database, skip that script and only grant `SELECT`.

## 2. Environment variables

| Variable | Purpose |
|----------|---------|
| `SPRING_PROFILES_ACTIVE` | `uat` or `prod` |
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
# UAT
set SPRING_PROFILES_ACTIVE=uat
mvn spring-boot:run

# PROD
set SPRING_PROFILES_ACTIVE=prod
java -jar target/opsconsole-0.0.1-SNAPSHOT.jar
```

Change the bootstrap password immediately at `/account/password`.

## 5. Later: Microsoft Entra / ADID

Set `SPRING_PROFILES_ACTIVE=prod,azure` and `AZURE_CLIENT_ID`, `AZURE_CLIENT_SECRET`, `AZURE_TENANT_ID`. Register the production redirect URI. Existing users keep their roles; new Entra users get `MONITORING` until an administrator updates them.
