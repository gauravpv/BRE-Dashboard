# OpsConsole

Enterprise DevOps operations dashboard built with Spring Boot 3, Thymeleaf, and MySQL.

## Features

- Dashboard with system health monitoring and uptime chart
- System Health — actuator probes and Model Hub watchlists (UAT and PROD)
- Transaction Analytics — live MySQL reporting views
- System Admin — SSH service control (start/stop/restart) and properties editing
- User Admin — roles, tab access, user CRUD
- Bajaj Tester — encrypted live API invoke (UAT and PROD URLs in YAML)
- Activity feed — logins, health changes, admin actions
- Temporary local login, with Azure AD (OAuth2) ready when Entra is enabled

## Requirements

- Java 17
- Maven 3.9+
- MySQL 8 for UAT/PROD (`opsconsole` + `bre_underwriting` schemas)

## Run locally (H2)

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

Open http://localhost:8080

Local profile still seeds `admin@opsconsole.local` / `Admin@123` for developers only. Do not use that profile in UAT or PROD.

## Run UAT / PROD

See [deploy/README.md](deploy/README.md) for SQL scripts and environment variables.

```bash
# After creating MySQL schemas
SPRING_PROFILES_ACTIVE=uat
# or
SPRING_PROFILES_ACTIVE=prod
```

Set `OPSCONSOLE_BOOTSTRAP_EMAIL` and `OPSCONSOLE_BOOTSTRAP_PASSWORD` on first boot, then change the password in the UI.

Live SSH: `opsconsole.admin.mode=live` (default) and `OPS_SSH_KEY_PATH`.

## Tests

```bash
mvn test
```

Tests use an in-memory H2 database.

## License

Internal use.
