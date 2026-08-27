-- Publish GF6 WFV L1A as a profile separate from the browse JPEG profile.
INSERT INTO rs_product_profile (product_code, profile_version, display_name, data_shape, support_status)
VALUES ('GF-6-WFV-L1A', 'v1', 'GF-6 WFV L1A', 'RASTER_GRID', 'SUPPORTED');

INSERT INTO rs_processing_capability (capability_code, capability_version, display_name, status, definition)
VALUES ('GF6_L1A_ENGINEERING', 'v1', 'GF6 L1A Engineering Processing', 'SUPPORTED',
        '{"fixedStages":["MOSAIC_GF6_TILES","CALIBRATE_GF6_WFV","BUILD_GF6_RPC_GRID","ALIGN_GF6_BANDS","CROP_GF6_RASTER","WRITE_GF6_STANDARD_COG"],"optionalStages":["EXTRACT_GF6_FEATURES","FUSE_GF6_BANDS","DETECT_GF6_SALIENCY"],"navigationStatus":"NOMINAL_RPC_NO_DEM"}'::jsonb);

INSERT INTO rs_profile_capability (product_code, profile_version, capability_code)
VALUES ('GF-6-WFV-L1A', 'v1', 'GF6_L1A_ENGINEERING');
