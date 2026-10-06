/**
 * Types cho luồng Teacher — khớp DTO backend (đã gọi thử trên dev).
 * Các type dùng chung với Student (lớp, enrollment, live, lịch, attempt) import từ ./student.
 */
import type { Attempt, ClassStatus, Enrollment, QuestionType, ScheduleType, LiveSessionType } from './student'

// ─── Lớp học ──────────────────────────────────────────────────────────────────

export interface ClassFormPayload {
  className: string
  classCode: string
  semester?: string | null
  startsAt?: string | null
  endsAt?: string | null
  maxStudents?: number | null
  description?: string | null
}

export interface ClassUpdatePayload extends Partial<ClassFormPayload> {
  status?: ClassStatus
}

/** POST /classes/{id}/students/bulk — kết quả từng dòng */
export interface BulkEnrollmentItem {
  studentId: number
  success: boolean
  enrollment: Enrollment | null
  error: string | null
}

// ─── Quiz ─────────────────────────────────────────────────────────────────────

export type QuizStatus = 'DRAFT' | 'REVIEWED' | 'PUBLISHED' | 'CLOSED' | 'ARCHIVED'
export type ResultPolicy = 'AFTER_SUBMISSION' | 'AFTER_GRADING' | 'AFTER_CLOSE_AT' | 'NEVER'

export interface QuizQuestion {
  questionId?: number | null
  questionType: QuestionType
  questionText: string
  options?: string[] | null
  /** Chữ cái đáp án: "B" hoặc "A,C"; câu tự luận ngắn là đáp án mẫu */
  correctAnswer?: string | null
  points: number
  explanation?: string | null
  orderIndex?: number
}

export interface QuizDetail {
  quizId: number
  title: string
  sourceType: 'MANUAL' | 'AI'
  sourceLectureId: number | null
  status: QuizStatus
  createdAt: string
  questions: QuizQuestion[]
}

export interface QuizVersion {
  versionId: number
  quizId: number
  versionNo: number
  publishedAt: string
  /** JSON string của QuizQuestion[] */
  questionsSnapshot: string
}

export interface QuizPayload {
  title: string
  questions: QuizQuestion[]
}

/** GET /quiz-assignments/teacher/class/{classId} */
export interface TeacherAssignment {
  assignmentId: number
  quizVersionId: number
  classId: number
  openAt: string | null
  closeAt: string | null
  durationMinutes: number | null
  maxAttempts: number | null
  resultPolicy: ResultPolicy
  status: 'OPEN' | 'CLOSED'
  quizId?: number
  quizTitle?: string
}

export interface AssignmentPayload {
  quizVersionId: number
  classId: number
  openAt: string
  closeAt: string
  durationMinutes?: number | null
  maxAttempts?: number | null
  resultPolicy: ResultPolicy
}

/** Một lượt làm trong GET /quiz-assignments/{id}/progress (kèm tên học sinh và câu trả lời) */
export type ProgressAttempt = Attempt

// ─── Live & lịch ──────────────────────────────────────────────────────────────

export interface LiveSessionPayload {
  classId: number
  title: string
  type: LiveSessionType
  startsAt: string
  endsAt: string
  location?: string | null
  meetingUrl?: string | null
}

export interface QrToken {
  tokenId: number
  sessionId: number
  code: string
  expiresAt: string
}

export interface AttendanceSummary {
  studentId: number
  studentName: string
  totalSeconds: number
  present: boolean
  source: string
}

export interface SchedulePayload {
  classId: number
  startsAt: string
  endsAt: string
  type: ScheduleType
  recurrence?: string | null
  timezone: string
}
