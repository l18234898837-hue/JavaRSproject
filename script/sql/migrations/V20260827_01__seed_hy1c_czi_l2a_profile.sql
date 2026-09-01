-- Purpose: publish the versioned HY-1C CZI L2A profile used by the multisource worker.
-- Dependency: V20260824_01__expand_sys_file_paths.sql.
-- Rollback: delete the HY-1C-CZI/L2A-v1 row by product_code and profile_version.

INSERT INTO rs_product_profile (product_code, profile_version, display_name, data_shape, support_status)
VALUES ('HY-1C-CZI', 'L2A-v1', 'HY-1C CZI L2A', 'SWATH_RASTER', 'SUPPORTED')
ON CONFLICT (product_code, profile_version) DO UPDATE
SET display_name = EXCLUDED.display_name,
    data_shape = EXCLUDED.data_shape,
    support_status = EXCLUDED.support_status;
