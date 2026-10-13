/**
 * API bản trình chiếu cho Teacher: AI sinh slide từ bài giảng hoặc file, sửa, phiên bản, xuất bản, xuất PPTX.
 */
import axiosInstance from './axiosInstance'
import type { PageResponse } from './lectureApi'
import type {
  Presentation, PresentationAsync, PresentationExport, PresentationSlide, PresentationVersion,
} from '@/types/presentation'

export async function getPresentations(params?: { title?: string; page?: number; size?: number }) {
  const res = await axiosInstance.get<PageResponse<Presentation>>('/presentations', { params })
  return res.data
}

export async function getPresentation(id: string): Promise<Presentation> {
  const res = await axiosInstance.get<Presentation>(`/presentations/${id}`)
  return res.data
}

/** Tạo từ một phiên bản bài giảng. Trả về ngay, AI chạy nền — theo dõi bằng getGenerationJob(jobId). */
export async function createPresentationFromLecture(
  lectureVersionId: string,
  title: string,
  templateId: string,
): Promise<PresentationAsync> {
  const res = await axiosInstance.post<PresentationAsync>(
    '/presentations/from-lecture',
    { lectureVersionId, title, templateId },
    { headers: { 'Idempotency-Key': crypto.randomUUID() } },
  )
  return res.data
}

/** Tạo từ file PDF/DOCX/PPTX (tối đa 20MB). */
export async function createPresentationFromFile(file: File, title: string, templateId: string): Promise<PresentationAsync> {
  const formData = new FormData()
  formData.append('file', file)
  const res = await axiosInstance.post<PresentationAsync>('/presentations/from-file', formData, {
    params: { title, templateId },
    headers: { 'Idempotency-Key': crypto.randomUUID() },
    timeout: 60_000,
  })
  return res.data
}

/** Lưu tiêu đề + toàn bộ slide thành một phiên bản nháp mới. */
export async function updatePresentation(id: string, title: string, slides: PresentationSlide[]): Promise<Presentation> {
  const res = await axiosInstance.patch<Presentation>(`/presentations/${id}`, { title, slides })
  return res.data
}

export async function getPresentationVersions(id: string): Promise<PresentationVersion[]> {
  const res = await axiosInstance.get<PresentationVersion[]>(`/presentations/${id}/versions`)
  return res.data
}

export async function restorePresentationVersion(id: string, versionId: string): Promise<Presentation> {
  const res = await axiosInstance.post<Presentation>(`/presentations/${id}/versions/${versionId}/restore`)
  return res.data
}

export async function publishPresentation(id: string): Promise<Presentation> {
  const res = await axiosInstance.post<Presentation>(`/presentations/${id}/publish`)
  return res.data
}

/** Sinh file PPTX cho một phiên bản; tải bằng downloadFromApi(exportContentPath(...)). */
export async function exportPresentation(id: string, versionId: string): Promise<PresentationExport> {
  const res = await axiosInstance.post<PresentationExport>(`/presentations/${id}/versions/${versionId}/exports`, null, { timeout: 60_000 })
  return res.data
}

export const exportContentPath = (id: string, exportId: string) => `/presentations/${id}/exports/${exportId}/content`

export async function archivePresentation(id: string): Promise<void> {
  await axiosInstance.delete(`/presentations/${id}`)
}

// ─── Cộng tác viên (backend chỉ trả danh sách userId) ─────────────────────────

export async function getPresentationCollaborators(id: string): Promise<number[]> {
  const res = await axiosInstance.get<number[]>(`/presentations/${id}/collaborators`)
  return res.data
}

export async function addPresentationCollaborator(id: string, userId: number): Promise<void> {
  await axiosInstance.post(`/presentations/${id}/collaborators`, { userId })
}

export async function removePresentationCollaborator(id: string, userId: number): Promise<void> {
  await axiosInstance.delete(`/presentations/${id}/collaborators/${userId}`)
}
