import type { ReactNode } from 'react'
import { TeacherSidebar } from './TeacherSidebar'
import { TeacherHeader } from './TeacherHeader'

interface TeacherLayoutProps {
  children: ReactNode
}

export function TeacherLayout({ children }: TeacherLayoutProps) {
  return (
    <div className="min-h-screen bg-background relative overflow-hidden">
      {/* Dark mode ambient orbs — tạo hiệu ứng nền tối sâu AI Futuristic */}
      <div className="pointer-events-none fixed inset-0 z-0 overflow-hidden" aria-hidden="true">
        <div className="animate-blob absolute -top-40 -left-40 w-96 h-96 rounded-full bg-primary/10 blur-[120px] dark:bg-primary/15" />
        <div className="animate-blob-delay-2 absolute top-1/3 -right-40 w-80 h-80 rounded-full bg-violet-500/8 blur-[100px] dark:bg-violet-500/12" />
        <div className="animate-blob-delay-4 absolute -bottom-40 left-1/3 w-96 h-96 rounded-full bg-indigo-500/6 blur-[120px] dark:bg-indigo-500/10" />
      </div>

      {/* Sidebar - fixed on large screens */}
      <TeacherSidebar />

      {/* Main Content Area */}
      <div className="lg:pl-72 flex flex-col min-h-screen transition-all duration-300 relative z-10">
        <TeacherHeader />

        {/* Main Content */}
        <main className="flex-1 p-4 lg:p-8 animate-in fade-in duration-500">
          {children}
        </main>
      </div>
    </div>
  )
}
