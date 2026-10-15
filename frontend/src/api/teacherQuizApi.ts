/**
 * API quiz cho Teacher: soạn quiz, publish, giao cho lớp, theo dõi và chấm điểm.
 */
import axiosInstance from './axiosInstance'
import { fetchAllPages } from './pagination'
import type { PageResponse } from './lectureApi'
import type { AttemptStart } from '@/types/student'
import type {
  AssignmentPayload, AssignmentUpdatePayload, ProgressAttempt, QuizDetail, QuizPayload, QuizStatus, QuizVersion, TeacherAssignment,
} from '@/types/teacher'

export async function getQuizzes(params?: { title?: string; status?: QuizStatus; page?: number; size?: number }) {
  const res = await axiosInstance.get<PageResponse<QuizDetail>>('/quizzes', { params })
  return res.data
}

export async function getQuiz(quizId: number): Promise<QuizDetail> {
  const res = await axiosInstance.get<QuizDetail>(`/quizzes/${quizId}`)
  return res.data
}

export async function createQuiz(payload: QuizPayload): Promise<QuizDetail> {
  const res = await axiosInstance.post<QuizDetail>('/quizzes', { ...payload, sourceType: 'MANUAL' })
  return res.data
}

/** Chỉ sửa được khi DRAFT/REVIEWED. Gửi `questions` sẽ thay toàn bộ danh sách câu hỏi. */
export async function updateQuiz(quizId: number, payload: QuizPayload): Promise<QuizDetail> {
  const res = await axiosInstance.patch<QuizDetail>(`/quizzes/${quizId}`, payload)
  return res.data
}

/** Tạo một version bất biến để giao cho lớp. */
export async function publishQuiz(quizId: number): Promise<QuizVersion> {
  const res = await axiosInstance.post<QuizVersion>(`/quizzes/${quizId}/publish`)
  return res.data
}

export async function getQuizVersions(quizId: number): Promise<QuizVersion[]> {
  const res = await axiosInstance.get<QuizVersion[]>(`/quizzes/${quizId}/versions`)
  return res.data
}

export async function closeQuiz(quizId: number): Promise<void> {
  await axiosInstance.patch(`/quizzes/${quizId}/close`)
}

export async function archiveQuiz(quizId: number): Promise<void> {
  await axiosInstance.patch(`/quizzes/${quizId}/archive`)
}

// ─── Tạo quiz bằng AI ─────────────────────────────────────────────────────────

export interface AiQuizJob {
  jobId: string
  quizId: number
  status: 'QUEUED' | 'PROCESSING' | 'DONE' | 'FAILED'
  questionCount?: number
  /** Lý do khi status = FAILED */
  error?: string
}

/** Tạo quiz DRAFT rồi để AI sinh câu hỏi nền từ nội dung bài giảng. jobId = quizId. */
export async function generateAiQuiz(title: string, sourceLectureId: number): Promise<AiQuizJob> {
  const res = await axiosInstance.post<AiQuizJob>('/quizzes/ai-generate', { title, sourceType: 'AI', sourceLectureId })
  return res.data
}

/** DONE khi quiz đã có câu hỏi; FAILED kèm `error` khi AI lỗi. */
export async function getAiQuizJob(jobId: string): Promise<AiQuizJob> {
  const res = await axiosInstance.get<AiQuizJob>(`/quizzes/ai-jobs/${jobId}`)
  return res.data
}

// ─── Giao bài & chấm điểm ─────────────────────────────────────────────────────

export async function createAssignment(payload: AssignmentPayload): Promise<TeacherAssignment> {
  const res = await axiosInstance.post<TeacherAssignment>('/quiz-assignments', payload)
  return res.data
}

/** Sửa bài đã giao. Đã có bài làm thì không đổi được thời lượng / số lượt. */
export async function updateAssignment(assignmentId: number, payload: AssignmentUpdatePayload): Promise<TeacherAssignment> {
  const res = await axiosInstance.patch<TeacherAssignment>(`/quiz-assignments/${assignmentId}`, payload)
  return res.data
}

/** Chỉ xoá được bài chưa có ai làm (đã có bài làm → 409, hãy đóng bài thay vì xoá). */
export async function deleteAssignment(assignmentId: number): Promise<void> {
  await axiosInstance.delete(`/quiz-assignments/${assignmentId}`)
}

export async function getClassAssignmentsForTeacher(classId: number): Promise<TeacherAssignment[]> {
  const res = await axiosInstance.get<TeacherAssignment[]>(`/quiz-assignments/teacher/class/${classId}`)
  return res.data
}

/** Danh sách lượt làm (tóm tắt, không kèm câu trả lời — xem chi tiết bằng getAttempt). */
export async function getAssignmentProgress(assignmentId: number): Promise<ProgressAttempt[]> {
  return fetchAllPages<ProgressAttempt>(`/quiz-assignments/${assignmentId}/progress`)
}

/** Chấm điểm từng câu (questionId → điểm). */
export async function gradeAttempt(attemptId: number, questionScores: Record<number, number>): Promise<void> {
  await axiosInstance.post(`/attempts/${attemptId}/grade`, { questionScores })
}

/** Chốt điểm → attempt chuyển GRADED. */
export async function finalizeAttempt(attemptId: number): Promise<void> {
  await axiosInstance.post(`/attempts/${attemptId}/finalize`)
}

/** Mở lại lượt làm cho học sinh. */
export async function reopenAttempt(attemptId: number): Promise<void> {
  await axiosInstance.post(`/attempts/${attemptId}/reopen`)
}

// ─── Làm thử, AI gợi ý điểm, xuất kết quả ─────────────────────────────────────

/** Giáo viên xem đề như học sinh (không lưu lượt làm). */
export async function previewAssignment(assignmentId: number): Promise<AttemptStart> {
  const res = await axiosInstance.post<AttemptStart>(`/quiz-assignments/${assignmentId}/preview`)
  return res.data
}

/** Yêu cầu AI gợi ý điểm cho một câu tự luận (chạy nền, 202). */
export async function requestAiSuggestion(attemptId: number, answerId: number): Promise<void> {
  await axiosInstance.post(`/attempts/${attemptId}/answers/${answerId}/ai-suggest`)
}

export type ExportType = 'GRADEBOOK_XLSX' | 'STUDENT_RESULT_XLSX' | 'STUDENT_RESULT_PDF'

export interface ExportJob {
  jobId: number
  type: ExportType
  status: 'QUEUED' | 'PROCESSING' | 'COMPLETED' | 'FAILED'
  progress: number
  fileUrl: string | null
  errorReport: string | null
}

export async function requestExport(type: ExportType, assignmentId: number, classId: number): Promise<ExportJob> {
  const res = await axiosInstance.post<ExportJob>('/exports', { type, assignmentId, classId })
  return res.data
}

export async function getExportJob(jobId: number): Promise<ExportJob> {
  const res = await axiosInstance.get<ExportJob>(`/exports/${jobId}`)
  return res.data
}

export const exportDownloadPath = (jobId: number) => `/exports/${jobId}/download`
