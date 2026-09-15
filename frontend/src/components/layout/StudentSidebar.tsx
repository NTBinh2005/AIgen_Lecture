import { Link, useLocation } from 'react-router-dom'
import {
  LayoutDashboard,
  PlaySquare,
  BookOpen,
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
    title: 'Khóa học của tôi',
    href: '/student/courses',
    icon: BookOpen,
  },
  {
    title: 'Bài giảng video',
    href: '/student/lectures',
    icon: PlaySquare,
  },
  {
    title: 'Cài đặt',
    href: '/student/settings',
    icon: Settings,
  },
]

export function StudentSidebar() {
  const location = useLocation()
  const { logout } = useAuthStore()

  return (
    <aside className="hidden lg:flex h-screen w-72 flex-col fixed left-0 top-0 border-r border-border/50 bg-background/50 backdrop-blur-xl z-30">
      {/* Logo Area */}
      <div className="h-16 flex items-center px-6 border-b border-border/50">
        <Link to="/student" className="flex items-center gap-2.5 group">
          <div className="w-8 h-8 rounded-xl bg-blue-600 flex items-center justify-center text-white shadow-lg shadow-blue-600/25 group-hover:scale-105 transition-transform">
            <Sparkles size={18} aria-hidden="true" />
          </div>
          <span className="font-bold text-xl tracking-tight text-foreground">
            Edu<span className="text-blue-600">Mind</span>
          </span>
        </Link>
      </div>

      {/* Navigation */}
      <div className="flex-1 py-6 px-4 space-y-1 overflow-y-auto scrollbar-none">
        <div className="px-2 pb-4 text-xs font-semibold text-muted-foreground uppercase tracking-wider">
          Học tập
        </div>
        {navItems.map((item) => {
          const isActive = location.pathname === item.href
          return (
            <Link
              key={item.href}
              to={item.href}
              className={`flex items-center gap-3 px-3 py-2.5 rounded-xl text-sm font-medium transition-all duration-200 group relative ${
                isActive
                  ? 'text-blue-600 bg-blue-600/10'
                  : 'text-muted-foreground hover:text-foreground hover:bg-muted'
              }`}
            >
              {isActive && (
                <div className="absolute left-0 top-1/2 -translate-y-1/2 w-1 h-5 bg-blue-600 rounded-r-full" />
              )}
              <item.icon
                size={18}
                className={`transition-colors ${isActive ? 'text-blue-600' : 'group-hover:text-foreground'}`}
              />
              {item.title}
            </Link>
          )
        })}
      </div>

      {/* User Actions */}
      <div className="p-4 border-t border-border/50 mt-auto">
        <button
          onClick={logout}
          className="flex items-center gap-3 w-full px-3 py-2.5 rounded-xl text-sm font-medium text-muted-foreground hover:text-destructive hover:bg-destructive/10 transition-colors group"
        >
          <LogOut size={18} className="group-hover:text-destructive transition-colors" />
          Đăng xuất
        </button>
      </div>
    </aside>
  )
}
