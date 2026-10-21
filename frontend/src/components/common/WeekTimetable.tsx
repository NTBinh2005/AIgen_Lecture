import { useMemo, useState } from 'react'
import { CalendarDays, ChevronLeft, ChevronRight, MapPin, Repeat, Video } from 'lucide-react'
import { addDays, currentTime, occurrencesBetween, SCHEDULE_TYPE_LABEL, startOfWeek } from '@/lib/schedule'
import { parseDate } from '@/lib/format'
import { cn } from '@/lib/utils'
import type { LiveSession, Schedule } from '@/types/student'

interface WeekTimetableProps {
  schedules: Schedule[]
  /** Buổi học có ngày giờ cụ thể (trực tuyến / tại lớp) */
  sessions?: LiveSession[]
  /** classId → tên lớp; có thì hiện tên lớp trên từng buổi (trang gộp nhiều lớp) */
  classNames?: Map<number, string>
}

interface Item {
  key: string
  start: Date
  end: Date
  title: string
  className?: string
  kind: 'schedule' | 'online' | 'offline'
  location?: string | null
  status?: LiveSession['status']
  /** Lịch cố định có lặp lại (false = lịch một lần) */
  recurring?: boolean
}

const DAY_NAMES = ['Thứ 2', 'Thứ 3', 'Thứ 4', 'Thứ 5', 'Thứ 6', 'Thứ 7', 'Chủ nhật']
const hm = (d: Date) => d.toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' })
const pad = (n: number) => String(n).padStart(2, '0')
const dm = (d: Date) => `${pad(d.getDate())}/${pad(d.getMonth() + 1)}`
const sameDay = (a: Date, b: Date) => a.getFullYear() === b.getFullYear() && a.getMonth() === b.getMonth() && a.getDate() === b.getDate()

const KIND_STYLE: Record<Item['kind'], string> = {
  schedule: 'border-l-primary bg-primary/5',
  online: 'border-l-sky-500 bg-sky-500/5',
  offline: 'border-l-amber-500 bg-amber-500/5',
}

