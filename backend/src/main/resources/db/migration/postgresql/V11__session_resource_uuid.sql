DO $$ BEGIN
IF to_regclass('session_resources') IS NOT NULL THEN
-- Old numeric references never pointed to UUID resources. Preserve them for manual repair.
CREATE TABLE IF NOT EXISTS legacy_session_resource_links AS SELECT * FROM session_resources WHERE FALSE;
INSERT INTO legacy_session_resource_links SELECT * FROM session_resources WHERE resource_id::text !~* '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$';
DELETE FROM session_resources WHERE resource_id::text !~* '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$';
ALTER TABLE session_resources ALTER COLUMN resource_id TYPE UUID USING resource_id::text::uuid;
END IF;
END $$;
