/** Menu điều hướng của Admin — dùng chung cho sidebar desktop và menu mobile. */
import { Activity, BarChart3, BookOpen, ClipboardList, CreditCard, Crown, LayoutDashboard, School, Settings, Users } from 'lucide-react'

export const adminNavItems = [
  { title: 'Tổng quan', href: '/admin', icon: LayoutDashboard },
  { title: 'Người dùng', href: '/admin/users', icon: Users },
  { title: 'Lớp học', href: '/admin/classes', icon: School },
  { title: 'Bài giảng', href: '/admin/lectures', icon: BookOpen },
  { title: 'Bài kiểm tra', href: '/admin/quizzes', icon: ClipboardList },
  { title: 'Gói đăng ký', href: '/admin/plans', icon: Crown },
  { title: 'Thanh toán', href: '/admin/payments', icon: CreditCard },
  { title: 'Thống kê', href: '/admin/statistics', icon: BarChart3 },
  { title: 'Nhật ký hệ thống', href: '/admin/logs', icon: Activity },
  { title: 'Cài đặt', href: '/admin/settings', icon: Settings },
]

export function isAdminNavActive(pathname: string, href: string) {
  return href === '/admin' ? pathname === '/admin' || pathname === '/admin/' : pathname.startsWith(href)
}
