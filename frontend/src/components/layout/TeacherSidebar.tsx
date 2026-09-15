import { Link, useLocation } from 'react-router-dom'
import {
  LayoutDashboard,
  BookOpen,
  BarChart3,
  Settings,
  LogOut,
  Sparkles
} from 'lucide-react'
import { useAuthStore } from '@/store/authStore'

const navItems = [
  {
    title: 'Tổng quan',
    href: '/teacher',
    icon: LayoutDashboard,
  },
  {
    title: 'Bài giảng của tôi',
    href: '/teacher/lectures',
    icon: BookOpen,
  },
  {
    title: 'Thống kê & Phân tích',
    href: '/teacher/analytics',
    icon: BarChart3,
  },
  {
    title: 'Cài đặt',
    href: '/teacher/settings',
    icon: Settings,
  },
]

export function TeacherSidebar() {
  const location = useLocation()
  const { logout, user } = useAuthStore()

  return (
    <aside className="hidden lg:flex h-screen w-72 flex-col fixed left-0 top-0 z-30
      border-r border-border/50 bg-card/80 backdrop-blur-xl
      dark:bg-background/60 dark:border-border/40 dark:backdrop-blur-2xl">

      {/* Logo Area */}
      <div className="h-16 flex items-center px-6 border-b border-border/50 dark:border-border/40">
        <Link to="/teacher" className="flex items-center gap-2.5 group">
          <div className="w-9 h-9 rounded-xl bg-primary flex items-center justify-center text-primary-foreground shadow-lg shadow-primary/30 group-hover:scale-110 transition-all duration-300 group-hover:shadow-primary/50">
            <Sparkles size={18} aria-hidden="true" />
          </div>
          <span className="font-bold text-xl tracking-tight text-foreground">
            Edu<span className="text-primary">Mind</span>
          </span>
        </Link>
      </div>

      {/* User Badge */}
      <div className="px-4 py-4 border-b border-border/30">
        <div className="flex items-center gap-3 px-3 py-2.5 rounded-xl bg-primary/8 dark:bg-primary/10 border border-primary/15">
          <div className="w-8 h-8 rounded-full bg-gradient-to-br from-primary to-violet-500 flex items-center justify-center text-primary-foreground text-xs font-bold shadow-md shadow-primary/20 shrink-0">
            {user?.name?.charAt(0)?.toUpperCase() || 'T'}
          </div>
          <div className="flex flex-col min-w-0">
            <span className="text-sm font-semibold text-foreground truncate">{user?.name || 'Giáo viên'}</span>
            <span className="text-xs text-muted-foreground">Giảng viên</span>
          </div>
        </div>
      </div>

      {/* Navigation */}
      <div className="flex-1 overflow-y-auto py-4 px-3 flex flex-col gap-1">
        <p className="text-xs font-semibold text-muted-foreground/60 uppercase tracking-wider px-3 mb-2">Menu</p>
        {navItems.map((item) => {
          const isActive = location.pathname === item.href
          return (
            <Link
              key={item.href}
              to={item.href}
              className={`flex items-center gap-3 px-3.5 py-2.5 rounded-xl transition-all duration-200 group relative ${
                isActive
                  ? 'bg-primary text-primary-foreground shadow-md shadow-primary/25'
                  : 'text-muted-foreground hover:bg-muted/60 hover:text-foreground dark:hover:bg-muted/30'
              }`}
            >
              {/* Active glow */}
              {isActive && (
                <div className="absolute inset-0 rounded-xl bg-primary/20 blur-xl -z-10 dark:block hidden" />
              )}
              <item.icon
                size={18}
                className={`shrink-0 transition-transform duration-300 ${isActive ? 'text-primary-foreground' : 'text-muted-foreground group-hover:text-foreground group-hover:scale-110'}`}
                aria-hidden="true"
              />
              <span className="text-sm font-medium">{item.title}</span>
              {isActive && (
                <div className="ml-auto w-1.5 h-1.5 rounded-full bg-primary-foreground/70" />
              )}
            </Link>
          )
        })}
      </div>

      {/* Footer Area (Logout) */}
      <div className="p-3 border-t border-border/40">
        <button
          onClick={logout}
          className="w-full flex items-center gap-3 px-3.5 py-2.5 rounded-xl text-muted-foreground hover:bg-destructive/10 hover:text-destructive transition-all duration-200 group"
        >
          <LogOut size={18} className="shrink-0 group-hover:rotate-12 transition-transform duration-300" aria-hidden="true" />
          <span className="text-sm font-medium">Đăng xuất</span>
        </button>
      </div>
    </aside>
  )
}
