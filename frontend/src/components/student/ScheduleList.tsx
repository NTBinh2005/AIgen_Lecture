import { CalendarDays, Repeat } from 'lucide-react'
import { Pill } from './StudentUi'
import { formatTime, formatWeekday } from '@/lib/format'
import type { Schedule, ScheduleType } from '@/types/student'

const TYPE_LABEL: Record<ScheduleType, string> = {
  LECTURE: 'Lý thuyết',
  LAB: 'Thực hành',
  EXAM: 'Kiểm tra',
  MEETING: 'Họp',
  OTHER: 'Khác',
}

const WEEKDAYS: Record<string, string> = {
  MO: 'Thứ 2', TU: 'Thứ 3', WE: 'Thứ 4', TH: 'Thứ 5', FR: 'Thứ 6', SA: 'Thứ 7', SU: 'Chủ nhật',
}

/** Đọc RRULE đơn giản (FREQ=WEEKLY;BYDAY=MO,WE) thành chữ. */
function describeRecurrence(rule: string | null): string | null {
  if (!rule) return null
  const parts = Object.fromEntries(rule.split(';').map(p => p.split('=') as [string, string]))
  if (parts.FREQ === 'WEEKLY') {
    const days = (parts.BYDAY ?? '').split(',').map(d => WEEKDAYS[d]).filter(Boolean)
    return days.length ? `Hằng tuần: ${days.join(', ')}` : 'Hằng tuần'
  }
  if (parts.FREQ === 'DAILY') return 'Hằng ngày'
  return rule
}

interface ScheduleListProps {
  schedules: Schedule[]
  /** classId → tên lớp (ScheduleResponse không có tên lớp) */
  classNames?: Map<number, string>
}

export function ScheduleList({ schedules, classNames }: ScheduleListProps) {
  const sorted = [...schedules].sort((a, b) => a.startsAt.localeCompare(b.startsAt))
  return (
    <ul className="divide-y divide-border/60 overflow-hidden rounded-2xl border border-border/50 bg-card dark:bg-card/70">
      {sorted.map(s => {
        const recurrence = describeRecurrence(s.recurrence)
        return (
          <li key={s.scheduleId} className="flex items-center gap-4 p-4">
            <div className="flex h-11 w-11 shrink-0 items-center justify-center rounded-xl bg-primary/10 text-primary">
              <CalendarDays size={20} aria-hidden="true" />
            </div>
            <div className="min-w-0 flex-1">
              <div className="flex flex-wrap items-center gap-2">
                <p className="font-semibold capitalize text-foreground">{formatWeekday(s.startsAt)}</p>
                <Pill tone="muted">{TYPE_LABEL[s.type] ?? s.type}</Pill>
              </div>
              <p className="mt-0.5 flex flex-wrap items-center gap-x-3 text-sm text-muted-foreground">
                <span>{formatTime(s.startsAt)} – {formatTime(s.endsAt)}</span>
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
  )
}
