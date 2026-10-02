# Task 1 Report: Storage Retention Schema

## Files changed

- `script/sql/migrations/V20261002_01__create_storage_retention_policy.sql`
- `htht-modules/htht-multisource/src/main/java/com/htht/multisource/infrastructure/persistence/entity/WorkspaceStoragePolicyEntity.java`
- `htht-modules/htht-multisource/src/main/java/com/htht/multisource/infrastructure/persistence/entity/ManagedInputFileEntity.java`
- `htht-modules/htht-multisource/src/main/java/com/htht/multisource/infrastructure/persistence/entity/TaskInputReferenceEntity.java`
- `htht-modules/htht-multisource/src/main/java/com/htht/multisource/infrastructure/persistence/mapper/WorkspaceStoragePolicyMyBatisMapper.java`
- `htht-modules/htht-multisource/src/main/java/com/htht/multisource/infrastructure/persistence/mapper/ManagedInputFileMyBatisMapper.java`
- `htht-modules/htht-multisource/src/main/java/com/htht/multisource/infrastructure/persistence/mapper/TaskInputReferenceMyBatisMapper.java`
- `htht-modules/htht-multisource/src/main/java/com/htht/multisource/infrastructure/persistence/mapper/TaskInputAssetMyBatisMapper.java`
- `htht-modules/htht-multisource/src/test/java/com/htht/multisource/infrastructure/persistence/StorageRetentionMigrationTest.java`

## Tests and commands

1. `mvn -pl htht-modules/htht-multisource -Dtest=StorageRetentionMigrationTest test`
   - RED before implementation: failed because the migration file did not exist.
2. `mvn -pl htht-modules/htht-multisource '-Dtest=StorageRetentionMigrationTest,PostgreSqlMigrationIntegrationTest' test`
   - GREEN: build success; 2 migration assertions passed; PostgreSQL integration tests passed as skipped because `MULTISOURCE_IT_JDBC_URL`, `MULTISOURCE_IT_USERNAME`, and `MULTISOURCE_IT_PASSWORD` were not set.
3. `git diff --check`
   - Passed.

## Design decisions

- The migration is additive and idempotent for the seeded `default` policy. It does not delete, truncate, or drop existing data.
- Existing tasks receive a non-null `workspace_key` defaulting to `default`; the explicit backfill documents the rollout intent.
- Policy defaults are 14 days, 24 hours, 48 hours, and cleanup disabled. Version columns use optimistic-lock-ready `bigint` defaults of zero.
- Managed files and task references use identity keys, timestamp metadata, non-negative size/version checks, workspace/task indexes, and a unique task/file reference constraint.
- Entities use the existing MyBatis-Plus table/camel-case conventions. The existing input-asset mapper gained a workspace-aware compatibility method without breaking the in-memory mapper implementations used by current tests.

## Concerns

- PostgreSQL execution of the new migration was not available in this environment because integration-test connection variables were unset; only the focused SQL assertions and Java compilation were executed.
- The compatibility method in `TaskInputAssetMyBatisMapper` currently delegates to the existing task query. A later service/repository task should enforce workspace filtering at the persistence query boundary when it starts using this method.
