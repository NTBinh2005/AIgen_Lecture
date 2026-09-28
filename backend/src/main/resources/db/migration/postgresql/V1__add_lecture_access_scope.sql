-- Upgrade databases created before Lecture.accessScope was introduced.
-- On a fresh database Hibernate creates the table, so this is a no-op.
DO $$
BEGIN
    IF to_regclass('lectures') IS NOT NULL THEN
        ALTER TABLE lectures
            ADD COLUMN IF NOT EXISTS access_scope VARCHAR(20);

        UPDATE lectures
        SET access_scope = 'PRIVATE'
        WHERE access_scope IS NULL;

        ALTER TABLE lectures
            ALTER COLUMN access_scope SET DEFAULT 'PRIVATE',
            ALTER COLUMN access_scope SET NOT NULL;
    END IF;
END
$$;