/** Thời khoá biểu theo tuần: lịch cố định (trải theo quy tắc lặp) + buổi học có ngày giờ cụ thể. */
export function WeekTimetable({ schedules, sessions = [], classNames }: WeekTimetableProps) {
  const [today] = useState(currentTime)
  const [weekOffset, setWeekOffset] = useState(0)
  const weekStart = useMemo(() => addDays(startOfWeek(today), weekOffset * 7), [today, weekOffset])
  const days = useMemo(() => Array.from({ length: 7 }, (_, i) => addDays(weekStart, i)), [weekStart])

  const itemsByDay = useMemo(() => {
    const weekEnd = addDays(weekStart, 7)
    const items: Item[] = []
    for (const s of schedules) {
      for (const o of occurrencesBetween(s.startsAt, s.endsAt, s.recurrence, weekStart, weekEnd)) {
        items.push({ key: `s${s.scheduleId}-${o.start.getTime()}`, start: o.start, end: o.end, kind: 'schedule', recurring: !!s.recurrence, title: SCHEDULE_TYPE_LABEL[s.type] ?? s.type, className: classNames?.get(s.classId) })
      }
    }
    for (const s of sessions) {
      const start = parseDate(s.startsAt), end = parseDate(s.endsAt)
      if (!start || !end || start < weekStart || start >= weekEnd) continue
      items.push({ key: `l${s.sessionId}`, start, end, kind: s.type === 'OFFLINE' ? 'offline' : 'online', title: s.title, className: classNames ? s.className : undefined, location: s.location, status: s.status })
    }
    items.sort((a, b) => a.start.getTime() - b.start.getTime())
    return days.map(d => items.filter(i => sameDay(i.start, d)))
  }, [schedules, sessions, classNames, weekStart, days])

  const total = itemsByDay.reduce((n, list) => n + list.length, 0)
  const todayDate = new Date(today)

  return (
    <div className="space-y-3">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <p className="text-sm font-semibold text-foreground">
          {dm(days[0])} – {dm(days[6])}/{days[6].getFullYear()}
          <span className="ml-2 font-normal text-muted-foreground">· {total} buổi</span>
        </p>
        <div className="flex items-center gap-1" role="group" aria-label="Chuyển tuần">
          <button type="button" onClick={() => setWeekOffset(w => w - 1)} aria-label="Tuần trước" className="flex h-9 w-9 items-center justify-center rounded-lg border border-border hover:bg-muted">
            <ChevronLeft size={16} aria-hidden="true" />
          </button>
          <button type="button" onClick={() => setWeekOffset(0)} disabled={weekOffset === 0} className="h-9 rounded-lg border border-border px-3 text-sm font-medium hover:bg-muted disabled:opacity-50">
            Tuần này
          </button>
          <button type="button" onClick={() => setWeekOffset(w => w + 1)} aria-label="Tuần sau" className="flex h-9 w-9 items-center justify-center rounded-lg border border-border hover:bg-muted">
            <ChevronRight size={16} aria-hidden="true" />
          </button>
        </div>
      </div>

      <ol className="grid grid-cols-1 gap-2 md:grid-cols-7">
        {days.map((d, i) => {
          const isToday = sameDay(d, todayDate)
          const list = itemsByDay[i]
          return (
            <li
              key={d.toISOString()}
              aria-label={`${DAY_NAMES[i]} ${dm(d)}: ${list.length} buổi`}
              className={cn(
                'flex min-h-28 flex-col rounded-2xl border bg-card p-2 dark:bg-card/70',
                isToday ? 'border-primary/60 ring-1 ring-primary/30' : 'border-border/50',
                list.length === 0 && 'max-md:min-h-0',
              )}
            >
              <p className={cn('mb-2 flex items-baseline justify-between px-1 text-xs font-semibold md:flex-col md:items-start', isToday ? 'text-primary' : 'text-muted-foreground')}>
                <span>{DAY_NAMES[i]}{isToday && ' · Hôm nay'}</span>
                <span className={cn('text-sm', isToday ? 'text-primary' : 'text-foreground')}>{dm(d)}</span>
              </p>
              {list.length === 0 ? (
                <p className="px-1 text-xs text-muted-foreground/70">Không có buổi</p>
              ) : (
                <ul className="space-y-1.5">
                  {list.map(item => {
                    const cancelled = item.status === 'CANCELLED'
                    const liveNow = item.status === 'LIVE' || item.status === 'OPEN'
                    return (
                      <li key={item.key} className={cn('rounded-lg border-l-[3px] px-2 py-1.5 text-xs', KIND_STYLE[item.kind], cancelled && 'opacity-60')}>
                        <p className="font-semibold tabular-nums text-foreground">{hm(item.start)} – {hm(item.end)}</p>
                        <p className={cn('mt-0.5 font-medium text-foreground', cancelled && 'line-through')}>{item.title}</p>
                        {item.className && <p className="truncate text-muted-foreground">{item.className}</p>}
                        <p className="mt-0.5 flex flex-wrap items-center gap-1 text-muted-foreground">
                          {item.kind === 'schedule' && (item.recurring ? <><Repeat size={11} aria-hidden="true" /> Lịch cố định</> : <><CalendarDays size={11} aria-hidden="true" /> Lịch học</>)}
                          {item.kind === 'online' && <><Video size={11} aria-hidden="true" /> Trực tuyến</>}
                          {item.kind === 'offline' && <><MapPin size={11} aria-hidden="true" /> {item.location || 'Tại lớp'}</>}
                          {cancelled && <span className="font-semibold text-destructive">· Đã hủy</span>}
                          {liveNow && <span className="font-semibold text-red-600 dark:text-red-400">· Đang diễn ra</span>}
                        </p>
                      </li>
                    )
                  })}
                </ul>
              )}
            </li>
          )
        })}
      </ol>
      {total === 0 && (
        <p className="flex items-center justify-center gap-2 text-sm text-muted-foreground">
          <CalendarDays size={15} aria-hidden="true" /> Không có buổi học nào trong tuần.
        </p>
      )}
    </div>
  )
}
