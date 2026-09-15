/**
 * API client cho Lecture endpoints — gọi backend Spring Boot.
 * Không động vào auth/RBAC code hiện có.
 */
import axiosInstance from './axiosInstance'

// ─── Types ────────────────────────────────────────────────────────────────────

export type VideoStatus = 'PENDING' | 'PROCESSING' | 'DONE' | 'FAILED'

export interface SlideDto {
  title: string
  bulletPoints: string[]
  narrationText: string
  imagePrompt?: string
}

export interface QuizDto {
  questionText: string
  options: string[]
  /** Ký tự A/B/C/D — ẩn với Student khi GET quizzes, hiện sau khi submit */
  correctAnswer: string
}

export interface LectureCreatePayload {
  title: string
  originalSource?: string
  slides: SlideDto[]
  /** Optional — null/empty nếu không có quiz */
  quizzes?: QuizDto[]
}

export interface LectureResponse {
  lectureId: number
  title: string
  originalSource: string | null
  teacherName: string
  teacherId: number
  videoStatus: VideoStatus
  videoUrl: string | null
  createdAt: string
}

export interface VideoStatusResponse {
  lectureId: number
  videoStatus: VideoStatus
  videoUrl: string | null
  errorMessage: string | null
}

/**
 * Response khi GET /api/lectures/{id}/quizzes.
 * correctAnswer = null khi Student gọi (ẩn để chống gian lận).
 */
export interface QuizResponse {
  elementId: number
  questionText: string
  options: string[]
  orderIndex: number
  correctAnswer: string | null
}

export interface SubmitAnswerRequest {
  elementId: number
  submittedAnswer: string  // 'A' | 'B' | 'C' | 'D'
  timeSpent?: number       // giây
}

export interface SubmitAnswerResponse {
  logId: number
  elementId: number
  submittedAnswer: string
  isCorrect: boolean
  correctAnswer: string
  isFirstAttempt: boolean
}

export interface PageResponse<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}

// ─── API Calls ────────────────────────────────────────────────────────────────

/**
 * Tạo mới bài giảng và trigger video generation.
 * Backend trả về 202 Accepted (video chưa xong ngay).
 */
export async function createLecture(
  payload: LectureCreatePayload,
): Promise<LectureResponse> {
  const res = await axiosInstance.post<LectureResponse>(
    `/lectures`,
    payload,
  )
  return res.data
}

/**
 * Lấy danh sách bài giảng của teacher (có phân trang, filter theo title).
 */
export async function getLectures(
  params?: { title?: string; page?: number; size?: number; sort?: string },
): Promise<PageResponse<LectureResponse>> {
  const res = await axiosInstance.get<PageResponse<LectureResponse>>('/lectures', {
    params: { ...params },
  })
  return res.data
}

/**
 * Lấy danh sách bài giảng cho Student (toàn bộ bài giảng trên hệ thống).
 */
export async function getStudentLectures(
  params?: { title?: string; page?: number; size?: number; sort?: string },
): Promise<PageResponse<LectureResponse>> {
  const res = await axiosInstance.get<PageResponse<LectureResponse>>('/lectures/student', {
    params: { ...params },
  })
  return res.data
}

/**
 * Lấy chi tiết bài giảng theo ID.
 */
export async function getLecture(
  lectureId: number,
): Promise<LectureResponse> {
  const res = await axiosInstance.get<LectureResponse>(`/lectures/${lectureId}`)
  return res.data
}

/**
 * Poll trạng thái video render của một lecture.
 * FE gọi mỗi 3 giây cho tới khi status = DONE hoặc FAILED.
 */
export async function getVideoStatus(lectureId: number): Promise<VideoStatusResponse> {
  const res = await axiosInstance.get<VideoStatusResponse>(`/lectures/${lectureId}/video-status`)
  return res.data
}

/**
 * Soft-delete bài giảng.
 */
export async function deleteLecture(lectureId: number): Promise<void> {
  await axiosInstance.delete(`/lectures/${lectureId}`)
}

export interface LectureUpdatePayload {
  title: string
}

/**
 * Sửa tên bài giảng.
 */
export async function updateLecture(
  lectureId: number,
  payload: LectureUpdatePayload,
): Promise<LectureResponse> {
  const res = await axiosInstance.put<LectureResponse>(
    `/lectures/${lectureId}`,
    payload,
  )
  return res.data
}

/**
 * Tích hợp LLM: Upload file (PDF, DOCX, PPTX) -> sinh kịch bản bài giảng.
 * Trả về cả slides và quizzes trong 1 lần gọi API.
 * quizzes có thể null/empty nếu Gemini không sinh ra.
 */
export async function generateFromFile(file: File): Promise<{ slides: SlideDto[]; quizzes?: QuizDto[] }> {
  const formData = new FormData()
  formData.append('file', file)
  const res = await axiosInstance.post<{ slides: SlideDto[]; quizzes?: QuizDto[] }>(
    `/lectures/generate-from-file`,
    formData,
    {
      // Timeout dài hơn vì LLM có thể mất đến 20s
      timeout: 35000,
    }
  )
  return res.data
}

/**
 * Lấy danh sách câu hỏi trắc nghiệm của một bài giảng.
 * - Teacher: nhận correctAnswer
 * - Student: correctAnswer = null (backend ẩn)
 */
export async function getQuizzes(lectureId: number): Promise<QuizResponse[]> {
  const res = await axiosInstance.get<QuizResponse[]>(`/lectures/${lectureId}/quizzes`)
  return res.data
}

/**
 * Student nộp câu trả lời trắc nghiệm.
 * Backend tự kiểm tra đúng/sai và xác định isFirstAttempt (BR-06).
 */
export async function submitAnswer(payload: SubmitAnswerRequest): Promise<SubmitAnswerResponse> {
  const res = await axiosInstance.post<SubmitAnswerResponse>('/interactions', payload)
  return res.data
}

// ─── Comment / Q&A Types ─────────────────────────────────────────────────────

export interface CommentResponse {
  commentId: number
  parentCommentId: number | null
  userId: number
  userName: string
  userRole: 'TEACHER' | 'STUDENT' | 'ADMIN'
  content: string
  createdAt: string
  replies: CommentResponse[]
}

export interface CommentCreateRequest {
  content: string
  parentCommentId?: number | null
}

// ─── Comment API Calls ────────────────────────────────────────────────────────

/**
 * Lấy danh sách bình luận Q&A của một bài giảng (dạng nested thread).
 */
export async function getComments(lectureId: number): Promise<CommentResponse[]> {
  const res = await axiosInstance.get<CommentResponse[]>(`/lectures/${lectureId}/comments`)
  return res.data
}

/**
 * Thêm bình luận mới hoặc reply vào một bài giảng.
 */
export async function addComment(
  lectureId: number,
  payload: CommentCreateRequest,
): Promise<CommentResponse> {
  const res = await axiosInstance.post<CommentResponse>(
    `/lectures/${lectureId}/comments`,
    payload,
  )
  return res.data
}

/**
 * Xóa bình luận (chủ sở hữu hoặc Admin).
 */
export async function deleteComment(lectureId: number, commentId: number): Promise<void> {
  await axiosInstance.delete(`/lectures/${lectureId}/comments/${commentId}`)
}

