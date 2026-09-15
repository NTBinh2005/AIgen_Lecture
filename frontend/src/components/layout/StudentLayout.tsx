import type { ReactNode } from 'react'
import { StudentSidebar } from './StudentSidebar'
import { StudentHeader } from './StudentHeader'

interface StudentLayoutProps {
  children: ReactNode
}

export function StudentLayout({ children }: StudentLayoutProps) {
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

      {/* Main Content Area */}
      <div className="lg:pl-72 flex flex-col min-h-screen transition-all duration-300 relative z-10">
        <StudentHeader />

        {/* Main Content */}
        <main className="flex-1 p-4 lg:p-8 animate-in fade-in duration-500">
          {children}
        </main>
      </div>
    </div>
  )
}
