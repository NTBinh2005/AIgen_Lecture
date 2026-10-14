import { Link } from 'react-router-dom'
import { ArrowRight, CalendarClock, ClipboardList, RotateCcw, Timer } from 'lucide-react'
import { buttonVariants } from '@/components/ui/button'
import { AssignmentStatusPill } from './StudentUi'
import { formatDateTime, parseDate } from '@/lib/format'
import { cn } from '@/lib/utils'
import type { StudentAssignment } from '@/types/student'

interface AssignmentCardProps {
  assignment: StudentAssignment
  className?: string
  /** Hiện tên lớp khi danh sách gom nhiều lớp */
  classLabel?: string
}

function getAction(a: StudentAssignment): { label: string; enabled: boolean; hint?: string } {
  const now = new Date()
  const openAt = parseDate(a.openAt)
  const closeAt = parseDate(a.closeAt)
  const attemptsLeft = a.maxAttempts == null ? Infinity : a.maxAttempts - a.usedAttempts

  if (a.status === 'CLOSED' || (closeAt && closeAt < now)) return { label: 'Xem kết quả', enabled: a.usedAttempts > 0, hint: 'Đã đóng' }
  if (openAt && openAt > now) return { label: 'Chưa mở', enabled: false, hint: `Mở lúc ${formatDateTime(a.openAt)}` }
  if (a.studentStatus === 'IN_PROGRESS') return { label: 'Làm tiếp', enabled: true }
  if (attemptsLeft <= 0) return { label: 'Xem kết quả', enabled: true, hint: 'Hết lượt làm' }
  if (a.usedAttempts > 0) return { label: 'Làm lại', enabled: true }
  return { label: 'Bắt đầu làm', enabled: true }
}

export function AssignmentCard({ assignment: a, className, classLabel }: AssignmentCardProps) {
  const action = getAction(a)
  const resultOnly = action.label === 'Xem kết quả'
  const to = resultOnly ? `/student/quizzes/${a.assignmentId}?view=result` : `/student/quizzes/${a.assignmentId}`

  return (
    <div className={cn('flex h-full flex-col rounded-2xl border border-border/50 bg-card p-5 transition-colors hover:border-primary/30 dark:bg-card/70', className)}>
      <div className="mb-3 flex items-start justify-between gap-3">
        <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-violet-500/10 text-violet-600 dark:bg-violet-500/15 dark:text-violet-400">
          <ClipboardList size={20} aria-hidden="true" />
        </div>
        <AssignmentStatusPill status={a.studentStatus} />
      </div>

      <h3 className="line-clamp-2 font-semibold leading-snug text-foreground">{a.quizTitle}</h3>
      {classLabel && <p className="mt-0.5 text-xs text-muted-foreground">{classLabel}</p>}

      <dl className="mt-3 space-y-1.5 text-sm text-muted-foreground">
        <div className="flex items-center gap-2">
          <CalendarClock size={14} className="shrink-0" aria-hidden="true" />
          <dt className="sr-only">Hạn nộp</dt>
          <dd>Hạn: {formatDateTime(a.closeAt)}</dd>
        </div>
        <div className="flex flex-wrap items-center gap-x-4 gap-y-1.5">
          <span className="flex items-center gap-2">
            <Timer size={14} className="shrink-0" aria-hidden="true" />
            {a.durationMinutes ? `${a.durationMinutes} phút` : 'Không giới hạn'}
          </span>
          <span className="flex items-center gap-2">
            <RotateCcw size={14} className="shrink-0" aria-hidden="true" />
            {a.usedAttempts}/{a.maxAttempts ?? '∞'} lượt
          </span>
        </div>
      </dl>

      <div className="mt-auto pt-4">
        {action.enabled ? (
          <Link
            to={to}
            className={cn(buttonVariants({ variant: resultOnly ? 'outline' : 'default' }), 'h-10 w-full rounded-xl')}
          >
            {action.label}
            <ArrowRight size={15} aria-hidden="true" />
          </Link>
        ) : (
          <div className="flex h-10 items-center justify-center rounded-xl bg-muted text-sm font-medium text-muted-foreground">
            {action.hint ?? action.label}
          </div>
        )}
        {action.enabled && action.hint && <p className="mt-2 text-center text-xs text-muted-foreground">{action.hint}</p>}
      </div>
    </div>
  )
}
