package com.htht.multisource.infrastructure.persistence;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("dev")
class MigrationScriptTest {

    private static final Path MIGRATIONS = Path.of("..", "..", "script", "sql", "migrations");

    @Test
    void coreMigrationDefinesOnlyMultisourceTablesAndEventIdempotency() throws IOException {
        String coreSql = Files.readString(MIGRATIONS.resolve("V20260819_01__create_multisource_core_tables.sql"));

        assertThat(coreSql).contains(
            "CREATE TABLE rs_processing_batch",
            "CREATE TABLE rs_processing_task",
            "CREATE TABLE rs_task_input_asset",
            "CREATE TABLE rs_task_event",
            "UNIQUE (stage_id, attempt, event_sequence)"
        );
        assertThat(coreSql).doesNotContain("bus_data_info", "sd_algorithm_run_record", "st_algorithm_record");
        assertThat(coreSql).doesNotContain("stage_type");
    }

    @Test
    void seedMigrationPublishesOnlyApprovedSupportedProfiles() throws IOException {
        String seedSql = Files.readString(MIGRATIONS.resolve("V20260819_03__seed_multisource_profiles.sql"));

        assertThat(seedSql).contains("FY-3H-MERSI", "HY-1C-CZI", "FY-4B-AGRI", "HY-2D-ALTIMETER", "SUPPORTED");
        assertThat(seedSql).doesNotContain("CFOSAT", "SPOT");
    }
}
