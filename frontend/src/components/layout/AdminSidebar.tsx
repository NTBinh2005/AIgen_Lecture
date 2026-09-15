import { Link, useLocation } from 'react-router-dom'
import {
  LayoutDashboard,
  Users,
  BrainCircuit,
  Activity,
  Settings,
  LogOut,
  Sparkles,
  BarChart2
} from 'lucide-react'
import { useAuthStore } from '@/store/authStore'

const navItems = [
  {
    title: 'Tổng quan',
    href: '/admin',
    icon: LayoutDashboard,
  },
  {
    title: 'Quản lý người dùng',
    href: '/admin/users',
    icon: Users,
  },
  {
    title: 'Cấu hình AI',
    href: '/admin/ai-settings',
    icon: BrainCircuit,
  },
  {
    title: 'Nhật ký hệ thống',
    href: '/admin/logs',
    icon: Activity,
  },
  {
    title: 'Thống kê & Phân tích',
    href: '/admin/statistics',
    icon: BarChart2,
  },
  {
    title: 'Cài đặt hệ thống',
    href: '/admin/settings',
    icon: Settings,
  },
]

export function AdminSidebar() {
  const location = useLocation()
  const { logout } = useAuthStore()

  return (
    <aside className="hidden lg:flex h-screen w-72 flex-col fixed left-0 top-0 border-r border-border/50 bg-background/50 backdrop-blur-xl z-30">
      {/* Logo Area */}
      <div className="h-16 flex items-center px-6 border-b border-border/50">
        <Link to="/admin" className="flex items-center gap-2.5 group">
          <div className="w-8 h-8 rounded-xl bg-violet-600 flex items-center justify-center text-primary-foreground shadow-lg shadow-violet-600/25 group-hover:scale-105 transition-transform">
            <Sparkles size={18} aria-hidden="true" />
          </div>
          <span className="font-bold text-xl tracking-tight text-foreground">
            Edu<span className="text-violet-600">Admin</span>
          </span>
        </Link>
      </div>

      {/* Navigation */}
      <div className="flex-1 overflow-y-auto py-6 px-4 flex flex-col gap-1.5 custom-scrollbar">
        {navItems.map((item) => {
          const isActive =
            item.href === '/admin'
              ? location.pathname === '/admin'
              : location.pathname.startsWith(item.href)
          return (
            <Link
              key={item.href}
              to={item.href}
              className={`flex items-center gap-3 px-3.5 py-3 rounded-xl transition-all duration-200 group relative ${
                isActive
                  ? 'bg-violet-600/10 text-violet-600 font-semibold'
                  : 'text-muted-foreground hover:bg-muted/50 hover:text-foreground'
              }`}
            >
              {isActive && (
                <div className="absolute left-0 top-1/2 -translate-y-1/2 w-1 h-6 bg-violet-600 rounded-r-full" />
              )}
              <item.icon size={20} className={isActive ? 'text-violet-600' : 'text-muted-foreground group-hover:text-foreground transition-colors'} aria-hidden="true" />
              <span className="text-sm">{item.title}</span>
            </Link>
          )
        })}
      </div>

      {/* Footer Area (Logout) */}
      <div className="p-4 border-t border-border/50">
        <button
          onClick={logout}
          className="w-full flex items-center gap-3 px-3.5 py-3 rounded-xl text-muted-foreground hover:bg-destructive/10 hover:text-destructive transition-colors group"
        >
          <LogOut size={20} className="group-hover:text-destructive transition-colors" aria-hidden="true" />
          <span className="text-sm font-medium">Đăng xuất</span>
        </button>
      </div>
    </aside>
  )
}
