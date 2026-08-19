# Multisource Persistence Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Execute task-by-task with `superpowers:subagent-driven-development` or `superpowers:executing-plans`.

**Goal:** Persist Slice A state through PostgreSQL migrations and MyBatis adapters, proved in a disposable development schema.

**Architecture:** Domain records own durable creation snapshots. Infrastructure implements outbound ports against `rs_` tables. Tests create a random schema, set `search_path`, migrate, assert, and drop it.

**Tech Stack:** Java 17, JUnit 5, AssertJ, MyBatis-Plus, PostgreSQL JDBC, Maven.

**Design:** `docs/superpowers/specs/2026-08-19-multisource-persistence-foundation-design.md`

---

### Task 1: Add Durable Creation Snapshots

**Files:** Modify `domain/model/ProcessingTask.java`, `domain/model/InputAsset.java`, `application/usecase/service/TaskCreationCoordinator.java`, related domain/application fixtures and tests.

- [x] **Step 1: Write failing tests**

```java
assertThat(task.name()).isEqualTo("task-01");
assertThat(task.parameterSnapshot()).isEqualTo("{\"mode\":\"standard\"}");
assertThat(task.outputUri()).isEqualTo(new StorageUri("rs://output/task-01"));
assertThat(asset.storageUriSnapshot()).isEqualTo(new StorageUri("rs://input/granule.hdf"));
```

- [x] **Step 2: Run red test**

```powershell
mvn -pl htht-modules\htht-multisource -am -Dtest=ProcessingTaskTest,CreateProcessingTaskUseCaseTest -DfailIfNoTests=false test
```

Expected: snapshot accessors do not compile.

- [x] **Step 3: Implement minimum record changes**

```java
public record ProcessingTask(TaskId id, String name, AccessSubject creator,
    TaskLifecycleStatus lifecycleStatus, QualityStatus qualityStatus,
    String parameterSnapshot, StorageUri outputUri, ProcessingPlan plan,
    ProcessingBatch batch, List<ProductCandidate> candidates) { }

public record InputAsset(String role, int ordinal, AssetReference reference,
    StorageUri storageUriSnapshot, String checksum, boolean required) { }
```

Reject blank task name/snapshot and null URI fields. Pass command name, snapshot, and URI through the coordinator; batch children use the batch name.

- [x] **Step 4: Run green test and commit**

Run Step 2, expect zero failures/errors, then commit `feat: retain multisource task persistence snapshots`.

### Task 2: Add Auditable Migrations

**Files:** Create `script/sql/migrations/V20260819_01__create_multisource_core_tables.sql`, `V20260819_02__create_multisource_indexes.sql`, `V20260819_03__seed_multisource_profiles.sql`, and `src/test/java/com/htht/multisource/infrastructure/persistence/MigrationScriptTest.java`.

- [x] **Step 1: Write failing script test**

```java
assertThat(coreSql).contains("CREATE TABLE rs_processing_batch", "CREATE TABLE rs_task_event");
assertThat(coreSql).contains("UNIQUE (stage_id, attempt, event_sequence)");
assertThat(coreSql).doesNotContain("bus_data_info", "sd_algorithm_run_record", "st_algorithm_record");
```

- [x] **Step 2: Run red test**

```powershell
mvn -pl htht-modules\htht-multisource -am -Dtest=MigrationScriptTest -DfailIfNoTests=false test
```

Expected: scripts are absent.

- [x] **Step 3: Create scripts**

Create only `rs_schema_migration`, `rs_processing_batch`, `rs_processing_task`, `rs_task_input_asset`, `rs_task_product`, `rs_task_stage`, `rs_task_stage_dependency`, `rs_task_event`, `rs_artifact`, `rs_quality_result`, `rs_product_profile`, `rs_processing_capability`, `rs_profile_capability`. Use internal foreign keys, JSONB snapshots, timestamptz execution fields, event uniqueness `(stage_id, attempt, event_sequence)`, and no legacy table references. Seed only the four approved `SUPPORTED` v1 profiles.

