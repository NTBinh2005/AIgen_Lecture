-- Đồng bộ check constraint của classes và class_students theo đúng Enum trong code Java:
-- ClassStatus: DRAFT, ACTIVE, CLOSED, ARCHIVED
-- EnrollmentStatus: ACTIVE, SUSPENDED, COMPLETED, CANCELLED
-- Do Hibernate ddl-auto=update không tự cập nhật check constraint khi Enum thêm giá trị mới.

ALTER TABLE classes DROP CONSTRAINT IF EXISTS classes_status_check;
ALTER TABLE classes ADD CONSTRAINT classes_status_check
  CHECK (status IN ('DRAFT', 'ACTIVE', 'CLOSED', 'ARCHIVED'));

ALTER TABLE classes DROP CONSTRAINT IF EXISTS uk_classes_class_code;

ALTER TABLE class_students DROP CONSTRAINT IF EXISTS class_students_status_check;
ALTER TABLE class_students ADD CONSTRAINT class_students_status_check
  CHECK (status IN ('ACTIVE', 'SUSPENDED', 'COMPLETED', 'CANCELLED'));
