-- Publish the engineering GF-6 WFV L1A profile separately from the browse profile.
-- Rollback: DELETE FROM rs_product_profile WHERE product_code = 'GF-6-WFV-L1A' AND profile_version = 'v1';
INSERT INTO rs_product_profile (product_code, profile_version, display_name, data_shape, support_status)
VALUES ('GF-6-WFV-L1A', 'v1', 'GF-6 WFV L1A', 'RASTER_GRID', 'SUPPORTED')
ON CONFLICT (product_code, profile_version) DO UPDATE
SET display_name = EXCLUDED.display_name,
    data_shape = EXCLUDED.data_shape,
    support_status = EXCLUDED.support_status;
