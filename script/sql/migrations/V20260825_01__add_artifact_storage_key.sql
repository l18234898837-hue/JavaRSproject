-- Persist the controlled path used to locate a registered task artifact.
ALTER TABLE rs_artifact ADD COLUMN storage_key varchar(2048);

-- Existing records predate managed task storage. Keep them readable as legacy metadata;
-- new registrations always receive a concrete controlled key from TaskArtifactStore.
UPDATE rs_artifact
SET storage_key = 'legacy/' || task_id || '/' || COALESCE(stage_id::text, artifact_id::text)
WHERE storage_key IS NULL;

ALTER TABLE rs_artifact ALTER COLUMN storage_key SET NOT NULL;
