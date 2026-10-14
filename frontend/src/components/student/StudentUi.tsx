/**
 * Khối UI nhỏ dùng chung cho các trang Student: tiêu đề trang, trạng thái rỗng/lỗi, skeleton, badge.
 */
import type { ReactNode } from 'react'
import type { LucideIcon } from 'lucide-react'
import { AlertTriangle } from 'lucide-react'
import { cn } from '@/lib/utils'
import type { LiveSessionStatus, StudentAssignmentStatus } from '@/types/student'

interface PageHeaderProps {
  title: string
  description?: string
  action?: ReactNode
}

export function PageHeader({ title, description, action }: PageHeaderProps) {
  return (
    <div className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
      <div>
        <h1 className="text-2xl font-bold tracking-tight text-foreground sm:text-3xl">{title}</h1>
        {description && <p className="mt-1 text-sm text-muted-foreground sm:text-base">{description}</p>}
      </div>
      {action}
    </div>
  )
}

interface EmptyStateProps {
  icon: LucideIcon
  title: string
  description?: string
  action?: ReactNode
  className?: string
}

export function EmptyState({ icon: Icon, title, description, action, className }: EmptyStateProps) {
  return (
    <div className={cn('rounded-2xl border border-dashed border-border bg-card/50 px-6 py-12 text-center', className)}>
      <Icon className="mx-auto mb-3 h-10 w-10 text-muted-foreground/50" aria-hidden="true" />
      <p className="font-medium text-foreground">{title}</p>
      {description && <p className="mx-auto mt-1 max-w-md text-sm text-muted-foreground">{description}</p>}
      {action && <div className="mt-5 flex justify-center">{action}</div>}
    </div>
  )
}

export function ErrorState({ message = 'Không tải được dữ liệu. Vui lòng thử lại.' }: { message?: string }) {
  return (
    <div className="flex items-start gap-3 rounded-2xl border border-destructive/30 bg-destructive/5 p-4 text-sm text-destructive">
      <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" aria-hidden="true" />
      <p>{message}</p>
    </div>
  )
}

export function CardSkeleton({ count = 3, className }: { count?: number; className?: string }) {
  return (
    <div className={cn('grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-3', className)}>
      {Array.from({ length: count }).map((_, i) => (
        <div key={i} className="space-y-3 rounded-2xl border border-border/50 bg-card p-5">
          <div className="h-4 w-2/3 animate-pulse rounded-lg bg-muted" />
          <div className="h-3 w-1/2 animate-pulse rounded-lg bg-muted" />
          <div className="h-8 w-full animate-pulse rounded-lg bg-muted" />
        </div>
      ))}
    </div>
  )
}

/** Thẻ nền chuẩn cho các khối nội dung. */
export function Panel({ className, children }: { className?: string; children: ReactNode }) {
  return (
    <div className={cn('rounded-2xl border border-border/50 bg-card p-5 dark:bg-card/70 dark:backdrop-blur-sm', className)}>
      {children}
    </div>
  )
}

type Tone = 'blue' | 'amber' | 'emerald' | 'red' | 'muted' | 'violet'

const TONE_CLASSES: Record<Tone, string> = {
  blue: 'bg-blue-500/10 text-blue-600 dark:bg-blue-500/15 dark:text-blue-400',
  amber: 'bg-amber-500/10 text-amber-700 dark:bg-amber-500/15 dark:text-amber-400',
  emerald: 'bg-emerald-500/10 text-emerald-700 dark:bg-emerald-500/15 dark:text-emerald-400',
  red: 'bg-red-500/10 text-red-600 dark:bg-red-500/15 dark:text-red-400',
  violet: 'bg-violet-500/10 text-violet-600 dark:bg-violet-500/15 dark:text-violet-400',
  muted: 'bg-muted text-muted-foreground',
}

export function Pill({ tone, children, pulse = false }: { tone: Tone; children: ReactNode; pulse?: boolean }) {
  return (
    <span className={cn('inline-flex items-center gap-1.5 whitespace-nowrap rounded-full px-2.5 py-0.5 text-xs font-semibold', TONE_CLASSES[tone])}>
      {pulse && <span className="h-1.5 w-1.5 animate-pulse rounded-full bg-current" aria-hidden="true" />}
      {children}
    </span>
  )
}

const ASSIGNMENT_STATUS: Record<StudentAssignmentStatus, { label: string; tone: Tone }> = {
  TODO: { label: 'Chưa làm', tone: 'blue' },
  IN_PROGRESS: { label: 'Đang làm', tone: 'amber' },
  COMPLETED: { label: 'Đã nộp', tone: 'emerald' },
  OVERDUE: { label: 'Quá hạn', tone: 'red' },
}

export function AssignmentStatusPill({ status }: { status: StudentAssignmentStatus }) {
  const s = ASSIGNMENT_STATUS[status] ?? { label: status, tone: 'muted' as Tone }
  return <Pill tone={s.tone}>{s.label}</Pill>
}

const LIVE_STATUS: Record<LiveSessionStatus, { label: string; tone: Tone }> = {
  SCHEDULED: { label: 'Sắp diễn ra', tone: 'blue' },
  OPEN: { label: 'Đã mở phòng', tone: 'violet' },
  LIVE: { label: 'Đang diễn ra', tone: 'red' },
  ENDED: { label: 'Đã kết thúc', tone: 'muted' },
  CANCELLED: { label: 'Đã hủy', tone: 'muted' },
}

export function LiveStatusPill({ status }: { status: LiveSessionStatus }) {
  const s = LIVE_STATUS[status] ?? { label: status, tone: 'muted' as Tone }
  return <Pill tone={s.tone} pulse={status === 'LIVE'}>{s.label}</Pill>
}
