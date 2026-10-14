import { Link, useLocation } from 'react-router-dom'
import { LogOut, Sparkles } from 'lucide-react'
import { useAuthStore } from '@/store/authStore'
import { isTeacherNavActive, teacherNavItems } from './teacherNav'

interface TeacherNavProps {
  /** Gọi khi chọn một mục — dùng để đóng menu mobile */
  onNavigate?: () => void
}

/** Logo + danh sách điều hướng + đăng xuất, dùng chung cho sidebar desktop và menu mobile (cùng kiểu với học sinh / admin). */
export function TeacherNav({ onNavigate }: TeacherNavProps) {
  const location = useLocation()
  const { logout } = useAuthStore()

  return (
    <>
      {/* Logo Area */}
      <div className="h-16 flex items-center px-6 border-b border-border/50 shrink-0">
        <Link to="/teacher" onClick={onNavigate} className="flex items-center gap-2.5 group">
          <div className="w-8 h-8 rounded-xl bg-primary flex items-center justify-center text-primary-foreground shadow-lg shadow-primary/25 group-hover:scale-105 transition-transform">
            <Sparkles size={18} aria-hidden="true" />
          </div>
          <span className="font-bold text-xl tracking-tight text-foreground">
            Edu<span className="text-primary">Mind</span>
          </span>
        </Link>
      </div>

      {/* Navigation */}
      <nav className="flex-1 py-6 px-4 space-y-1 overflow-y-auto scrollbar-none" aria-label="Điều hướng giáo viên">
        <div className="px-2 pb-4 text-xs font-semibold text-muted-foreground uppercase tracking-wider">
          Giảng dạy
        </div>
        {teacherNavItems.map((item) => {
          const isActive = isTeacherNavActive(location.pathname, item.href)
          return (
            <Link
              key={item.href}
              to={item.href}
              onClick={onNavigate}
              aria-current={isActive ? 'page' : undefined}
              className={`flex items-center gap-3 px-3 py-2.5 min-h-11 rounded-xl text-sm font-medium transition-all duration-200 group relative ${
                isActive
                  ? 'text-primary bg-primary/10'
                  : 'text-muted-foreground hover:text-foreground hover:bg-muted'
              }`}
            >
              {isActive && (
                <div className="absolute left-0 top-1/2 -translate-y-1/2 w-1 h-5 bg-primary rounded-r-full" />
              )}
              <item.icon
                size={18}
                className={`transition-colors ${isActive ? 'text-primary' : 'group-hover:text-foreground'}`}
                aria-hidden="true"
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
          <LogOut size={18} className="group-hover:text-destructive transition-colors" aria-hidden="true" />
          Đăng xuất
        </button>
      </div>
    </>
  )
}

export function TeacherSidebar() {
  return (
    <aside className="hidden lg:flex h-screen w-72 flex-col fixed left-0 top-0 border-r border-border/50 bg-background/50 backdrop-blur-xl z-30">
      <TeacherNav />
    </aside>
  )
}
