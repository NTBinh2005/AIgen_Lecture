import { Link, useLocation } from 'react-router-dom'
import {
  LayoutDashboard,
  PlaySquare,
  Users,
  CalendarDays,
  Settings,
  LogOut,
  Sparkles
} from 'lucide-react'
import { useAuthStore } from '@/store/authStore'

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
    title: 'Cài đặt',
    href: '/student/settings',
    icon: Settings,
  },
]

function isActivePath(pathname: string, href: string) {
  // Bảng điều khiển chỉ active đúng trang gốc; các mục khác active cả trang con (/student/classes/1)
  return href === '/student' ? pathname === href || pathname === '/student/' : pathname.startsWith(href)
}

interface StudentNavProps {
  /** Gọi khi chọn một mục — dùng để đóng menu mobile */
  onNavigate?: () => void
}

/** Logo + danh sách điều hướng + đăng xuất, dùng chung cho sidebar desktop và menu mobile. */
export function StudentNav({ onNavigate }: StudentNavProps) {
  const location = useLocation()
  const { logout } = useAuthStore()

  return (
    <>
      {/* Logo Area */}
      <div className="h-16 flex items-center px-6 border-b border-border/50 shrink-0">
        <Link to="/student" onClick={onNavigate} className="flex items-center gap-2.5 group">
          <div className="w-8 h-8 rounded-xl bg-blue-600 flex items-center justify-center text-white shadow-lg shadow-blue-600/25 group-hover:scale-105 transition-transform">
            <Sparkles size={18} aria-hidden="true" />
          </div>
          <span className="font-bold text-xl tracking-tight text-foreground">
            Edu<span className="text-blue-600">Mind</span>
          </span>
        </Link>
      </div>

      {/* Navigation */}
      <nav className="flex-1 py-6 px-4 space-y-1 overflow-y-auto scrollbar-none" aria-label="Điều hướng học sinh">
        <div className="px-2 pb-4 text-xs font-semibold text-muted-foreground uppercase tracking-wider">
          Học tập
        </div>
        {navItems.map((item) => {
          const isActive = isActivePath(location.pathname, item.href)
          return (
            <Link
              key={item.href}
              to={item.href}
              onClick={onNavigate}
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
              <item.icon
                size={18}
                className={`transition-colors ${isActive ? 'text-blue-600 dark:text-blue-400' : 'group-hover:text-foreground'}`}
              />
              {item.title}
            </Link>
          )
        })}
      </nav>

      {/* User Actions */}
      <div className="p-4 border-t border-border/50 mt-auto">
        <button
          onClick={logout}
          className="flex items-center gap-3 w-full px-3 py-2.5 min-h-11 rounded-xl text-sm font-medium text-muted-foreground hover:text-destructive hover:bg-destructive/10 transition-colors group"
        >
          <LogOut size={18} className="group-hover:text-destructive transition-colors" />
          Đăng xuất
        </button>
      </div>
    </>
  )
}

export function StudentSidebar() {
  return (
    <aside className="hidden lg:flex h-screen w-72 flex-col fixed left-0 top-0 border-r border-border/50 bg-background/50 backdrop-blur-xl z-30">
      <StudentNav />
    </aside>
  )
}