- [x] **Step 4: Run green test and commit**

Run Step 2, expect zero failures/errors, then commit `feat: add multisource PostgreSQL migrations`.

### Task 3: Implement Persistence Adapters

**Files:** Modify `htht-modules/htht-multisource/pom.xml`; create entities, mappers, and repository adapters for batch, task, task product, input asset, stage, and artifact under `src/main/java/com/htht/multisource/infrastructure/persistence`; create `RepositoryMappingTest.java`.

- [ ] **Step 1: Write failing mapping test**

```java
ProcessingTaskEntity entity = mapper.toEntity(task);
assertThat(entity.getName()).isEqualTo("task-01");
assertThat(entity.getParameterSnapshot()).isEqualTo("{\"mode\":\"standard\"}");
assertThat(mapper.toTask(entity, batch, candidates)).isEqualTo(task);
```

- [ ] **Step 2: Run red test**

```powershell
mvn -pl htht-modules\htht-multisource -am -Dtest=RepositoryMappingTest -DfailIfNoTests=false test
```

Expected: persistence types do not compile.

- [ ] **Step 3: Add adapters**

Add test-scope `org.postgresql:postgresql`. Use `@TableName`, input IDs, and `BaseMapper<Entity>`. Repositories implement only matching outbound ports. Task save writes task, candidates, then assets; lookup rebuilds the immutable task. ID adapters wrap an injected positive `LongSupplier`.

- [ ] **Step 4: Run green test and commit**

Run Step 2, expect zero failures/errors, then commit `feat: add multisource MyBatis persistence adapters`.

### Task 4: Verify a Disposable Development Schema

**Files:** Create `src/test/java/com/htht/multisource/infrastructure/persistence/PostgreSqlMigrationIntegrationTest.java`.

- [ ] **Step 1: Write the opt-in integration test**

```java
String schema = "rs_ms_it_" + UUID.randomUUID().toString().replace("-", "");
try (Connection admin = DriverManager.getConnection(url, username, password)) {
    createSchema(admin, schema);
    try (Connection connection = connectWithSearchPath(schema)) {
        runMigrations(connection);
        assertCoreTablesAndSeeds(connection);
        assertRepositoryRoundTrip(connection);
    } finally {
        dropSchema(admin, schema);
    }
    assertSchemaAbsent(admin, schema);
}
```

- [ ] **Step 2: Verify skip behavior**

```powershell
mvn -pl htht-modules\htht-multisource -am -Dtest=PostgreSqlMigrationIntegrationTest -DfailIfNoTests=false test
```

Expected: skip only when all three `MULTISOURCE_IT_*` variables are absent.

- [ ] **Step 3: Verify development database and commit**

```powershell
$env:MULTISOURCE_IT_JDBC_URL='jdbc:postgresql://127.0.0.1:5433/htht_rs_dev'
$env:MULTISOURCE_IT_USERNAME='<development user>'
$env:MULTISOURCE_IT_PASSWORD='<development password>'
mvn -pl htht-modules\htht-multisource -am -Dtest=MigrationScriptTest,RepositoryMappingTest,PostgreSqlMigrationIntegrationTest -DfailIfNoTests=false clean test
```

Expected: zero failures/errors and the random schema is absent after the test. Commit `test: verify multisource persistence foundation`.

### Task 5: Sync Documentation and Regress Slice A

**Files:** Modify `docs/superpowers/plans/2026-08-18-multisource-implementation-plan.md` and this plan.

- [ ] **Step 1: Link this detailed plan from broad Task 4 and mark completed steps.**

- [ ] **Step 2: Run the final regression**

```powershell
mvn -pl htht-modules\htht-multisource -am -Dtest=PreflightProductUseCaseTest,CreateProcessingTaskUseCaseTest,GetTaskDetailUseCaseTest -DfailIfNoTests=false clean test
```

Expected: 8 tests, zero failures/errors. Commit `docs: track multisource persistence implementation`.
