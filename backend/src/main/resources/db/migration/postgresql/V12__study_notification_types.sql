DO $$ BEGIN
IF to_regclass('notifications') IS NOT NULL THEN
    ALTER TABLE notifications DROP CONSTRAINT IF EXISTS notifications_type_check;
END IF;
END $$;
