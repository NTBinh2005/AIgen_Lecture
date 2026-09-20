import { Menu, Search, Bell, User as UserIcon, LogOut, LayoutDashboard, BookOpen, BarChart3, Settings, Sparkles, X } from 'lucide-react'
import { ThemeToggle } from '@/components/common/ThemeToggle'
import { useAuthStore } from '@/store/authStore'
import { useState } from 'react'
import { Link, useLocation } from 'react-router-dom'

// Cần tạo một mảng nav cho mobile (hoặc export từ Sidebar qua, 
// nhưng để đơn giản ta lặp lại ở đây cho phần Mobile Menu rời)
const navItems = [
  { title: 'Tổng quan', href: '/teacher', icon: LayoutDashboard },
  { title: 'Bài giảng của tôi', href: '/teacher/lectures', icon: BookOpen },
  { title: 'Thống kê & Phân tích', href: '/teacher/analytics', icon: BarChart3 },
  { title: 'Cài đặt', href: '/teacher/settings', icon: Settings },
]

export function TeacherHeader() {
  const { user, logout } = useAuthStore()
  const [isMobileMenuOpen, setIsMobileMenuOpen] = useState(false)
  const location = useLocation()

  return (
    <>
      <header className="h-16 border-b border-border/50 bg-background/80 backdrop-blur-md flex items-center justify-between px-4 lg:px-8 sticky top-0 z-20">
        <div className="flex items-center gap-4">
          <button 
            className="lg:hidden p-2 text-muted-foreground hover:bg-muted rounded-lg transition-colors"
            onClick={() => setIsMobileMenuOpen(true)}
            aria-label="Open menu"
          >
            <Menu size={20} aria-hidden="true" />
          </button>

          {/* Ô tìm kiếm (Ẩn trên mobile siêu nhỏ) */}
          <div className="hidden sm:flex items-center relative group">
            <Search size={18} className="absolute left-3 text-muted-foreground group-focus-within:text-primary transition-colors" aria-hidden="true" />
            <input 
              type="text"
              aria-label="Tìm kiếm bài giảng"
              placeholder="Tìm kiếm bài giảng..."
              className="h-10 pl-10 pr-4 w-64 bg-muted/30 border border-border/50 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-primary/30 transition-all"
            />
          </div>
        </div>

        <div className="flex items-center gap-2 sm:gap-4">
          <ThemeToggle />
          
          <button className="p-2 text-muted-foreground hover:bg-muted rounded-full transition-colors relative" aria-label="Notifications">
            <Bell size={20} aria-hidden="true" />
            <span className="absolute top-1.5 right-2 w-2 h-2 bg-destructive rounded-full border border-background">
              <span className="sr-only">Có thông báo mới</span>
            </span>
          </button>

          <div className="w-px h-6 bg-border/50 mx-1 hidden sm:block"></div>

          {/* User Profile */}
          <div className="flex items-center gap-3">
            <div className="hidden sm:flex flex-col items-end">
              <span className="text-sm font-semibold leading-none">{user?.name || 'Giáo viên'}</span>
              <span className="text-xs text-muted-foreground mt-1">Giảng viên</span>
            </div>
            <div className="w-9 h-9 rounded-full bg-primary/10 border border-primary/20 flex items-center justify-center text-primary overflow-hidden">
              <UserIcon size={18} aria-hidden="true" />
            </div>
          </div>
        </div>
      </header>

      {/* Mobile Menu Overlay */}
      {isMobileMenuOpen && (
        <div className="fixed inset-0 z-50 lg:hidden flex">
          <div 
            className="absolute inset-0 bg-background/80 backdrop-blur-sm"
            onClick={() => setIsMobileMenuOpen(false)}
            aria-hidden="true"
          />
          <div className="relative w-72 h-full bg-background border-r border-border shadow-2xl flex flex-col animate-in slide-in-from-left duration-300">
            <div className="h-16 flex items-center justify-between px-6 border-b border-border/50">
              <Link to="/teacher" className="flex items-center gap-2 group" onClick={() => setIsMobileMenuOpen(false)}>
                <div className="w-8 h-8 rounded-xl bg-primary flex items-center justify-center text-primary-foreground">
                  <Sparkles size={18} aria-hidden="true" />
                </div>
                <span className="font-bold text-xl tracking-tight">
                  Edu<span className="text-primary">Mind</span>
                </span>
              </Link>
              <button 
                onClick={() => setIsMobileMenuOpen(false)}
                className="p-2 text-muted-foreground hover:bg-muted rounded-lg transition-colors"
                aria-label="Close menu"
              >
                <X size={20} aria-hidden="true" />
              </button>
            </div>

            <div className="flex-1 overflow-y-auto py-6 px-4 flex flex-col gap-1.5">
              {navItems.map((item) => {
                const isActive = location.pathname === item.href
                return (
                  <Link
                    key={item.href}
                    to={item.href}
                    onClick={() => setIsMobileMenuOpen(false)}
                    className={`flex items-center gap-3 px-3.5 py-3 rounded-xl transition-all duration-200 ${
                      isActive
                        ? 'bg-primary/10 text-primary font-semibold'
                        : 'text-muted-foreground hover:bg-muted/50 hover:text-foreground'
                    }`}
                  >
                    <item.icon size={20} className={isActive ? 'text-primary' : 'text-muted-foreground'} aria-hidden="true" />
                    <span className="text-sm">{item.title}</span>
                  </Link>
                )
              })}
            </div>

            <div className="p-4 border-t border-border/50">
              <button
                onClick={() => {
                  setIsMobileMenuOpen(false)
                  logout()
                }}
                className="w-full flex items-center gap-3 px-3.5 py-3 rounded-xl text-muted-foreground hover:bg-destructive/10 hover:text-destructive transition-colors"
              >
                <LogOut size={20} aria-hidden="true" />
                <span className="text-sm font-medium">Đăng xuất</span>
              </button>
            </div>
          </div>
        </div>
      )}
    </>
  )
}
