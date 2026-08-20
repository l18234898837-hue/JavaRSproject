package com.htht.multisource.infrastructure.persistence;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.htht.multisource.domain.model.AccessSubject;
import com.htht.multisource.domain.model.Artifact;
import com.htht.multisource.domain.model.AssetReference;
import com.htht.multisource.domain.model.InputAsset;
import com.htht.multisource.domain.model.ProcessingBatch;
import com.htht.multisource.domain.model.ProcessingBatchStatus;
import com.htht.multisource.domain.model.ProcessingPlan;
import com.htht.multisource.domain.model.ProcessingStage;
import com.htht.multisource.domain.model.ProcessingTask;
import com.htht.multisource.domain.model.ProductCandidate;
import com.htht.multisource.domain.model.ProductDataShape;
import com.htht.multisource.domain.model.ProductProfileKey;
import com.htht.multisource.domain.model.ProductSupportStatus;
import com.htht.multisource.domain.model.QualityStatus;
import com.htht.multisource.domain.model.StageExecutionStatus;
import com.htht.multisource.domain.model.StageId;
import com.htht.multisource.domain.model.StorageUri;
import com.htht.multisource.domain.model.TaskId;
import com.htht.multisource.domain.model.TaskLifecycleStatus;
import com.htht.multisource.domain.model.TaskStage;
import com.htht.multisource.infrastructure.persistence.mapper.ArtifactMyBatisMapper;
import com.htht.multisource.infrastructure.persistence.mapper.BatchProductMyBatisMapper;
import com.htht.multisource.infrastructure.persistence.mapper.ProcessingBatchMyBatisMapper;
import com.htht.multisource.infrastructure.persistence.mapper.ProcessingTaskMyBatisMapper;
import com.htht.multisource.infrastructure.persistence.mapper.TaskInputAssetMyBatisMapper;
import com.htht.multisource.infrastructure.persistence.mapper.TaskProductMyBatisMapper;
import com.htht.multisource.infrastructure.persistence.mapper.TaskStageMyBatisMapper;
import com.htht.multisource.infrastructure.persistence.mapping.TaskPersistenceMapper;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.exceptions.PersistenceException;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("dev")
class PostgreSqlMigrationIntegrationTest {

    private static final Path MIGRATIONS = Path.of("..", "..", "script", "sql", "migrations");
    private static final List<String> MIGRATION_FILES = List.of(
        "V20260819_01__create_multisource_core_tables.sql",
        "V20260819_02__create_multisource_indexes.sql",
        "V20260819_03__seed_multisource_profiles.sql"
    );

