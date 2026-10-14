import { Menu } from 'lucide-react'
import { Link } from 'react-router-dom'
import { useAuthStore } from '@/store/authStore'
import { ThemeToggle } from '@/components/common/ThemeToggle'
import { NotificationBell } from '@/components/student/NotificationBell'

interface StudentHeaderProps {
  onOpenMenu: () => void
}

export function StudentHeader({ onOpenMenu }: StudentHeaderProps) {
  const { user } = useAuthStore()

  return (
    <header className="h-16 border-b border-border/50 bg-background/50 backdrop-blur-xl sticky top-0 z-20 px-4 lg:px-8 flex items-center justify-between">
      {/* Mobile Menu Button - hidden on desktop */}
      <button
        onClick={onOpenMenu}
        className="lg:hidden flex h-10 w-10 -ml-2 items-center justify-center rounded-xl text-muted-foreground hover:text-foreground hover:bg-muted transition-colors"
        aria-label="Mở menu"
      >
        <Menu size={20} />
      </button>

      <div className="flex items-center gap-2 sm:gap-3 ml-auto">
        <ThemeToggle />
        <NotificationBell />

        <div className="w-px h-6 bg-border mx-1 hidden sm:block" />

        {/* User Profile */}
        <Link to="/student/settings" className="flex items-center gap-3 rounded-xl p-1 hover:bg-muted/60 transition-colors">
          <div className="hidden sm:flex flex-col items-end">
            <span className="text-sm font-bold text-foreground leading-none">{user?.name || 'Học sinh'}</span>
            <span className="text-[11px] font-medium text-muted-foreground mt-1">Học viên</span>
          </div>
          <div className="w-9 h-9 rounded-full bg-linear-to-tr from-blue-600 to-indigo-600 flex items-center justify-center text-white font-bold shadow-md">
            {user?.name?.charAt(0).toUpperCase() || 'H'}
          </div>
        </Link>
      </div>
    </header>
  )
}
