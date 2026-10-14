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

/** "P1DT2H5M3.2S" (ISO-8601 duration) → "1 ngày 2 giờ 5 phút". Trả null nếu không đọc được. */
export function formatIsoDuration(value: string | null | undefined): string | null {
  const m = value?.match(/^P(?:(\d+)D)?(?:T(?:(\d+)H)?(?:(\d+)M)?(?:([\d.]+)S)?)?$/)
  if (!m) return null
  const [days, hours, minutes, seconds] = [m[1], m[2], m[3], m[4]].map(x => Number(x ?? 0))
  const totalHours = days * 24 + hours
  const d = Math.floor(totalHours / 24)
  const parts = [d && `${d} ngày`, totalHours % 24 && `${totalHours % 24} giờ`, minutes && `${minutes} phút`].filter(Boolean)
  return parts.length ? parts.join(' ') : `${Math.floor(seconds)} giây`
}
