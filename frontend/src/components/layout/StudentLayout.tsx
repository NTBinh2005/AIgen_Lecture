import type { ReactNode } from 'react'
import { StudentDock } from './StudentDock'
import { StudentHeader } from './StudentHeader'
import { PageTransition } from '@/components/common/PageTransition'
import { PlanRequiredBanner } from '@/components/common/PlanRequiredBanner'

interface StudentLayoutProps {
  children: ReactNode
}

export function StudentLayout({ children }: StudentLayoutProps) {
  return (
    <div className="min-h-screen bg-background relative overflow-x-clip">
      {/* Dark mode ambient orbs */}
      <div className="pointer-events-none fixed inset-0 z-0 overflow-hidden" aria-hidden="true">
        <div className="animate-blob absolute -top-40 -right-40 w-96 h-96 rounded-full bg-primary/8 blur-[120px] dark:bg-primary/13" />
        <div className="animate-blob-delay-2 absolute bottom-0 -left-40 w-80 h-80 rounded-full bg-primary/8 blur-[100px] dark:bg-primary/12" />
        <div className="animate-blob-delay-4 absolute top-1/2 right-1/3 w-72 h-72 rounded-full bg-indigo-500/5 blur-[100px] dark:bg-indigo-500/9" />
      </div>

      {/* Main Content Area */}
      <div className="flex flex-col min-h-screen relative z-10">
        <StudentHeader />

        {/* Chừa khoảng trống dưới cùng để StudentDock (cao ~70px kể cả lề) không che nội dung cuối trang */}
        <main className="flex-1 px-4 pt-4 pb-28 lg:px-8 lg:pt-8">
          <PlanRequiredBanner />
          <PageTransition>{children}</PageTransition>
        </main>
      </div>

      <StudentDock />
    </div>
  )
}
