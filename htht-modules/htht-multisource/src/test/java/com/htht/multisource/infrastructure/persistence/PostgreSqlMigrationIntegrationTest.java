package com.htht.multisource.infrastructure.persistence;

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
