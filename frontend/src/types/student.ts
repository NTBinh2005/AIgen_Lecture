/**
 * Types cho luồng Student — khớp với DTO backend (đã gọi thử trên dev).
 * Thời gian backend trả về là LocalDateTime không kèm timezone (giờ server).
 */

// ─── Lớp học & ghi danh ──────────────────────────────────────────────────────

export type ClassStatus = 'DRAFT' | 'ACTIVE' | 'CLOSED' | 'ARCHIVED'
export type EnrollmentStatus = 'ACTIVE' | 'SUSPENDED' | 'COMPLETED' | 'CANCELLED'

/** GET /students/me/classes, POST /classes/self-enroll */
export interface Enrollment {
  classId: number
  className: string
  studentId: number
  studentName: string
  enrolledAt: string
  status: EnrollmentStatus
}

/** GET /classes/{classId} */
export interface ClassDetail {
  classId: number
  teacherId: number
  teacherName: string
  className: string
  classCode: string
  semester: string | null
  startsAt: string | null
  endsAt: string | null
  maxStudents: number | null
  description: string | null
  status: ClassStatus
  createdAt?: string
}

/** GET /classes/{classId}/lectures */
export interface ClassLecture {
  lectureId: number
  title: string
  publishedAt: string | null
  assignedAt: string
}

// ─── Quiz / bài làm ──────────────────────────────────────────────────────────

export type StudentAssignmentStatus = 'TODO' | 'IN_PROGRESS' | 'COMPLETED' | 'OVERDUE'
export type AttemptStatus = 'IN_PROGRESS' | 'SUBMITTED' | 'AUTO_SUBMITTED' | 'REVIEW_REQUIRED' | 'GRADED'
export type QuestionType = 'MCQ_SINGLE' | 'MCQ_MULTI' | 'TRUE_FALSE' | 'SHORT_ANSWER' | 'ESSAY'

/** GET /quiz-assignments/student/class/{classId} */
export interface StudentAssignment {
  assignmentId: number
  quizTitle: string
  classId: number
  openAt: string | null
  closeAt: string | null
  durationMinutes: number | null
  maxAttempts: number | null
  status: 'OPEN' | 'CLOSED'
  usedAttempts: number
  studentStatus: StudentAssignmentStatus
}

export interface AttemptQuestion {
  questionId: number
  questionType: QuestionType
  questionText: string
  options: string[] | null
  points: number
  orderIndex: number
}

/** POST /quiz-assignments/{id}/start */
export interface AttemptStart {
  attemptId: number
  assignmentId: number
  attemptNo: number
  status: AttemptStatus
  startedAt: string
  deadlineAt: string | null
  questions: AttemptQuestion[]
}

export interface AttemptAnswer {
  answerId: number
  questionId: number
  response: string | null
  answerVersion: number
  isCorrect: boolean | null
  correctAnswer: string | null
  explanation: string | null
  pointsAwarded: number | null
  /** Điểm giáo viên chấm lại (nếu có) */
  teacherFinalScore?: number | null
}

/** GET /attempts/{id}, POST /attempts/{id}/submit, GET /quiz-assignments/{id}/my-result */
export interface Attempt {
  attemptId: number
  assignmentId: number
  attemptNo: number
  status: AttemptStatus
  startedAt: string
  deadlineAt: string | null
  submittedAt: string | null
  finalScore: number | null
  objectiveScore: number | null
  answers: AttemptAnswer[]
  /** Có khi giáo viên xem bài nộp */
  studentId?: number
  studentName?: string
}

// ─── Buổi học live & lịch ────────────────────────────────────────────────────

export type LiveSessionStatus = 'SCHEDULED' | 'OPEN' | 'LIVE' | 'ENDED' | 'CANCELLED'
export type LiveSessionType = 'ONLINE' | 'OFFLINE'

export interface LiveSession {
  sessionId: number
  classId: number
  className: string
  createdByName: string
  title: string
  type: LiveSessionType
  status: LiveSessionStatus
  startsAt: string
  endsAt: string
  location: string | null
  meetingUrl: string | null
  cancelReason: string | null
}

/** POST /live-sessions/{id}/join */
export interface JoinToken {
  token: string
  meetingUrl: string | null
  expiresAt: string
}

export type ScheduleType = 'LECTURE' | 'LAB' | 'EXAM' | 'MEETING' | 'OTHER'

export interface Schedule {
  scheduleId: number
  classId: number
  startsAt: string
  endsAt: string
  type: ScheduleType
  recurrence: string | null
  timezone: string
}

// ─── Thông báo & tài khoản ───────────────────────────────────────────────────

export interface AppNotification {
  id: number
  title: string
  message: string
  type: string
  /** Record Java `isRead` có thể được Jackson serialize thành `read` — đọc cả hai. */
  isRead?: boolean
  read?: boolean
  referenceId: string | null
  createdAt: string
}

export interface Profile {
  userId: number
  role: string
  name: string
  email: string
  status: string
  createdAt: string
}
