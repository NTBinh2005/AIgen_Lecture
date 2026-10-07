/**
 * API dành cho Admin: người dùng, thống kê, nhật ký hệ thống, toàn bộ lớp/ghi danh, hoá đơn.
 * (Quản lý chi tiết một lớp dùng chung teacherClassApi / teacherLiveApi — backend cho phép ADMIN.)
 */
import axiosInstance from './axiosInstance'
import type { ClassDetail, Enrollment } from '@/types/student'
import type {
  AdminUser, AuditAction, AuditLog, Invoice, StatisticsOverview, UserCreatePayload, UserUpdatePayload,
} from '@/types/admin'

// ─── Người dùng ───────────────────────────────────────────────────────────────

export async function getUsers(): Promise<AdminUser[]> {
  const res = await axiosInstance.get<AdminUser[]>('/users')
  return res.data
}

export async function createUser(payload: UserCreatePayload): Promise<AdminUser> {
  const res = await axiosInstance.post<AdminUser>('/users', payload)
  return res.data
}

export async function updateUser(userId: number, payload: UserUpdatePayload): Promise<AdminUser> {
  const res = await axiosInstance.patch<AdminUser>(`/users/${userId}`, payload)
  return res.data
}

/** Khoá tài khoản (status → INACTIVE). Mở lại bằng updateUser({ status: 'ACTIVE' }). */
export async function deactivateUser(userId: number): Promise<void> {
  await axiosInstance.delete(`/users/${userId}`)
}

/** Các lớp mà một học sinh đã ghi danh */
export async function getStudentClasses(studentId: number): Promise<Enrollment[]> {
  const res = await axiosInstance.get<Enrollment[]>(`/students/${studentId}/classes`)
  return res.data
}

// ─── Lớp học & ghi danh ───────────────────────────────────────────────────────

/** ADMIN nhận toàn bộ lớp trong hệ thống */
export async function getAllClasses(): Promise<ClassDetail[]> {
  const res = await axiosInstance.get<ClassDetail[]>('/classes')
  return res.data
}

export async function getAllEnrollments(): Promise<Enrollment[]> {
  const res = await axiosInstance.get<Enrollment[]>('/enrollments')
  return res.data
}

/** Vô hiệu hoá lớp (chỉ ADMIN). */
export async function deleteClass(classId: number): Promise<void> {
  await axiosInstance.delete(`/classes/${classId}`)
}

// ─── Thống kê, nhật ký, hoá đơn ───────────────────────────────────────────────

export async function getStatisticsOverview(): Promise<StatisticsOverview> {
  const res = await axiosInstance.get<StatisticsOverview>('/statistics/overview')
  return res.data
}

export async function getAuditLogs(params?: { userId?: number; action?: AuditAction; resourceType?: string }): Promise<AuditLog[]> {
  const res = await axiosInstance.get<AuditLog[]>('/audit-logs', { params })
  return res.data
}

export async function getInvoices(): Promise<Invoice[]> {
  const res = await axiosInstance.get<Invoice[]>('/invoices')
  return res.data
}
