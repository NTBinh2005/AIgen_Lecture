import { Bell, Search, Menu } from 'lucide-react'
import { useAuthStore } from '@/store/authStore'

export function StudentHeader() {
  const { user } = useAuthStore()

  return (
    <header className="h-16 border-b border-border/50 bg-background/50 backdrop-blur-xl sticky top-0 z-20 px-4 lg:px-8 flex items-center justify-between">
      {/* Mobile Menu Button - hidden on desktop */}
      <button className="lg:hidden p-2 -ml-2 text-muted-foreground hover:text-foreground transition-colors">
        <Menu size={20} />
      </button>

      {/* Search Bar - hidden on mobile */}
      <div className="hidden lg:flex items-center max-w-md w-full relative group">
        <Search className="absolute left-3 top-1/2 -translate-y-1/2 text-muted-foreground group-focus-within:text-blue-600 transition-colors" size={18} />
        <input 
          type="text" 
          placeholder="Tìm kiếm bài giảng, khóa học..." 
          className="w-full h-10 pl-10 pr-4 rounded-xl bg-muted/50 border border-transparent focus:border-blue-600/30 focus:bg-background focus:ring-4 focus:ring-blue-600/10 text-sm transition-all"
        />
      </div>

      <div className="flex items-center gap-4 ml-auto lg:ml-0">
        {/* Notifications */}
        <button className="relative p-2 text-muted-foreground hover:text-foreground transition-colors rounded-full hover:bg-muted">
          <Bell size={20} />
          <span className="absolute top-1.5 right-1.5 w-2 h-2 rounded-full bg-blue-600 border-2 border-background" />
        </button>

        <div className="w-px h-6 bg-border mx-2 hidden sm:block" />

        {/* User Profile */}
        <div className="flex items-center gap-3">
          <div className="hidden sm:flex flex-col items-end">
            <span className="text-sm font-bold text-foreground leading-none">{user?.name || 'Học sinh'}</span>
            <span className="text-[11px] font-medium text-muted-foreground mt-1">Học viên</span>
          </div>
          <div className="w-9 h-9 rounded-full bg-gradient-to-tr from-blue-600 to-indigo-600 flex items-center justify-center text-white font-bold shadow-md cursor-pointer hover:shadow-lg transition-shadow">
            {user?.name?.charAt(0) || 'H'}
          </div>
        </div>
      </div>
    </header>
  )
}
