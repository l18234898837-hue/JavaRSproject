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
    private static final Path INPUT_ASSET_MAPPER = Path.of(
        "src", "main", "java", "com", "htht", "multisource", "infrastructure", "persistence", "mapper",
        "TaskInputAssetMyBatisMapper.java");

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
            "UNIQUE (workspace_key, sys_file_id)",
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

    @Test
    void workspaceScopedInputAssetQueryBindsWorkspaceKey() throws IOException {
        String mapper = Files.readString(INPUT_ASSET_MAPPER);

        assertThat(mapper).contains(
            "rs_processing_task",
            "workspace_key",
            "{0}",
            "selectByTaskIdAndWorkspace"
        );
    }
}
