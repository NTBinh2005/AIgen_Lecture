import { Link } from 'react-router-dom'
import { ThemeToggle } from '@/components/common/ThemeToggle'
import { NotificationBell } from '@/components/student/NotificationBell'
import { UserAccountDropdown } from '@/components/layout/UserAccountDropdown'

export function StudentHeader() {
  return (
    <header className="h-16 border-b border-border/50 bg-background/50 backdrop-blur-xl sticky top-0 z-20 px-4 lg:px-8 flex items-center justify-between">
      <Link to="/student" className="flex items-center gap-2.5 group" aria-label="EduMind, về bảng điều khiển">
        <img src="/logo-icon.png" alt="" className="h-9 w-9 object-contain group-hover:scale-105 transition-transform" />
        <span className="font-heading text-xl font-bold tracking-tight text-foreground">
          Edu<span className="text-primary">Mind</span>
        </span>
      </Link>

      <div className="flex items-center gap-2 sm:gap-3">
        <ThemeToggle />
        <NotificationBell />

        <div className="w-px h-6 bg-border mx-1 hidden sm:block" />

        {/* User Profile Dropdown with Wallet & Actions */}
        <UserAccountDropdown />
      </div>
    </header>
  )
}
