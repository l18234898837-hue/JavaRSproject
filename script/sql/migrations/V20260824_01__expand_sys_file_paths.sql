-- Purpose: allow managed upload paths to include long local/Windows roots.
-- Dependency: the existing sys_file table from script/sql/pgsql.sql.
-- Rollback: reduce both columns only after verifying all stored values fit varchar(100).

ALTER TABLE sys_file
    ALTER COLUMN file_path TYPE varchar(1024),
    ALTER COLUMN static_path TYPE varchar(1024);
