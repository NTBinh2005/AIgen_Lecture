-- ============================================================
-- Seed data cho môi trường development/test
-- Password được hash bằng BCrypt (strength=10)
--
-- Credentials:
--   teacher@edumind.dev  / Teacher123!
--   student@edumind.dev  / Student123!
--   admin@edumind.dev    / Admin1234!
-- ============================================================

INSERT INTO users (role, name, email, password_hash, status, auth_provider, created_at)
VALUES
  ('TEACHER', 'Nguyễn Demo Teacher', 'teacher@edumind.dev',
   '$2b$10$Ail1nAo4FpqfHTzWw0Uj4.TcGjZXteLuR5EnpBGTwwoPVuOMF3rvi',
   'ACTIVE', 'LOCAL', NOW()),
  ('STUDENT', 'Trần Demo Student', 'student@edumind.dev',
   '$2b$10$fBJEqFBkg6lFty3btNoVkujryqSAUTn..Q6DyZ/QbknJjS6XxFC.O',
   'ACTIVE', 'LOCAL', NOW()),
  ('ADMIN', 'Admin EduMind', 'admin@edumind.dev',
   '$2b$10$mp3BRURQvkevLTbY2BwkZeYGITaHF0p/P/E6P4/PtMDQ1lKWSibHq',
   'ACTIVE', 'LOCAL', NOW())
ON CONFLICT (email) DO NOTHING;
