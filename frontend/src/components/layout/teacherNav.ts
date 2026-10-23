/** Menu điều hướng của Teacher — dùng chung cho sidebar desktop và menu mobile. */
import { BookOpen, ClipboardList, Crown, LayoutDashboard, Presentation, School, Wallet } from 'lucide-react'

export const teacherNavItems = [
  { title: 'Tổng quan', href: '/teacher', icon: LayoutDashboard },
  { title: 'Lớp học', href: '/teacher/classes', icon: School },
  { title: 'Bài giảng của tôi', href: '/teacher/lectures', icon: BookOpen },
  { title: 'Bài kiểm tra', href: '/teacher/quizzes', icon: ClipboardList },
  { title: 'Bản trình chiếu', href: '/teacher/presentations', icon: Presentation },
  { title: 'Ví & Doanh thu', href: '/teacher/wallet', icon: Wallet },
  { title: 'Gói & thanh toán', href: '/teacher/payments', icon: Crown },
]

/** Tổng quan chỉ active đúng trang gốc; các mục khác active cả trang con. */
export function isTeacherNavActive(pathname: string, href: string) {
  return href === '/teacher' ? pathname === '/teacher' || pathname === '/teacher/' : pathname.startsWith(href)
}
