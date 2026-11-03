import { Menu, LogOut, X } from 'lucide-react'
import { ThemeToggle } from '@/components/common/ThemeToggle'
import { NotificationBell } from '@/components/student/NotificationBell'
import { UserAccountDropdown } from './UserAccountDropdown'
import { adminNavItems, isAdminNavActive } from './adminNav'
import { useAuthStore } from '@/store/authStore'
import { useState } from 'react'
import { Link, useLocation } from 'react-router-dom'

export function AdminHeader() {
  const { logout } = useAuthStore()
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

        </div>

        <div className="flex items-center gap-2 sm:gap-4">
          <ThemeToggle />
          
          <NotificationBell />

          <div className="w-px h-6 bg-border/50 mx-1 hidden sm:block"></div>

          {/* User Account Dropdown with Wallet, Settings & Logout */}
          <UserAccountDropdown />
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
              <Link to="/admin" className="flex items-center gap-2.5 group" onClick={() => setIsMobileMenuOpen(false)}>
                <img src="/logo-icon.png" alt="EduAdmin Logo" className="w-8 h-8 object-contain" />
                <span className="font-bold text-xl tracking-tight">
                  Edu<span className="text-violet-600">Admin</span>
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
              {adminNavItems.map((item) => {
                const isActive = isAdminNavActive(location.pathname, item.href)
                return (
                  <Link
                    key={item.href}
                    to={item.href}
                    onClick={() => setIsMobileMenuOpen(false)}
                    className={`flex items-center gap-3 px-3.5 py-3 rounded-xl transition-all duration-200 ${
                      isActive
                        ? 'bg-violet-600/10 text-violet-600 font-semibold'
                        : 'text-muted-foreground hover:bg-muted/50 hover:text-foreground'
                    }`}
                  >
                    <item.icon size={20} className={isActive ? 'text-violet-600' : 'text-muted-foreground'} aria-hidden="true" />
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
