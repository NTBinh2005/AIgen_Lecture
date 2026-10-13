/** Types bản trình chiếu (presentation) — khớp DTO backend (đã gọi thử). */

export type PresentationStatus = 'DRAFT' | 'GENERATING' | 'READY' | 'PUBLISHED' | 'FAILED' | 'ARCHIVED'

/** Template backend chấp nhận (app.presentation.allowed-templates) */
export const PRESENTATION_TEMPLATES = [
  { id: 'standard', label: 'Tiêu chuẩn' },
  { id: 'academic', label: 'Học thuật' },
  { id: 'minimal', label: 'Tối giản' },
] as const

export interface PresentationSlide {
  title: string
  contentBlocks: string[]
  speakerNotes: string | null
  layout: string
  order: number
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
