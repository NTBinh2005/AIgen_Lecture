import { useEffect, useState } from 'react'
import type { ReactNode } from 'react'
import { useLocation } from 'react-router-dom'
import { X } from 'lucide-react'
import { StudentNav, StudentSidebar } from './StudentSidebar'
import { StudentHeader } from './StudentHeader'

interface StudentLayoutProps {
  children: ReactNode
}

export function StudentLayout({ children }: StudentLayoutProps) {
  const location = useLocation()
  // Menu mobile gắn với trang đang mở → tự đóng khi đổi trang (kể cả bấm Back)
  const [menuPath, setMenuPath] = useState<string | null>(null)
  const menuOpen = menuPath === location.pathname
  const setMenuOpen = (open: boolean) => setMenuPath(open ? location.pathname : null)

  // Esc để đóng menu mobile
  useEffect(() => {
    if (!menuOpen) return
    const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape') setMenuPath(null) }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [menuOpen])

  return (
    <div className="min-h-screen bg-background relative overflow-hidden">
      {/* Dark mode ambient orbs */}
      <div className="pointer-events-none fixed inset-0 z-0 overflow-hidden" aria-hidden="true">
        <div className="animate-blob absolute -top-40 -right-40 w-96 h-96 rounded-full bg-blue-500/8 blur-[120px] dark:bg-blue-500/13" />
        <div className="animate-blob-delay-2 absolute bottom-0 -left-40 w-80 h-80 rounded-full bg-primary/8 blur-[100px] dark:bg-primary/12" />
        <div className="animate-blob-delay-4 absolute top-1/2 right-1/3 w-72 h-72 rounded-full bg-indigo-500/5 blur-[100px] dark:bg-indigo-500/9" />
      </div>

      {/* Sidebar - fixed on large screens */}
      <StudentSidebar />

      {/* Mobile drawer */}
      {menuOpen && (
        <div className="lg:hidden fixed inset-0 z-40" role="dialog" aria-modal="true" aria-label="Menu">
          <div className="absolute inset-0 bg-black/40 backdrop-blur-sm animate-in fade-in" onClick={() => setMenuOpen(false)} />
          <aside className="absolute left-0 top-0 h-full w-72 max-w-[85vw] flex flex-col bg-background border-r border-border/50 shadow-2xl animate-in slide-in-from-left duration-200">
            <button
              onClick={() => setMenuOpen(false)}
              className="absolute right-3 top-3 flex h-10 w-10 items-center justify-center rounded-xl text-muted-foreground hover:bg-muted hover:text-foreground"
              aria-label="Đóng menu"
            >
              <X size={18} />
            </button>
            <StudentNav onNavigate={() => setMenuOpen(false)} />
          </aside>
        </div>
      )}

      {/* Main Content Area */}
      <div className="lg:pl-72 flex flex-col min-h-screen transition-all duration-300 relative z-10">
        <StudentHeader onOpenMenu={() => setMenuOpen(true)} />

        {/* Main Content */}
        <main className="flex-1 p-4 lg:p-8 animate-in fade-in duration-500">
          {children}
        </main>
      </div>
    </div>
  )
}
