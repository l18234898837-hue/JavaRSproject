-- Purpose: publish the concrete FY-3H MERSI 1000M + GEO1K processing profile.
-- Dependency: V20260819_01__create_multisource_core_tables.sql.
-- Rollback: DELETE FROM rs_product_profile WHERE product_code = 'FY-3H-MERSI'
--           AND profile_version = 'L1-1000M-GEO1K-v1';

INSERT INTO rs_product_profile (product_code, profile_version, display_name, data_shape, support_status)
VALUES ('FY-3H-MERSI', 'L1-1000M-GEO1K-v1', 'FY-3H MERSI L1 1000M + GEO1K', 'SWATH_RASTER', 'SUPPORTED')
ON CONFLICT (product_code, profile_version) DO UPDATE
SET display_name = EXCLUDED.display_name,
    data_shape = EXCLUDED.data_shape,
    support_status = EXCLUDED.support_status;
