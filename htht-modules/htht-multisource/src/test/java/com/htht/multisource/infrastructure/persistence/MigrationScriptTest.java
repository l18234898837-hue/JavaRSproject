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
            "CREATE TABLE rs_batch_product",
            "CREATE TABLE rs_processing_task",
            "CREATE TABLE rs_task_input_asset",
            "CREATE TABLE rs_task_event",
            "UNIQUE (stage_id, attempt, event_sequence)"
        );
        assertThat(coreSql).contains(
            "package_id varchar(255) NOT NULL",
            "UNIQUE (task_id, package_id, role, ordinal)",
            "FOREIGN KEY (task_id, package_id) REFERENCES rs_task_product(task_id, package_id)"
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

    @Test
    void forwardMigrationsAddMetadataSnapshotsAndOnlyTheBrowseCapabilityForGf6() throws IOException {
        String snapshotSql = Files.readString(MIGRATIONS.resolve("V20260820_01__add_product_metadata_snapshots.sql"));
        String gf6Sql = Files.readString(MIGRATIONS.resolve("V20260820_02__seed_gf6_browse_profile.sql"));

        assertThat(snapshotSql).contains(
            "ALTER TABLE rs_batch_product ADD COLUMN metadata_snapshot jsonb NOT NULL DEFAULT '{}'::jsonb",
            "ALTER TABLE rs_task_product ADD COLUMN metadata_snapshot jsonb NOT NULL DEFAULT '{}'::jsonb",
            "jsonb_typeof(metadata_snapshot) = 'object'"
        );
        assertThat(gf6Sql).contains("GF-6-WFV-BROWSE", "RASTER_GRID", "BROWSE_VISUAL_FEATURES");
        assertThat(gf6Sql).contains(
            "RADIOMETRIC_CALIBRATION", "SPECTRAL_INDEX", "RPC_ORTHORECTIFICATION",
            "ATMOSPHERIC_CORRECTION", "SCIENTIFIC_QUALITY_MASK"
        );
        assertThat(gf6Sql).doesNotContain("CALIBRATION_CAPABILITY", "SPECTRAL_INDEX_CAPABILITY");
    }

    @Test
    void executionEventMigrationAddsExecutionIdWithoutChangingSourceTables() throws IOException {
        String sql = Files.readString(MIGRATIONS.resolve("V20260821_01__add_execution_event_id.sql"));

        assertThat(sql).contains(
            "ALTER TABLE rs_task_event ADD COLUMN execution_id varchar(128)",
            "Rollback: ALTER TABLE rs_task_event DROP COLUMN execution_id"
        );
        assertThat(sql).doesNotContain("st_island_info", "sd_observe_data");
    }
}
