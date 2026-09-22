# BRE Dashboard

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
- MySQL 8 for UAT/PROD: BRE Dashboard database (`bre_dashboard`). Transaction Analytics uses a separate existing reporting MySQL (`TXN_DB_*`), not created by this project.

## Run locally (H2)

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

Open http://localhost:8080

Local profile still seeds `admin@bredashboard.local` / `Admin@123` for developers only. Do not use that profile in UAT or PROD.

## Run UAT / PROD

See [deploy/README.md](deploy/README.md) for SQL scripts. For UAT/PROD, copy [deploy/server/application.yml](deploy/server/application.yml) (or the UAT template) next to the JAR, fill the values, and run `java -jar` from that folder. No environment variables.

```bash
# After creating the BRE Dashboard MySQL database, on the server:
java -jar bre-dashboard-0.0.1-SNAPSHOT.jar
```

Fill `bredashboard.auth.bootstrap-email` and `bootstrap-password` in that YAML on first boot, then change the password in the UI.

Live SSH: set `bredashboard.admin.ssh.private-key-path` in the same YAML.

## Tests

```bash
mvn test
```

Tests use an in-memory H2 database.

## License

Internal use.
