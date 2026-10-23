DO $$ BEGIN
IF to_regclass('users') IS NOT NULL THEN
-- Preserve all identities and their original conflicting emails. An administrator must
-- assign a distinct verified email to each ambiguous account before it can log in.
ALTER TABLE users ADD COLUMN IF NOT EXISTS email_ambiguous BOOLEAN NOT NULL DEFAULT FALSE;
UPDATE users SET email_ambiguous = TRUE
WHERE lower(trim(email)) IN (SELECT lower(trim(email)) FROM users GROUP BY lower(trim(email)) HAVING count(*) > 1);
UPDATE users SET email = lower(trim(email)) WHERE email_ambiguous = FALSE;
CREATE UNIQUE INDEX IF NOT EXISTS uk_users_normalized_email
    ON users (lower(trim(email))) WHERE email_ambiguous = FALSE;
END IF;
END $$;
