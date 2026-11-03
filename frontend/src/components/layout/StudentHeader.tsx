import { Menu } from 'lucide-react'
import { ThemeToggle } from '@/components/common/ThemeToggle'
import { NotificationBell } from '@/components/student/NotificationBell'
import { UserAccountDropdown } from '@/components/layout/UserAccountDropdown'

interface StudentHeaderProps {
  onOpenMenu: () => void
}

export function StudentHeader({ onOpenMenu }: StudentHeaderProps) {
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

        {/* User Profile Dropdown with Wallet & Actions */}
        <UserAccountDropdown />
      </div>
    </header>
  )
}
