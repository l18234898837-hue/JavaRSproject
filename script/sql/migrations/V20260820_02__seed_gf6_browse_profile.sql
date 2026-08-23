-- Purpose: publish the bounded GF-6 WFV browse profile and its visual-only capability.
-- Dependency: V20260820_01__add_product_metadata_snapshots.sql.
-- Rollback: delete the GF-6 profile-capability relation, capability, and profile in that order.

INSERT INTO rs_product_profile (product_code, profile_version, display_name, data_shape, support_status)
VALUES ('GF-6-WFV-BROWSE', 'v1', 'GF-6 WFV Browse', 'RASTER_GRID', 'SUPPORTED');

INSERT INTO rs_processing_capability (capability_code, capability_version, display_name, status, definition)
VALUES (
    'BROWSE_VISUAL_FEATURES', 'v1', 'Browse Visual Features', 'SUPPORTED',
    '{"allowedOperations":["GEOREFERENCE_BROWSE","EXTRACT_VISUAL_FEATURES","WRITE_BROWSE_REPORT"],"forbiddenOperations":["RADIOMETRIC_CALIBRATION","REFLECTANCE","BAND_RATIO","SPECTRAL_INDEX","ATMOSPHERIC_CORRECTION","RPC_ORTHORECTIFICATION","TERRAIN_CORRECTION","SCIENTIFIC_QUALITY_MASK"]}'::jsonb
);

INSERT INTO rs_profile_capability (product_code, profile_version, capability_code)
VALUES ('GF-6-WFV-BROWSE', 'v1', 'BROWSE_VISUAL_FEATURES');
