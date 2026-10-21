import type { Role } from '@/store/authStore'

/** Trang chính của từng vai trò sau khi đăng nhập. */
export const HOME_BY_ROLE: Record<Role, string> = { TEACHER: '/teacher', STUDENT: '/student', ADMIN: '/admin' }

export const ROLE_TITLE: Record<Role, string> = { TEACHER: 'Giảng viên', STUDENT: 'Học viên', ADMIN: 'Quản trị viên' }

/**
 * Trang cần quay lại sau khi đăng nhập (tham số `next`). Chỉ nhận đường dẫn nội bộ thuộc khu vực của đúng vai trò
 * để không bị lợi dụng chuyển hướng ra trang ngoài; không hợp lệ thì về trang chính của vai trò.
 */
export function homeAfterLogin(role: Role, next: string | null): string {
  const home = HOME_BY_ROLE[role]
  if (next && next.startsWith(home) && !next.startsWith('//') && !next.includes('\\')) return next
  return home
}
