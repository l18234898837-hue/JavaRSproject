package com.htht.multisource.infrastructure.persistence;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("dev")
class StorageRetentionMigrationTest {

    private static final Path MIGRATIONS = Path.of("..", "..", "script", "sql", "migrations");
    private static final Path MIGRATION = MIGRATIONS.resolve(
        "V20261002_01__create_storage_retention_policy.sql");

    @Test
    void createsWorkspacePolicyAndManagedInputTablesWithSafeDefaults() throws IOException {
        String sql = Files.readString(MIGRATION);

        assertThat(sql).contains(
            "ALTER TABLE rs_processing_task\n    ADD COLUMN workspace_key varchar(128) NOT NULL DEFAULT 'default'",
            "UPDATE rs_processing_task\nSET workspace_key = 'default'\nWHERE workspace_key IS NULL",
            "CREATE TABLE rs_workspace_storage_policy",
            "CREATE TABLE rs_managed_input_file",
            "CREATE TABLE rs_task_input_reference",
            "input_retention_days integer NOT NULL DEFAULT 14",
            "orphan_upload_ttl_hours integer NOT NULL DEFAULT 24",
            "worker_directory_ttl_hours integer NOT NULL DEFAULT 48",
            "auto_cleanup_enabled boolean NOT NULL DEFAULT false",
            "version bigint NOT NULL DEFAULT 0",
            "UNIQUE (task_id, managed_file_id)",
            "idx_rs_processing_task_workspace",
            "idx_rs_managed_input_file_status",
            "idx_rs_task_input_reference_status"
        );
    }

    @Test
    void doesNotDeleteExistingDataAndSeedsDefaultWorkspacePolicy() throws IOException {
        String sql = Files.readString(MIGRATION);

        assertThat(sql).contains(
            "INSERT INTO rs_workspace_storage_policy (workspace_key)",
            "VALUES ('default')",
            "ON CONFLICT (workspace_key) DO NOTHING"
        );
        assertThat(sql).doesNotContain("DROP TABLE", "DELETE FROM", "TRUNCATE");
    }
}
