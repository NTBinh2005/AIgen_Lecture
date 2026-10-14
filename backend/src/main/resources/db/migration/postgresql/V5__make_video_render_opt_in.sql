DO $$
BEGIN
    -- A fresh development database can run Flyway before Hibernate creates
    -- the application tables, so the migration must also be safe in that order.
    IF to_regclass('public.lectures') IS NOT NULL THEN
        ALTER TABLE lectures ALTER COLUMN video_status SET DEFAULT 'NOT_REQUESTED';

        -- Legacy PENDING rows cannot be distinguished from rows that were never
        -- requested, so keep them idle and require a new explicit render request.
        UPDATE lectures
        SET video_status = 'NOT_REQUESTED'
        WHERE video_status = 'PENDING' AND video_job_id IS NULL;
    END IF;
END $$;
