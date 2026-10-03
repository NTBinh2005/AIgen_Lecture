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

/** Vòng đời bài giảng (LECT-01..08) */
export type LectureStatus = 'DRAFT' | 'PROCESSING' | 'READY' | 'PUBLISHED' | 'FAILED' | 'ARCHIVED'

/** PRIVATE: chỉ chủ sở hữu/cộng tác viên thấy. CLASS: phân phối cho lớp học. */
export type LectureAccessScope = 'PRIVATE' | 'CLASS'

export interface LectureCreatePayload {
  title: string
  /** Nội dung văn bản — backend bắt buộc có khi publish */
  originalSource?: string
  accessScope?: LectureAccessScope
  slides: SlideDto[]
}

export interface LectureResponse {
  lectureId: number
  businessId: string
  title: string
  originalSource: string | null
  teacherName: string
  teacherId: number
  status: LectureStatus
  accessScope: LectureAccessScope
  currentVersionId: string | null
  publishedVersionId: string | null
  latestGenerationJobId: string | null
  videoStatus: VideoStatus
  videoUrl: string | null
  createdAt: string
  canEdit: boolean
  canPublish: boolean
}

export interface LectureVersionResponse {
  versionId: string
  lectureId: number
  versionNumber: number
  title: string
  content: string | null
  /** JSON string của SlideDto[] */
  slideContent: string | null
  status: string
  aiGenerated: boolean
}

export type GenerationJobStatus = 'QUEUED' | 'PROCESSING' | 'DONE' | 'FAILED' | 'CANCELLED'

export interface GenerationJobResponse {
  jobId: string
  status: GenerationJobStatus
  progress: number
  currentStep: string | null
  safeErrorCode: string | null
  safeErrorMessage: string | null
  targetId: string | null
}

export interface LectureAsyncResponse {
  lectureId: number
  lectureBusinessId: string
  jobId: string
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
 * Tạo bài giảng thủ công từ slides — backend lưu ở trạng thái DRAFT (201).
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
  title?: string
  content?: string
  accessScope?: LectureAccessScope
  /** Ghi vào slideContent của version nháp (bản đã publish sẽ được tách thành nháp mới) */
  slides?: SlideDto[]
}

/**
 * Sửa tiêu đề / nội dung văn bản / phạm vi truy cập / slides.
 */
export async function updateLecture(
  lectureId: number,
  payload: LectureUpdatePayload,
): Promise<LectureResponse> {
  const res = await axiosInstance.patch<LectureResponse>(
    `/lectures/${lectureId}`,
    payload,
  )
  return res.data
}

/**
 * Xuất bản phiên bản hiện tại của bài giảng (cần title, content, accessScope).
 */
export async function publishLecture(lectureId: number): Promise<LectureResponse> {
  const res = await axiosInstance.post<LectureResponse>(`/lectures/${lectureId}/publish`)
  return res.data
}

/**
 * Lấy một phiên bản bài giảng (chứa slideContent).
 */
export async function getLectureVersion(
  lectureId: number,
  versionId: string,
): Promise<LectureVersionResponse> {
  const res = await axiosInstance.get<LectureVersionResponse>(
    `/lectures/${lectureId}/versions/${versionId}`,
  )
  return res.data
}

/**
 * Parse slideContent (JSON string) → SlideDto[]. Trả về [] nếu rỗng/lỗi.
 */
export function parseSlideContent(slideContent: string | null | undefined): SlideDto[] {
  if (!slideContent) return []
  try {
    const parsed = JSON.parse(slideContent)
    return Array.isArray(parsed) ? parsed : []
  } catch {
    return []
  }
}

/**
 * Tích hợp LLM (bất đồng bộ): upload file (PDF, DOCX, PPTX) → backend tạo
 * lecture + generation job và trả về ngay. Theo dõi tiến độ bằng getGenerationJob.
 */
export async function startGenerationFromFile(
  file: File,
  title: string,
  accessScope: LectureAccessScope = 'PRIVATE',
): Promise<LectureAsyncResponse> {
  const formData = new FormData()
  formData.append('file', file)
  const res = await axiosInstance.post<LectureAsyncResponse>(
    `/lectures/from-file`,
    formData,
    {
      params: { title, accessScope },
      // Idempotency-Key: gửi lại cùng request (do mạng chập chờn) không tạo job trùng
      headers: { 'Idempotency-Key': crypto.randomUUID() },
      timeout: 60000, // chỉ là thời gian upload, AI chạy nền
    },
  )
  return res.data
}

export async function getGenerationJob(jobId: string): Promise<GenerationJobResponse> {
  const res = await axiosInstance.get<GenerationJobResponse>(`/generation-jobs/${jobId}`)
  return res.data
}

export async function retryGenerationJob(jobId: string): Promise<GenerationJobResponse> {
  const res = await axiosInstance.post<GenerationJobResponse>(`/generation-jobs/${jobId}/retry`)
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

