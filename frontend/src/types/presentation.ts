/** Types bản trình chiếu (presentation) — khớp DTO backend (đã gọi thử). */

export type PresentationStatus = 'DRAFT' | 'GENERATING' | 'READY' | 'PUBLISHED' | 'FAILED' | 'ARCHIVED'

/** Template backend chấp nhận (app.presentation.allowed-templates) */
export const PRESENTATION_TEMPLATES = [
  { id: 'standard', label: 'Tiêu chuẩn' },
  { id: 'academic', label: 'Học thuật' },
  { id: 'minimal', label: 'Tối giản' },
] as const

/** Đối tượng người học (slide-ai điều chỉnh độ khó và cách diễn đạt theo đối tượng) */
export const PRESENTATION_AUDIENCES = [
  { id: 'THCS', label: 'THCS' },
  { id: 'THPT', label: 'THPT' },
  { id: 'DAI_HOC', label: 'Đại học' },
  { id: 'DI_LAM', label: 'Người đi làm' },
] as const

export type PresentationAudience = (typeof PRESENTATION_AUDIENCES)[number]['id']

/** Số slide AI tạo: backend giới hạn 3–30, mặc định 10 */
export const SLIDE_COUNT = { min: 3, max: 30, default: 10 } as const

/** Tham số gửi kèm khi tạo bằng AI (backend lưu vào generationParameters) */
export interface PresentationGenerationParams {
  slideCount: number
  audience: PresentationAudience
}

export interface PresentationSlide {
  title: string
  contentBlocks: string[]
  speakerNotes: string | null
  layout: string
  order: number
  /** Slide gốc của slide-ai (bố cục COMPARE, TABLE...); không có với slide thường. Gửi lại nguyên vẹn khi lưu. */
  data?: Record<string, unknown> | null
}

export interface PresentationVersion {
  versionId: string
  versionNumber: number
  title: string
  slides: PresentationSlide[]
  status: string
  restoredFromVersionId: string | null
  createdBy: number
  createdAt: string
  publishedAt: string | null
}

export interface Presentation {
  presentationId: string
  ownerId: number
  title: string
  sourceType: 'LECTURE_VERSION' | 'ASSET'
  templateId: string
  status: PresentationStatus
  currentVersionId: string | null
  publishedVersionId: string | null
  currentVersionNumber: number
  createdAt: string
  updatedAt: string
  publishedAt: string | null
  currentVersion: PresentationVersion | null
  canEdit: boolean
  canPublish: boolean
  canArchive: boolean
  canExport: boolean
}

/** Bản trình chiếu đã giao cho lớp (phiên bản đã xuất bản); assignedAt null khi học sinh xem chi tiết */
export interface ClassPresentation {
  presentationId: string
  title: string
  templateId: string
  slideCount: number
  publishedAt: string | null
  assignedAt: string | null
}

export interface PresentationAsync {
  presentationId: string
  jobId: string
}

export interface PresentationExport {
  exportId: string
  presentationId: string
  versionId: string
  fileName: string
  contentType: string
  byteSize: number
  createdAt: string
}
