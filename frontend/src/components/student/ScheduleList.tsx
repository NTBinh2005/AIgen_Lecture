import { useMemo } from 'react'
import { CalendarDays, Repeat } from 'lucide-react'
import { Pill } from './StudentUi'
import { formatTime, formatWeekday } from '@/lib/format'
import { currentTime, describeRecurrence, nextOccurrence, SCHEDULE_TYPE_LABEL } from '@/lib/schedule'
import { cn } from '@/lib/utils'
import type { Schedule } from '@/types/student'
import { usePagination } from '@/hooks/usePagination'
import { Pagination } from '@/components/common/Pagination'

interface ScheduleListProps {
  schedules: Schedule[]
  /** classId → tên lớp (ScheduleResponse không có tên lớp) */
  classNames?: Map<number, string>
}

/** Lịch cố định: lịch lặp hiện buổi gần nhất ("Buổi tới"), xếp theo buổi sắp diễn ra; lịch một lần đã qua để cuối. */
export function ScheduleList({ schedules, classNames }: ScheduleListProps) {
  const rows = useMemo(() => {
    const now = currentTime()
    return schedules
      .map(s => ({ s, next: nextOccurrence(s.startsAt, s.endsAt, s.recurrence, now) }))
      .sort((a, b) => Number(a.next?.past ?? true) - Number(b.next?.past ?? true)
        || (a.next?.start.getTime() ?? 0) - (b.next?.start.getTime() ?? 0))
  }, [schedules])
  const pager = usePagination(rows, 15, null)
  return (
  <>
    <ul className="divide-y divide-border/60 overflow-hidden rounded-2xl border border-border/50 bg-card dark:bg-card/70">
      {pager.pageItems.map(({ s, next }) => {
        const recurrence = describeRecurrence(s.recurrence)
        const startIso = next ? next.start.toISOString() : s.startsAt
        const endIso = next ? next.end.toISOString() : s.endsAt
        const past = next?.past ?? false
        return (
          <li key={s.scheduleId} className={cn('flex items-center gap-4 p-4', past && 'opacity-60')}>
            <div className="flex h-11 w-11 shrink-0 items-center justify-center rounded-xl bg-primary/10 text-primary">
              <CalendarDays size={20} aria-hidden="true" />
            </div>
            <div className="min-w-0 flex-1">
              <div className="flex flex-wrap items-center gap-2">
                {recurrence && !past && <span className="text-xs font-medium text-muted-foreground">Buổi tới:</span>}
                <p className="font-semibold capitalize text-foreground">{formatWeekday(startIso)}</p>
                <Pill tone="muted">{SCHEDULE_TYPE_LABEL[s.type] ?? s.type}</Pill>
                {past && <Pill tone="muted">Đã qua</Pill>}
              </div>
              <p className="mt-0.5 flex flex-wrap items-center gap-x-3 text-sm text-muted-foreground">
                <span>{formatTime(startIso)} – {formatTime(endIso)}</span>
                {classNames?.get(s.classId) && <span>{classNames.get(s.classId)}</span>}
                {recurrence && (
                  <span className="flex items-center gap-1"><Repeat size={13} aria-hidden="true" />{recurrence}</span>
                )}
              </p>
            </div>
          </li>
        )
      })}
    </ul>
    <Pagination page={pager.page} totalPages={pager.totalPages} total={pager.total} pageSize={pager.pageSize} onChange={pager.setPage} label="Phân trang lịch học" />
  </>
  )
}
