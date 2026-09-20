import type { ReactNode } from 'react'
import { AdminSidebar } from './AdminSidebar'
import { AdminHeader } from './AdminHeader'

interface AdminLayoutProps {
  children: ReactNode
}

export function AdminLayout({ children }: AdminLayoutProps) {
  return (
    <div className="min-h-screen bg-background relative overflow-hidden selection:bg-violet-600/30">
      {/* Dark mode ambient orbs — violet/purple cho Admin */}
      <div className="pointer-events-none fixed inset-0 z-0 overflow-hidden" aria-hidden="true">
        <div className="animate-blob absolute -top-40 -left-20 w-[500px] h-[500px] rounded-full bg-violet-500/8 blur-[130px] dark:bg-violet-500/14" />
        <div className="animate-blob-delay-2 absolute -bottom-40 -right-20 w-96 h-96 rounded-full bg-primary/8 blur-[100px] dark:bg-primary/12" />
        <div className="animate-blob-delay-4 absolute top-1/3 left-1/2 w-80 h-80 rounded-full bg-purple-500/5 blur-[100px] dark:bg-purple-500/9" />
      </div>

      <AdminSidebar />
      <div className="flex-1 flex flex-col lg:pl-72 w-full transition-all duration-300 relative z-10">
        <AdminHeader />
        <main className="flex-1 p-4 lg:p-8 animate-in fade-in duration-500">
          <div className="mx-auto max-w-7xl">
            {children}
          </div>
        </main>
      </div>
    </div>
  )
}
