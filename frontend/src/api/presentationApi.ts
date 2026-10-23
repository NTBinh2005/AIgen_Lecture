/**
 * API bản trình chiếu cho Teacher: AI sinh slide từ bài giảng hoặc file, sửa, phiên bản, xuất bản, xuất PPTX.
 */
import axiosInstance from './axiosInstance'
import type { CollaboratorUser, PageResponse } from './lectureApi'
import type {
  ClassPresentation, Presentation, PresentationAsync, PresentationExport, PresentationGenerationParams, PresentationSlide, PresentationVersion,
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
  parameters?: PresentationGenerationParams,
): Promise<PresentationAsync> {
  const res = await axiosInstance.post<PresentationAsync>(
    '/presentations/from-lecture',
    { lectureVersionId, title, templateId, parameters },
    { headers: { 'Idempotency-Key': crypto.randomUUID() } },
  )
  return res.data
}

/** Tạo từ file PDF/DOCX/PPTX (tối đa 20MB). */
export async function createPresentationFromFile(
  file: File,
  title: string,
  templateId: string,
  parameters?: PresentationGenerationParams,
): Promise<PresentationAsync> {
  const formData = new FormData()
  formData.append('file', file)
  const res = await axiosInstance.post<PresentationAsync>('/presentations/from-file', formData, {
    // Backend nhận parameters dạng chuỗi JSON (@RequestParam)
    params: { title, templateId, parameters: parameters ? JSON.stringify(parameters) : undefined },
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

/** HTML trình chiếu (dựng bởi slide-ai-service) của phiên bản hiện tại hoặc versionId. Hiển thị bằng iframe srcDoc có sandbox. */
export function renderPresentation(id: string, versionId?: string): Promise<string> {
  return getHtml(`/presentations/${id}/render`, versionId ? { versionId } : undefined)
}

async function getHtml(url: string, params?: Record<string, string>): Promise<string> {
  try {
    const res = await axiosInstance.get<string>(url, { params, responseType: 'text', timeout: 60_000 })
    return res.data
  } catch (error) {
    // responseType 'text' nên body lỗi là chuỗi JSON: parse lại để getErrorMessage đọc được message
    const response = (error as { response?: { data?: unknown } }).response
    if (response && typeof response.data === 'string') {
      try { response.data = JSON.parse(response.data) } catch { /* giữ nguyên */ }
    }
    throw error
  }
}

export async function archivePresentation(id: string): Promise<void> {
  await axiosInstance.delete(`/presentations/${id}`)
}

// ─── Giao cho lớp (giống bài giảng) ──────────────────────────────────────────

/** Bản trình chiếu đã giao cho lớp; giáo viên và học sinh của lớp đều gọi được. */
export async function getClassPresentations(classId: number): Promise<ClassPresentation[]> {
  const res = await axiosInstance.get<ClassPresentation[]>(`/classes/${classId}/presentations`)
  return res.data
}

/** Chỉ giao được bản đã xuất bản; học sinh xem phiên bản đã xuất bản. */
export async function assignPresentationToClass(classId: number, presentationId: string): Promise<ClassPresentation> {
  const res = await axiosInstance.post<ClassPresentation>(`/classes/${classId}/presentations`, { presentationId })
  return res.data
}

export async function unassignPresentationFromClass(classId: number, presentationId: string): Promise<void> {
  await axiosInstance.delete(`/classes/${classId}/presentations/${presentationId}`)
}

/** Học sinh: thông tin bản trình chiếu được giao (không kèm nội dung slide). */
export async function getStudentPresentation(id: string): Promise<ClassPresentation> {
  const res = await axiosInstance.get<ClassPresentation>(`/student/presentations/${id}`)
  return res.data
}

/** Học sinh: HTML trình chiếu của phiên bản đã xuất bản. */
export function renderStudentPresentation(id: string): Promise<string> {
  return getHtml(`/student/presentations/${id}/render`)
}

// ─── Cộng tác viên (backend chỉ trả danh sách userId) ─────────────────────────

export async function getPresentationCollaborators(id: string): Promise<CollaboratorUser[]> {
  const res = await axiosInstance.get<CollaboratorUser[]>(`/presentations/${id}/collaborators/details`)
  return res.data
}

export async function addPresentationCollaborator(id: string, userId: number): Promise<void> {
  await axiosInstance.post(`/presentations/${id}/collaborators`, { userId })
}

export async function removePresentationCollaborator(id: string, userId: number): Promise<void> {
  await axiosInstance.delete(`/presentations/${id}/collaborators/${userId}`)
}
