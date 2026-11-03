/** Menu điều hướng của Student — hiển thị ở thanh dock dưới đáy màn hình. */
import { CalendarDays, CreditCard, GraduationCap, LayoutDashboard, PlaySquare, Users, Wallet } from 'lucide-react'

export const studentNavItems = [
  { title: 'Bảng điều khiển', href: '/student', icon: LayoutDashboard },
  { title: 'Lớp học của tôi', href: '/student/classes', icon: Users },
  { title: 'Bài giảng', href: '/student/lectures', icon: PlaySquare },
  { title: 'Lịch học & Live', href: '/student/schedule', icon: CalendarDays },
  { title: 'Kết quả học tập', href: '/student/quiz-history', icon: GraduationCap },
  { title: 'Ví học tập', href: '/student/wallet', icon: Wallet },
  { title: 'Gói & thanh toán', href: '/student/payments', icon: CreditCard },
]

/** Bảng điều khiển chỉ active đúng trang gốc; các mục khác active cả trang con (/student/classes/1). */
export function isStudentNavActive(pathname: string, href: string) {
  return href === '/student' ? pathname === href || pathname === '/student/' : pathname.startsWith(href)
}
