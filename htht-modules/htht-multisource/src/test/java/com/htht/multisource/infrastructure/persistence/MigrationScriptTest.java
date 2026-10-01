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

    @Test
    void fy3hMigrationPublishesTheConcretePairedInputProfile() throws IOException {
        String sql = Files.readString(MIGRATIONS.resolve("V20260827_02__seed_fy3h_mersi_l1_profile.sql"));

        assertThat(sql).contains(
            "FY-3H-MERSI", "L1-1000M-GEO1K-v1", "SWATH_RASTER", "SUPPORTED",
            "ON CONFLICT (product_code, profile_version) DO UPDATE"
        );
    }

    @Test
    void gf6L1aMigrationPublishesAnIdempotentRasterGridProfile() throws IOException {
        String sql = Files.readString(MIGRATIONS.resolve("V20260828_01__seed_gf6_wfv_l1a_profile.sql"));

        assertThat(sql).contains(
            "GF-6-WFV-L1A", "GF-6 WFV L1A", "RASTER_GRID", "SUPPORTED",
            "ON CONFLICT (product_code, profile_version) DO UPDATE"
        );
    }

    @Test
    void legacyArchiveMigrationPreservesTaskHistoryWithAnIdempotentMarker() throws IOException {
        String sql = Files.readString(MIGRATIONS.resolve("V20260916_03__create_legacy_plan_archive.sql"));
        assertThat(sql).contains("CREATE TABLE rs_legacy_plan_archive", "REFERENCES rs_processing_task(task_id)",
            "CHECK (plan_version IN ('plan-v1', 'plan-v2'))");
        assertThat(sql).doesNotContain("DROP TABLE", "DELETE FROM", "TRUNCATE");
    }

    @Test
    void artifactRetentionMigrationCreatesOnlyRetentionStateAndHistoryIndexes() throws IOException {
        String sql = Files.readString(MIGRATIONS.resolve("V20260930_01__create_task_artifact_retention.sql"));

        assertThat(sql).contains(
            "CREATE TABLE rs_task_artifact_retention",
            "PURGE_FAILED",
            "idx_rs_processing_task_creator_updated",
            "idx_rs_task_product_product_code",
            "idx_rs_task_artifact_retention_status_cleanup"
        );
        assertThat(sql).doesNotContain("DELETE FROM", "DROP TABLE", "rs_legacy_plan_archive");
    }
}
