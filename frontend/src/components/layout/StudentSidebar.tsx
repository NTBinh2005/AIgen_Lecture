import { Link, useLocation } from 'react-router-dom'
import {
  LayoutDashboard,
  PlaySquare,
  Users,
  CalendarDays,
  GraduationCap,
  CreditCard,
  Wallet
} from 'lucide-react'

const navItems = [
  {
    title: 'Bảng điều khiển',
    href: '/student',
    icon: LayoutDashboard,
  },
  {
    title: 'Lớp học của tôi',
    href: '/student/classes',
    icon: Users,
  },
  {
    title: 'Bài giảng',
    href: '/student/lectures',
    icon: PlaySquare,
  },
  {
    title: 'Lịch học & Live',
    href: '/student/schedule',
    icon: CalendarDays,
  },
  {
    title: 'Kết quả học tập',
    href: '/student/quiz-history',
    icon: GraduationCap,
  },
  {
    title: 'Ví học tập',
    href: '/student/wallet',
    icon: Wallet,
  },
  {
    title: 'Gói & thanh toán',
    href: '/student/payments',
    icon: CreditCard,
  },
]

function isActivePath(pathname: string, href: string) {
  // Bảng điều khiển chỉ active đúng trang gốc; các mục khác active cả trang con (/student/classes/1)
  return href === '/student' ? pathname === href || pathname === '/student/' : pathname.startsWith(href)
}

interface StudentNavProps {
  /** Gọi khi chọn một mục — dùng để đóng menu mobile */
  onNavigate?: () => void
  /** true khi hiển thị trong mobile drawer (luôn mở full, không thu gọn) */
  isMobile?: boolean
}

/** Logo + danh sách điều hướng + đăng xuất, dùng chung cho sidebar desktop và menu mobile. */
export function StudentNav({ onNavigate, isMobile }: StudentNavProps) {
  const location = useLocation()

  return (
    <>
      {/* Logo Area */}
      <div className="h-16 flex items-center px-3 border-b border-border/50 shrink-0 overflow-hidden">
        <Link to="/student" onClick={onNavigate} className="flex items-center gap-3 px-2 py-2 rounded-xl group w-full">
          <div className="w-9 h-9 flex items-center justify-center shrink-0 group-hover:scale-105 transition-transform">
            <img src="/logo-icon.png" alt="EduMind Logo" className="w-9 h-9 object-contain" />
          </div>
          <span
            className={`font-bold text-xl tracking-tight text-foreground whitespace-nowrap overflow-hidden transition-all duration-300 ${
              isMobile
                ? 'opacity-100 max-w-xs'
                : 'opacity-0 group-hover/sidebar:opacity-100 max-w-0 group-hover/sidebar:max-w-xs'
            }`}
          >
            Edu<span className="text-primary">Mind</span>
          </span>
        </Link>
      </div>

      {/* Navigation */}
      <nav className="flex-1 py-6 px-3 space-y-1 overflow-y-auto scrollbar-none overflow-x-hidden" aria-label="Điều hướng học sinh">
        <div className="h-6 flex items-center px-3 mb-2">
          <span
            className={`text-xs font-semibold text-muted-foreground uppercase tracking-wider whitespace-nowrap overflow-hidden transition-all duration-300 ${
              isMobile ? 'opacity-100' : 'opacity-0 group-hover/sidebar:opacity-100'
            }`}
          >
            Học tập
          </span>
          {!isMobile && (
            <div className="w-6 h-px bg-border/60 mx-auto group-hover/sidebar:hidden transition-all" />
          )}
        </div>
        {navItems.map((item) => {
          const isActive = isActivePath(location.pathname, item.href)
          return (
            <Link
              key={item.href}
              to={item.href}
              onClick={onNavigate}
              title={item.title}
              aria-current={isActive ? 'page' : undefined}
              className={`flex items-center gap-3 px-3 py-2.5 min-h-11 rounded-xl text-sm font-medium transition-all duration-200 group relative ${
                isActive
                  ? 'text-blue-600 bg-blue-600/10 dark:text-blue-400'
                  : 'text-muted-foreground hover:text-foreground hover:bg-muted'
              }`}
            >
              {isActive && (
                <div className="absolute left-0 top-1/2 -translate-y-1/2 w-1 h-5 bg-blue-600 rounded-r-full" />
              )}
              <div className="w-8 h-8 flex items-center justify-center shrink-0">
                <item.icon
                  size={18}
                  className={`transition-colors ${isActive ? 'text-blue-600 dark:text-blue-400' : 'group-hover:text-foreground'}`}
                  aria-hidden="true"
                />
              </div>
              <span
                className={`whitespace-nowrap overflow-hidden transition-all duration-300 ${
                  isMobile
                    ? 'opacity-100 max-w-xs'
                    : 'opacity-0 group-hover/sidebar:opacity-100 max-w-0 group-hover/sidebar:max-w-[200px]'
                }`}
              >
                {item.title}
              </span>
            </Link>
          )
        })}
      </nav>
    </>
  )
}

export function StudentSidebar() {
  return (
    <aside className="hidden lg:flex h-screen flex-col fixed left-0 top-0 border-r border-border/50 bg-background/80 hover:bg-background/95 backdrop-blur-xl z-30 transition-[width,box-shadow,background-color] duration-300 ease-in-out w-20 hover:w-72 hover:shadow-2xl overflow-hidden group/sidebar">
      <StudentNav />
    </aside>
  )
}
