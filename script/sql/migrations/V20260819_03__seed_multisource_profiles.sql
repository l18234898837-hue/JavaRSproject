-- Purpose: publish the approved first multisource product profile set.
-- Dependency: V20260819_01__create_multisource_core_tables.sql.
-- Rollback: delete the four rows by their product_code and profile_version.

INSERT INTO rs_product_profile (product_code, profile_version, display_name, data_shape, support_status)
VALUES
    ('FY-3H-MERSI', 'v1', 'FY-3H MERSI', 'SWATH_RASTER', 'SUPPORTED'),
    ('HY-1C-CZI', 'v1', 'HY-1C CZI', 'SWATH_RASTER', 'SUPPORTED'),
    ('FY-4B-AGRI', 'v1', 'FY-4B AGRI', 'GEOSTATIONARY_RASTER', 'SUPPORTED'),
    ('HY-2D-ALTIMETER', 'v1', 'HY-2D Altimeter', 'ALONG_TRACK_OBSERVATION', 'SUPPORTED');
