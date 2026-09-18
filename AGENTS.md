# AGENTS.md

This file provides guidance to agents when working with code in this repository.

## Stack
- Java 21, Spring Boot 3.4.5, Thymeleaf, Spring Security, Spring Data JPA
- H2 embedded file DB (`./data/opsconsole`) in dev; in-memory H2 (`MODE=PostgreSQL`) in tests
- No external build plugins beyond `spring-boot-maven-plugin`

## Commands
```
# Build & run
mvn spring-boot:run

# All tests
mvn test

# Single test class
mvn test -Dtest=ModelHubWatchlistServiceTest

# Single test method
mvn test -Dtest=ModelHubWatchlistServiceTest#evaluateWatchlist_marksMatchingInstanceUp

# Package (skip tests)
mvn package -DskipTests
```

## Critical Gotchas

### H2 ENUM column migration
H2 persists `@Enumerated` columns as ENUM types. When `AppTab` gains a new constant, `H2EnumColumnMigration` runs at startup (`@Order(Integer.MIN_VALUE)`) to `ALTER TABLE role_tab_access ALTER COLUMN tab VARCHAR(40)`. If you add a new `AppTab` constant, this migration handles the H2 compatibility automatically — do not add DDL scripts for this.

### Two separate `MonitoredHost` entities
- `MonitoredHost` (table `monitored_hosts`) — UAT tier
- `MonitoredHostProd` (table `monitored_hosts_prod`) — Production tier

These are parallel classes, not a hierarchy. Services have separate `evaluateUatWatchlist` / `evaluateProdWatchlist` / `check` / `checkProd` pairs. Always keep both in sync when changing host-related logic.

### Auth modes
`opsconsole.auth.mode` is `dev` (email/password) or `azure` (Microsoft Entra/OIDC). The `azure` Spring profile activates OAuth2 config. Dev seed accounts are in `application.yml` comments.

### Navigation access control
`NavAccessInterceptor` enforces tab access by matching request paths to `AppTab` enum values. Adding a new page requires adding a corresponding `AppTab` constant — access is denied for any tab not present in the enum.

### Import ordering convention (non-standard)
`ModelHubWatchlistService.java` shows blank lines between every import statement (one import per line, each separated by a blank line). This is not the project-wide style — `ActuatorHealthService.java` and others use standard grouped imports without blank lines between each. Follow the style of the file you are editing.

## Test conventions
- Unit tests (no `@SpringBootTest`) use plain instantiation: `service = new ModelHubWatchlistService()`
- Integration/controller tests use `@SpringBootTest` + `@AutoConfigureMockMvc` / `@DataJpaTest`
- `src/test/resources/application.yml` overrides: disables model-hub, uses in-memory H2 with `create-drop` DDL
- Parser tests for Model Hub JSON live under `src/test/resources/modelhub/`
- Test classes live in the same sub-package as the source class (e.g., `com.opsconsole.health`) not in a mirrored path

## Module layout
```
com.opsconsole.
  activity/     — system activity feed (events, log)
  admin/        — SSH-based service admin (start/stop/restart, properties edit)
  tester/       — Bajaj-specific AES-CBC encrypted API tester
  config/       — app-wide beans and H2 migration
  health/       — Spring Actuator polling + Model Hub integration
  tester/       — Bajaj-specific AES-CBC encrypted API tester
  web/          — top-level page controllers
```
