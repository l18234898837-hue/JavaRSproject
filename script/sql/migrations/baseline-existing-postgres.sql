-- Explicit baseline for an existing PostgreSQL database.
-- Run only after a backup and after verifying that the database already contains
-- the schema represented by migration version 20260824.01.
-- This file is an operator script and is not copied into classpath:db/migration.

CREATE TABLE IF NOT EXISTS flyway_schema_history (
    installed_rank integer NOT NULL,
    version varchar(50),
    description varchar(200) NOT NULL,
    type varchar(20) NOT NULL,
    script varchar(1000) NOT NULL,
    checksum integer,
    installed_by varchar(100) NOT NULL,
    installed_on timestamp without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    execution_time integer NOT NULL,
    success boolean NOT NULL,
    CONSTRAINT flyway_schema_history_pk PRIMARY KEY (installed_rank)
);

CREATE UNIQUE INDEX IF NOT EXISTS flyway_schema_history_s_idx
    ON flyway_schema_history (success, version);

INSERT INTO flyway_schema_history (
    installed_rank, version, description, type, script,
    checksum, installed_by, execution_time, success
)
SELECT
    1, '20260824.01', '<< Flyway Baseline >>', 'BASELINE',
    '<< Flyway Baseline >>', NULL, CURRENT_USER, 0, TRUE
WHERE NOT EXISTS (
    SELECT 1 FROM flyway_schema_history
    WHERE version = '20260824.01' AND success = TRUE
);