    @Test
    void migrationsRunAndCleanUpOnlyTheirRandomSchema() throws Exception {
        String url = System.getenv("MULTISOURCE_IT_JDBC_URL");
        String username = System.getenv("MULTISOURCE_IT_USERNAME");
        String password = System.getenv("MULTISOURCE_IT_PASSWORD");
        Assumptions.assumeTrue(url != null && username != null && password != null,
            "Set all MULTISOURCE_IT_JDBC_URL, MULTISOURCE_IT_USERNAME and MULTISOURCE_IT_PASSWORD to run.");

        String schema = "rs_ms_it_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection admin = DriverManager.getConnection(url, username, password)) {
            createSchema(admin, schema);
            try {
                try (Connection connection = DriverManager.getConnection(url, username, password)) {
                    setSearchPath(connection, schema);
                    runMigrations(connection);
                    assertSchema(connection);
                }
            } finally {
                dropSchema(admin, schema);
            }
            assertSchemaAbsent(admin, schema);
        }
    }

    @Test
    void repositoriesRoundTripThroughMyBatisAndPostgreSql() throws Exception {
        String url = System.getenv("MULTISOURCE_IT_JDBC_URL");
        String username = System.getenv("MULTISOURCE_IT_USERNAME");
        String password = System.getenv("MULTISOURCE_IT_PASSWORD");
        Assumptions.assumeTrue(url != null && username != null && password != null,
            "Set all MULTISOURCE_IT_JDBC_URL, MULTISOURCE_IT_USERNAME and MULTISOURCE_IT_PASSWORD to run.");

        String schema = "rs_ms_it_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection admin = DriverManager.getConnection(url, username, password)) {
            createSchema(admin, schema);
            try {
                try (Connection connection = DriverManager.getConnection(url, username, password)) {
                    setSearchPath(connection, schema);
                    runMigrations(connection);
                }
                SqlSessionFactory sessionFactory = sessionFactory(url, username, password, schema);
                try (SqlSession session = sessionFactory.openSession(false)) {
                    TaskPersistenceMapper persistenceMapper = new TaskPersistenceMapper();
                    MyBatisProcessingBatchRepository batchRepository = new MyBatisProcessingBatchRepository(
                        session.getMapper(ProcessingBatchMyBatisMapper.class),
                        session.getMapper(BatchProductMyBatisMapper.class), persistenceMapper);
                    MyBatisProcessingTaskRepository taskRepository = new MyBatisProcessingTaskRepository(
                        session.getMapper(ProcessingTaskMyBatisMapper.class),
                        session.getMapper(TaskProductMyBatisMapper.class),
                        session.getMapper(TaskInputAssetMyBatisMapper.class), batchRepository, persistenceMapper);
                    MyBatisTaskStageRepository stageRepository = new MyBatisTaskStageRepository(
                        session.getMapper(TaskStageMyBatisMapper.class), persistenceMapper);
                    MyBatisArtifactRepository artifactRepository = new MyBatisArtifactRepository(
                        session.getMapper(ArtifactMyBatisMapper.class), persistenceMapper);

                    ProcessingTask task = sampleTask();
                    batchRepository.save(task.batch());
                    taskRepository.save(task);
                    TaskStage stage = new TaskStage(new StageId(201L), task.id(), "READ",
                        StageExecutionStatus.PENDING, 1);
                    TaskStage laterStage = new TaskStage(new StageId(202L), task.id(), "CAL",
                        StageExecutionStatus.PENDING, 1);
                    stageRepository.save(laterStage);
                    stageRepository.save(stage);
                    Artifact laterArtifact = new Artifact(task.id(), laterStage.id(), "INTERMEDIATE",
                        new StorageUri("rs://output/task-01-intermediate.tif"), "sha256:intermediate");
                    Artifact artifact = new Artifact(task.id(), stage.id(), "OUTPUT",
                        new StorageUri("rs://output/task-01.tif"), "sha256:output");
                    artifactRepository.save(laterArtifact);
                    artifactRepository.save(artifact);
                    session.commit();

                    ProcessingBatch restoredBatch = batchRepository.findById(task.batch().id()).orElseThrow();
                    assertThat(restoredBatch).isEqualTo(task.batch());
                    ProcessingTask restoredTask = taskRepository.findById(task.id()).orElseThrow();
                    assertThat(restoredTask.name()).isEqualTo(task.name());
                    assertThat(restoredTask.creator()).isEqualTo(task.creator());
                    assertThat(restoredTask.lifecycleStatus()).isEqualTo(task.lifecycleStatus());
                    assertThat(restoredTask.qualityStatus()).isEqualTo(task.qualityStatus());
                    assertThat(new ObjectMapper().readTree(restoredTask.parameterSnapshot()))
                        .isEqualTo(new ObjectMapper().readTree(task.parameterSnapshot()));
                    assertThat(restoredTask.outputUri()).isEqualTo(task.outputUri());
                    assertThat(restoredTask.plan()).isEqualTo(task.plan());
                    assertThat(restoredTask.batch()).isEqualTo(task.batch());
                    assertThat(restoredTask.candidates())
                        .extracting(ProductCandidate::packageId)
                        .containsExactly("fy3h-granule-01", "hy1c-granule-01");
                    assertThat(stageRepository.findById(stage.id())).contains(stage);
                    assertThat(stageRepository.findByTaskId(task.id())).containsExactly(stage, laterStage);
                    assertThat(artifactRepository.findByTaskId(task.id()))
                        .extracting(Artifact::uri)
                        .containsExactly(laterArtifact.uri(), artifact.uri());
                }
            } finally {
                dropSchema(admin, schema);
            }
            assertSchemaAbsent(admin, schema);
        }
    }

    @Test
    void repositoryWritesCanBeRolledBackWithoutLeavingPartialRows() throws Exception {
        String url = System.getenv("MULTISOURCE_IT_JDBC_URL");
        String username = System.getenv("MULTISOURCE_IT_USERNAME");
        String password = System.getenv("MULTISOURCE_IT_PASSWORD");
        Assumptions.assumeTrue(url != null && username != null && password != null,
            "Set all MULTISOURCE_IT_JDBC_URL, MULTISOURCE_IT_USERNAME and MULTISOURCE_IT_PASSWORD to run.");

        String schema = "rs_ms_it_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection admin = DriverManager.getConnection(url, username, password)) {
            createSchema(admin, schema);
            try {
                try (Connection connection = DriverManager.getConnection(url, username, password)) {
                    setSearchPath(connection, schema);
                    runMigrations(connection);
                }
                SqlSessionFactory sessionFactory = sessionFactory(url, username, password, schema);
                try (SqlSession session = sessionFactory.openSession(false)) {
                    TaskPersistenceMapper persistenceMapper = new TaskPersistenceMapper();
                    MyBatisProcessingBatchRepository batchRepository = new MyBatisProcessingBatchRepository(
                        session.getMapper(ProcessingBatchMyBatisMapper.class),
                        session.getMapper(BatchProductMyBatisMapper.class), persistenceMapper);
                    MyBatisProcessingTaskRepository taskRepository = new MyBatisProcessingTaskRepository(
                        session.getMapper(ProcessingTaskMyBatisMapper.class),
                        session.getMapper(TaskProductMyBatisMapper.class),
                        session.getMapper(TaskInputAssetMyBatisMapper.class), batchRepository, persistenceMapper);

                    ProcessingTask task = sampleTask();
                    batchRepository.save(task.batch());
                    taskRepository.save(task);
                    try {
                        taskRepository.save(task);
                        throw new AssertionError("Expected duplicate task id to fail.");
                    } catch (PersistenceException expected) {
                        session.rollback();
                    }
                }
                try (Connection verification = DriverManager.getConnection(url, username, password)) {
                    setSearchPath(verification, schema);
                    assertThat(queryInt(verification, "SELECT count(*) FROM rs_processing_batch")).isZero();
                    assertThat(queryInt(verification, "SELECT count(*) FROM rs_batch_product")).isZero();
                    assertThat(queryInt(verification, "SELECT count(*) FROM rs_processing_task")).isZero();
                    assertThat(queryInt(verification, "SELECT count(*) FROM rs_task_product")).isZero();
                    assertThat(queryInt(verification, "SELECT count(*) FROM rs_task_input_asset")).isZero();
                    assertThat(queryInt(verification, "SELECT count(*) FROM rs_task_stage")).isZero();
                }
            } finally {
                dropSchema(admin, schema);
            }
            assertSchemaAbsent(admin, schema);
        }
    }

    private static SqlSessionFactory sessionFactory(String url, String username, String password, String schema) {
        String separator = url.contains("?") ? "&" : "?";
        UnpooledDataSource dataSource = new UnpooledDataSource("org.postgresql.Driver",
            url + separator + "currentSchema=" + schema, username, password);
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.setEnvironment(new Environment("integration", new JdbcTransactionFactory(), dataSource));
        configuration.addMapper(ProcessingBatchMyBatisMapper.class);
        configuration.addMapper(BatchProductMyBatisMapper.class);
        configuration.addMapper(ProcessingTaskMyBatisMapper.class);
        configuration.addMapper(TaskProductMyBatisMapper.class);
        configuration.addMapper(TaskInputAssetMyBatisMapper.class);
        configuration.addMapper(TaskStageMyBatisMapper.class);
        configuration.addMapper(ArtifactMyBatisMapper.class);
        return new com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder().build(configuration);
    }

    private static ProcessingTask sampleTask() {
        ProductCandidate first = candidate("fy3h-granule-01", "FY-3H-MERSI",
            "rs://input/fy3h-granule-01.hdf");
        ProductCandidate second = candidate("hy1c-granule-01", "HY-1C-CZI",
            "rs://input/hy1c-granule-01.hdf");
        ProcessingBatch batch = new ProcessingBatch(11L, "batch-01", ProcessingBatchStatus.CREATED,
            List.of(first, second));
        ProcessingPlan plan = new ProcessingPlan("plan-v1", List.of(
            new ProcessingStage("READ", "READ_SOURCE", List.of()),
            new ProcessingStage("CAL", "CALIBRATE", List.of("READ"))));
        return new ProcessingTask(TaskId.of(101L), "task-01", new AccessSubject(7L),
            TaskLifecycleStatus.CREATED, QualityStatus.NOT_CHECKED, "{\"mode\":\"standard\"}",
            new StorageUri("rs://output/task-01"), plan, batch, List.of(first, second));
    }

    private static ProductCandidate candidate(String packageId, String productCode, String uri) {
        return new ProductCandidate(packageId, new ProductProfileKey(productCode, "v1"),
            ProductDataShape.SWATH_RASTER, ProductSupportStatus.SUPPORTED,
            List.of(new InputAsset("MAIN", 0, new AssetReference("LOCAL_FILE", packageId + ".hdf"),
                new StorageUri(uri), "sha256:" + packageId, true)), 0.91);
    }

    private static void runMigrations(Connection connection) throws Exception {
        for (String filename : MIGRATION_FILES) {
            String sql = Files.readString(MIGRATIONS.resolve(filename));
            try (Statement statement = connection.createStatement()) {
                for (String command : sql.split(";")) {
                    if (!command.isBlank()) {
                        statement.execute(command);
                    }
                }
            }
        }
    }

    private static void assertSchema(Connection connection) throws SQLException {
        assertThat(tableExists(connection, "rs_processing_batch")).isTrue();
        assertThat(tableExists(connection, "rs_batch_product")).isTrue();
        assertThat(tableExists(connection, "rs_task_input_asset")).isTrue();
        assertThat(tableExists(connection, "rs_task_event")).isTrue();
        assertThat(indexExists(connection, "idx_rs_processing_task_lifecycle")).isTrue();
        assertThat(queryInt(connection, "SELECT count(*) FROM rs_product_profile WHERE support_status = 'SUPPORTED'"))
            .isEqualTo(4);
        assertThat(queryString(connection, "SELECT data_type FROM information_schema.columns "
            + "WHERE table_name = 'rs_processing_task' AND column_name = 'parameter_snapshot'"))
            .isEqualTo("jsonb");
    }

    private static void createSchema(Connection connection, String schema) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA " + quoteIdentifier(schema));
        }
    }

    private static void dropSchema(Connection connection, String schema) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("DROP SCHEMA " + quoteIdentifier(schema) + " CASCADE");
        }
    }

    private static void setSearchPath(Connection connection, String schema) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("SET search_path TO " + quoteIdentifier(schema));
        }
    }

    private static void assertSchemaAbsent(Connection connection, String schema) throws SQLException {
        assertThat(queryInt(connection, "SELECT count(*) FROM pg_namespace WHERE nspname = '" + schema + "'"))
            .isZero();
    }

    private static boolean tableExists(Connection connection, String table) throws SQLException {
        return queryInt(connection, "SELECT count(*) FROM information_schema.tables WHERE table_schema = current_schema() "
            + "AND table_name = '" + table + "'") == 1;
    }

    private static boolean indexExists(Connection connection, String index) throws SQLException {
        return queryInt(connection, "SELECT count(*) FROM pg_indexes WHERE schemaname = current_schema() "
            + "AND indexname = '" + index + "'") == 1;
    }

    private static int queryInt(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(sql)) {
            result.next();
            return result.getInt(1);
        }
    }

    private static String queryString(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(sql)) {
            result.next();
            return result.getString(1);
        }
    }

    private static String quoteIdentifier(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }
}
