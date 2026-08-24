# PostgreSQL Flyway Migration Design

## Goal

Use Flyway to apply versioned PostgreSQL schema changes automatically at application startup, while allowing the existing development database to be explicitly baselined without replaying historical migrations.

## Scope

Flyway manages incremental migrations only. The existing `script/sql/pgsql.sql` remains the base schema/data initialization script and is not executed automatically by the application.

The first implementation targets PostgreSQL and the `htht-admin` application. MySQL and other database profiles are not included in automatic migration until they have a separate migration set.

## Architecture

The `htht-admin` module will include Flyway and package migrations under `classpath:db/migration`. Flyway connects using the configured PostgreSQL datasource credentials, creates and maintains `flyway_schema_history`, and uses PostgreSQL advisory locking during migration.

The source-of-truth migration files remain under `script/sql/migrations`. The build will copy them into the application resources so the packaged JAR contains the exact files used at runtime. Every migration uses a monotonically increasing `V<version>__<description>.sql` name and is immutable after release.

## Database Workflows

### New database

1. Create the PostgreSQL database.
2. Load `script/sql/pgsql.sql` once to create the legacy application schema and seed data.
3. Start the application with Flyway enabled.
4. Flyway applies every versioned migration that is not present in `flyway_schema_history`.

### Existing database

1. Back up the database.
2. Apply any one-off compatibility SQL required by the current release, such as expanding `sys_file.file_path` and `sys_file.static_path`.
3. Run Flyway's explicit baseline command at the latest migration version already represented by the database.
4. Start the application; only migrations newer than the baseline are applied.

`baselineOnMigrate` remains disabled by default so an accidentally unregistered database cannot be silently accepted. Baseline is an explicit deployment operation.

## Configuration

The default configuration enables Flyway for PostgreSQL and points it at `classpath:db/migration`. The Flyway connection uses the same PostgreSQL URL, username, and password as the application datasource, but is declared explicitly so the dynamic-datasource wrapper does not become Flyway's datasource.

Required operational properties:

```yaml
spring:
  flyway:
    enabled: true
    locations: classpath:db/migration
    baseline-on-migrate: false
    validate-on-migrate: true
    clean-disabled: true
```

The URL, username, and password are supplied per environment and must not be hard-coded in the shared configuration.

## Failure and Safety Rules

- A checksum change to an already-applied migration fails validation and requires a new migration instead of editing history.
- Any migration error aborts application startup.
- `clean` is disabled in every environment.
- Baseline is never automatic.
- Migration SQL must be forward-only and safe to run once; rollback instructions belong in comments and deployment documentation.

## Testing

The implementation will add a focused migration integration test against PostgreSQL that verifies:

1. A clean schema receives all migrations in version order.
2. A second run is a no-op.
3. An existing schema can be explicitly baselined and then accepts a newer migration.
4. A failed migration prevents application startup.

The test database is isolated from the developer database through a dedicated JDBC URL or disposable PostgreSQL container.

## Operational Documentation

Deployment documentation will include:

- how to initialize a new database;
- how to baseline the current development/production database;
- how to inspect `flyway_schema_history`;
- how to recover from checksum or failed-migration errors;
- the exact Maven and JAR startup commands.

## Non-goals

- Automatically importing the full legacy `pgsql.sql` dump.
- Supporting MySQL, Oracle, SQL Server, or DM migrations in this first iteration.
- Providing a web UI for migration management.
