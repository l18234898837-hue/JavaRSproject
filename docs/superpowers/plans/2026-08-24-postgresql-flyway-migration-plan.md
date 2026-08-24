# PostgreSQL Flyway Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add explicit-baseline Flyway migration management for PostgreSQL while preserving the existing `pgsql.sql` base-schema workflow.

**Architecture:** `htht-admin` owns Flyway startup integration and connects directly to the configured PostgreSQL datasource. Versioned SQL remains under `script/sql/migrations`; Maven copies those files into `classpath:db/migration` during resource processing. Flyway validates and applies pending migrations with `baselineOnMigrate=false`, so existing databases must be baselined explicitly.

**Tech Stack:** Spring Boot 2.7.13, Flyway managed by Spring Boot dependency management, PostgreSQL, Maven, YAML configuration.

---

### Task 1: Add Flyway dependency and package migration resources

**Files:**
- Modify: `htht-admin/pom.xml`
- Modify: `pom.xml` only if a Flyway version property is required by dependency management
- Runtime output: `htht-admin/target/classes/db/migration/` (generated, not committed)

- [ ] **Step 1: Add the Flyway runtime dependency**

Add `org.flywaydb:flyway-core` to `htht-admin/pom.xml` without hard-coding a version when Spring Boot dependency management supplies one.

- [ ] **Step 2: Configure Maven resource copying**

Add a `maven-resources-plugin` execution in `htht-admin/pom.xml` bound to `process-resources`, copying `${project.parent.basedir}/script/sql/migrations` into `${project.build.outputDirectory}/db/migration` and including only `V*.sql` files. Do not copy `README.md` into the runtime migration location.

- [ ] **Step 3: Build and verify resource placement**

Run:

```powershell
mvn -Pdev -pl :htht-admin -am -DskipTests process-resources
Get-ChildItem htht-admin\target\classes\db\migration -Filter V*.sql
```

Expected: every versioned SQL file from `script/sql/migrations` is present exactly once under `target/classes/db/migration`.

- [ ] **Step 4: Commit**

```powershell
git add htht-admin/pom.xml pom.xml
git commit -m "build: add Flyway migration runtime"
```

### Task 2: Configure explicit PostgreSQL Flyway datasource

**Files:**
- Modify: `htht-admin/src/main/resources/application.yml`
- Modify: `htht-admin/src/main/resources/application-dev.yml`
- Modify: `htht-admin/src/main/resources/application-prod.yml`

- [ ] **Step 1: Add shared Flyway safety defaults**

Configure:

```yaml
spring:
  flyway:
    enabled: true
    locations: classpath:db/migration
    baseline-on-migrate: false
    validate-on-migrate: true
    clean-disabled: true
```

Keep the URL, username, and password environment-specific rather than embedding credentials in shared configuration.

- [ ] **Step 2: Bind Flyway to the PostgreSQL connection**

In each PostgreSQL environment profile, set `spring.flyway.url`, `spring.flyway.user`, and `spring.flyway.password` from that profile's existing PostgreSQL values. This bypasses the dynamic-datasource wrapper and gives Flyway a direct JDBC connection.

- [ ] **Step 3: Verify YAML resolves without changing the application datasource**

Run:

```powershell
mvn -Pdev -pl :htht-admin -am -DskipTests process-resources
```

Inspect the effective profile configuration and confirm the application still uses `postgres` as the dynamic datasource while Flyway has its own direct connection properties.

- [ ] **Step 4: Commit**

```powershell
git add htht-admin/src/main/resources/application.yml htht-admin/src/main/resources/application-dev.yml htht-admin/src/main/resources/application-prod.yml
git commit -m "feat: configure PostgreSQL Flyway startup migrations"
```

### Task 3: Document new-database and existing-database workflows

**Files:**
- Modify: `script/sql/migrations/README.md`
- Modify: `docs/deployment/multisource-backend-deployment.md`
- Create: `script/sql/migrations/baseline-existing-postgres.sql`

- [ ] **Step 1: Add an explicit baseline SQL template**

Create a template that operators run after reviewing the current schema version. It must use Flyway's metadata table and include a guarded insert for the selected baseline version; it must not call `flyway clean` or drop application tables.

- [ ] **Step 2: Document the new database flow**

Document: create database, load `script/sql/pgsql.sql` once, start the application, and inspect `flyway_schema_history`.

- [ ] **Step 3: Document the existing database flow**

Document backup, schema compatibility checks, explicit baseline, startup, and verification queries. Include the current baseline version represented by the repository migrations and explain how a later migration is added.

- [ ] **Step 4: Document failure recovery**

Document checksum mismatch handling (restore/fix migration history and create a new migration rather than editing an applied file), failed migration repair, and the fact that `clean` is disabled.

- [ ] **Step 5: Commit**

```powershell
git add script/sql/migrations/README.md script/sql/migrations/baseline-existing-postgres.sql docs/deployment/multisource-backend-deployment.md
git commit -m "docs: document Flyway baseline and deployment workflow"
```

### Task 4: Run focused verification against an isolated PostgreSQL schema

**Files:**
- Modify only if verification exposes a defect in Tasks 1-3.

- [ ] **Step 1: Create an isolated verification database or schema**

Use a disposable PostgreSQL database/schema, never `htht_rs_dev`, for migration verification.

- [ ] **Step 2: Verify clean migration discovery**

Run the packaged application or Flyway command against the isolated target and confirm all versioned migrations are recorded in `flyway_schema_history` in version order.

- [ ] **Step 3: Verify idempotent startup**

Run the same migration operation a second time and confirm it is a no-op with no duplicate-table or duplicate-data errors.

- [ ] **Step 4: Verify explicit baseline behavior**

Baseline a second isolated schema at the current repository version, add a temporary higher-version migration in a disposable copy, and confirm only the higher version is applied. Remove the temporary migration after the check.

- [ ] **Step 5: Build the backend**

Run:

```powershell
mvn -Pdev -pl :htht-admin -am -DskipTests package
```

Expected: `BUILD SUCCESS` and the packaged JAR contains `BOOT-INF/classes/db/migration/V*.sql`.

- [ ] **Step 6: Commit any required corrections and report evidence**

Do not claim completion unless the clean run, second run, baseline behavior, and package verification all pass. Keep unrelated pre-existing worktree changes untouched.

## Rollout Notes

The first deployment to the current development database requires the explicit baseline operation before enabling the new application build. New databases must load the base `pgsql.sql` before application startup. Future schema changes are added only as new immutable `V...sql` files under `script/sql/migrations`.
