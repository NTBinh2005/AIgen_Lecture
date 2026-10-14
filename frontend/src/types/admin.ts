/** Types cho luồng Admin — khớp DTO backend (đã gọi thử bằng tài khoản ADMIN). */
import type { Role } from '@/store/authStore'

export type UserStatus = 'ACTIVE' | 'INACTIVE'

export interface AdminUser {
  userId: number
  role: Role
  name: string
  email: string
  status: UserStatus
  createdAt: string
  /** Email trùng (khác hoa thường) với tài khoản khác: không đăng nhập bằng email được cho tới khi admin đổi email. */
  emailAmbiguous?: boolean
}

export interface UserCreatePayload {
  role: Role
  name: string
  email: string
  /** Mật khẩu dạng thường — backend đặt tên trường là passwordHash và tự băm BCrypt */
  passwordHash: string
}

export interface UserUpdatePayload {
  role?: Role
  name?: string
  email?: string
  passwordHash?: string
  status?: UserStatus
}

export const AUDIT_ACTIONS = [
  'USER_REGISTER', 'USER_LOGIN', 'USER_LOGOUT', 'PASSWORD_CHANGE',
  'PAYMENT_CREATED', 'PAYMENT_SUCCESS', 'PAYMENT_FAILED',
  'REFUND_REQUESTED', 'REFUND_COMPLETED',
  'ROLE_CHANGED', 'PERMISSION_GRANTED', 'PERMISSION_REVOKED',
] as const

export type AuditAction = (typeof AUDIT_ACTIONS)[number]

export interface AuditLog {
  id: number
  userId: number | null
  action: AuditAction
  resourceType: string | null
  resourceId: string | null
  ipAddress: string | null
  userAgent: string | null
  metadata: string | null
  createdAt: string
}

/** GET /statistics/overview — uptime là ISO-8601 duration; chi phí null khi chưa có telemetry. */
export interface StatisticsOverview {
  totalUsers: number
  totalStudents: number
  totalTeachers: number
  totalLectures: number
  totalAiGeneratedLectures: number
  totalInteractions: number
  llmCostUsd: number | null
  serverUptime: string
}

export interface Invoice {
  id: number
  invoiceNumber: string
  userId: number
  paymentId: number
  amount: number
  status: string
  issuedAt: string | null
  createdAt: string
}
