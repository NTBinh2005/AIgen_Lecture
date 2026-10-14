/** Nhãn hiển thị cho luồng Admin. */
import type { Role } from '@/store/authStore'
import type { AuditAction } from '@/types/admin'

export const ROLE_LABEL: Record<Role, string> = { ADMIN: 'Quản trị viên', TEACHER: 'Giáo viên', STUDENT: 'Học sinh' }

export const AUDIT_ACTION_LABEL: Record<AuditAction, string> = {
  USER_REGISTER: 'Đăng ký',
  USER_LOGIN: 'Đăng nhập',
  USER_LOGOUT: 'Đăng xuất',
  PASSWORD_CHANGE: 'Đổi mật khẩu',
  PAYMENT_CREATED: 'Tạo thanh toán',
  PAYMENT_SUCCESS: 'Thanh toán thành công',
  PAYMENT_FAILED: 'Thanh toán thất bại',
  REFUND_REQUESTED: 'Yêu cầu hoàn tiền',
  REFUND_COMPLETED: 'Hoàn tiền xong',
  ROLE_CHANGED: 'Đổi vai trò',
  PERMISSION_GRANTED: 'Cấp quyền',
  PERMISSION_REVOKED: 'Thu hồi quyền',
}

/** "2026-10" → "T10/2026" */
export function monthLabel(key: string): string {
  const [y, m] = key.split('-')
  return `T${Number(m)}/${y}`
}
