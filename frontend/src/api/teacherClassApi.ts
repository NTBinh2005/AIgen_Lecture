/**
 * API quản lý lớp học cho Teacher: lớp, học sinh, giao bài giảng.
 */
import axiosInstance from './axiosInstance'
import type { ClassDetail, ClassLecture, Enrollment, EnrollmentStatus } from '@/types/student'
import type { BulkEnrollmentItem, ClassFormPayload, ClassUpdatePayload } from '@/types/teacher'

/** Lớp mình phụ trách (chính hoặc đồng giảng dạy) */
export async function getTeacherClasses(): Promise<ClassDetail[]> {
  const res = await axiosInstance.get<ClassDetail[]>('/classes/my')
  return res.data
}

/** Lớp mới luôn ở trạng thái DRAFT. teacherId là bắt buộc trong DTO backend. */
export async function createClass(teacherId: number, payload: ClassFormPayload): Promise<ClassDetail> {
  const res = await axiosInstance.post<ClassDetail>('/classes', { teacherId, ...payload })
  return res.data
}

export async function updateClass(classId: number, payload: ClassUpdatePayload): Promise<ClassDetail> {
  const res = await axiosInstance.patch<ClassDetail>(`/classes/${classId}`, payload)
  return res.data
}

/** DRAFT → ACTIVE. Cần có tên lớp, mã lớp và ngày bắt đầu. */
export async function activateClass(classId: number): Promise<ClassDetail> {
  const res = await axiosInstance.patch<ClassDetail>(`/classes/${classId}/activate`)
  return res.data
}

/** ACTIVE → CLOSED. Không đóng được khi đang có buổi live. */
export async function closeClass(classId: number): Promise<ClassDetail> {
  const res = await axiosInstance.patch<ClassDetail>(`/classes/${classId}/close`)
  return res.data
}

export async function archiveClass(classId: number): Promise<ClassDetail> {
  const res = await axiosInstance.patch<ClassDetail>(`/classes/${classId}/archive`)
  return res.data
}

// ─── Học sinh ─────────────────────────────────────────────────────────────────

export interface StudentSearchResult {
  userId: number
  name: string
  email: string
  status: string
}

/** Tìm học sinh theo email (chứa chuỗi, không phân biệt hoa thường, tối đa 20 kết quả). */
export async function searchStudents(email: string): Promise<StudentSearchResult[]> {
  const res = await axiosInstance.get<StudentSearchResult[]>('/users/students', { params: { email } })
  return res.data
}

export async function getClassStudents(classId: number): Promise<Enrollment[]> {
  const res = await axiosInstance.get<Enrollment[]>(`/classes/${classId}/students`)
  return res.data
}

export async function enrollStudents(classId: number, studentIds: number[]): Promise<BulkEnrollmentItem[]> {
  const res = await axiosInstance.post<BulkEnrollmentItem[]>(`/classes/${classId}/students/bulk`, { studentIds })
  return res.data
}

export async function updateEnrollmentStatus(
  classId: number,
  studentId: number,
  status: EnrollmentStatus,
): Promise<Enrollment> {
  const res = await axiosInstance.patch<Enrollment>(`/classes/${classId}/students/${studentId}`, { status })
  return res.data
}

// ─── Bài giảng của lớp ────────────────────────────────────────────────────────

/** Chỉ giao được bài giảng đã PUBLISHED. */
export async function assignLectureToClass(classId: number, lectureId: number): Promise<ClassLecture> {
  const res = await axiosInstance.post<ClassLecture>(`/classes/${classId}/lectures`, { lectureId })
  return res.data
}

export async function unassignLecture(classId: number, lectureId: number): Promise<void> {
  await axiosInstance.delete(`/classes/${classId}/lectures/${lectureId}`)
}
