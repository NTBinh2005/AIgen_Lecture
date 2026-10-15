/**
 * API dành cho Admin: người dùng, thống kê, nhật ký hệ thống, toàn bộ lớp/ghi danh, hoá đơn.
 * (Quản lý chi tiết một lớp dùng chung teacherClassApi / teacherLiveApi — backend cho phép ADMIN.)
 */
import axiosInstance from './axiosInstance'
import { fetchAllPages, fetchPage } from './pagination'
import type { Page } from './pagination'
import type { ClassDetail, Enrollment } from '@/types/student'
import type { Payment, Refund, Subscription, SubscriptionPlan, SubscriptionPlanPayload } from '@/types/billing'
import type {
  AdminUser, AuditAction, AuditLog, Invoice, StatisticsOverview, UserCreatePayload, UserUpdatePayload,
} from '@/types/admin'

// ─── Người dùng ───────────────────────────────────────────────────────────────

/** Mọi người dùng (gộp các trang — trang quản trị cần lọc / đếm trên toàn bộ). */
export async function getUsers(): Promise<AdminUser[]> {
  return fetchAllPages<AdminUser>('/users')
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
  return fetchAllPages<ClassDetail>('/classes')
}

export async function getAllEnrollments(): Promise<Enrollment[]> {
  return fetchAllPages<Enrollment>('/enrollments')
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

export interface AuditLogFilters { userId?: number; action?: AuditAction; resourceType?: string }

/** Một trang nhật ký (mới nhất trước) — dùng cho trang Nhật ký hệ thống. */
export async function getAuditLogPage(filters: AuditLogFilters, page: number, size = 20): Promise<Page<AuditLog>> {
  return fetchPage<AuditLog>('/audit-logs', { ...filters, page, size })
}

/** Nhật ký gần đây (tối đa 500 bản ghi) — dùng cho tổng quan và biểu đồ 7 ngày. */
export async function getAuditLogs(filters: AuditLogFilters = {}): Promise<AuditLog[]> {
  return fetchAllPages<AuditLog>('/audit-logs', { ...filters }, 5)
}

/** Toàn bộ giao dịch của mọi người dùng, mới nhất trước. */
export async function getAllPayments(): Promise<Payment[]> {
  return fetchAllPages<Payment>('/payments/admin')
}

/** Toàn bộ yêu cầu hoàn tiền, mới nhất trước. */
export async function getAllRefunds(): Promise<Refund[]> {
  return fetchAllPages<Refund>('/refunds')
}

/** Chỉ đi được theo chiều REQUESTED → APPROVED | REJECTED, rồi APPROVED → COMPLETED. */
export async function updateRefundStatus(refundId: number, status: 'APPROVED' | 'REJECTED' | 'COMPLETED'): Promise<Refund> {
  const res = await axiosInstance.patch<Refund>(`/refunds/${refundId}/status`, { status })
  return res.data
}

// ─── Gói đăng ký ──────────────────────────────────────────────────────────────

/** Mọi gói, kể cả gói đã ngừng bán. */
export async function getAllPlans(): Promise<SubscriptionPlan[]> {
  const res = await axiosInstance.get<SubscriptionPlan[]>('/admin/plans')
  return res.data
}

export async function createPlan(payload: SubscriptionPlanPayload): Promise<SubscriptionPlan> {
  const res = await axiosInstance.post<SubscriptionPlan>('/plans', payload)
  return res.data
}

export async function updatePlan(planId: number, payload: SubscriptionPlanPayload): Promise<SubscriptionPlan> {
  const res = await axiosInstance.put<SubscriptionPlan>(`/plans/${planId}`, payload)
  return res.data
}

/** Ngừng bán (không xoá): người đã mua vẫn dùng tới hết hạn. */
export async function deactivatePlan(planId: number): Promise<void> {
  await axiosInstance.delete(`/plans/${planId}`)
}

/** Gói của mọi người dùng (chỉ ADMIN). */
export async function getAllSubscriptions(): Promise<Subscription[]> {
  return fetchAllPages<Subscription>('/subscriptions')
}

/** Cấp gói thủ công (không qua thanh toán) — VD khi chưa có khoá cổng thanh toán. */
export async function grantSubscription(userId: number, planId: number, days: number): Promise<Subscription> {
  const res = await axiosInstance.post<Subscription>('/admin/subscriptions', { userId, planId, days })
  return res.data
}

export async function extendSubscription(subscriptionId: number, days: number): Promise<Subscription> {
  const res = await axiosInstance.post<Subscription>(`/admin/subscriptions/${subscriptionId}/extend`, { days })
  return res.data
}

/** Huỷ ngay (khác với người dùng tự huỷ gia hạn cuối chu kỳ). */
export async function cancelSubscriptionNow(subscriptionId: number): Promise<Subscription> {
  const res = await axiosInstance.post<Subscription>(`/admin/subscriptions/${subscriptionId}/cancel`)
  return res.data
}

export async function getInvoices(): Promise<Invoice[]> {
  return fetchAllPages<Invoice>('/invoices')
}

/** Chuyển quiz kiểu cũ (ai_elements) của một bài giảng sang module Quiz mới. */
export async function migrateLectureQuizzes(lectureId: number): Promise<void> {
  await axiosInstance.post(`/admin/quiz-migrations/import-ai-elements/${lectureId}`)
}

export interface StatisticsCharts {
  userGrowth: Array<{ month: string; students: number; teachers: number }>
  lectureGrowth: Array<{ month: string; lectures: number; aiGenerated: number }>
  weeklyInteractions: Array<{ day: string; quizzes: number; flashcards: number; notes: number }>
  roleDistribution: Array<{ name: string; value: number; color: string }>
}

/** Hiện chỉ roleDistribution có dữ liệu thật; các chuỗi theo thời gian trả mảng rỗng (FIX.md #16b). */
export async function getStatisticsCharts(): Promise<StatisticsCharts> {
  const res = await axiosInstance.get<StatisticsCharts>('/statistics/charts')
  return res.data
}
