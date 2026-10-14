-- V5 changed the default video status to NOT_REQUESTED but left the legacy
-- check constraint in place on databases upgraded from the old schema.
DO $$
DECLARE
    constraint_row RECORD;
BEGIN
    IF to_regclass('public.lectures') IS NULL THEN
        RETURN;
    END IF;

    FOR constraint_row IN
        SELECT conname
        FROM pg_constraint
        WHERE conrelid = 'public.lectures'::regclass
          AND contype = 'c'
          AND pg_get_constraintdef(oid) LIKE '%video_status%'
    LOOP
        EXECUTE format(
            'ALTER TABLE public.lectures DROP CONSTRAINT %I',
            constraint_row.conname
        );
    END LOOP;

    ALTER TABLE public.lectures
        ADD CONSTRAINT lectures_video_status_check
        CHECK (video_status IN ('NOT_REQUESTED', 'PENDING', 'PROCESSING', 'DONE', 'FAILED'));
END $$;
