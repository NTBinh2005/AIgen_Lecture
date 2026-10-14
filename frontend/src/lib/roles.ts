import type { Role } from '@/store/authStore'

/** Trang chính của từng vai trò sau khi đăng nhập. */
export const HOME_BY_ROLE: Record<Role, string> = { TEACHER: '/teacher', STUDENT: '/student', ADMIN: '/admin' }

export const ROLE_TITLE: Record<Role, string> = { TEACHER: 'Giảng viên', STUDENT: 'Học viên', ADMIN: 'Quản trị viên' }
