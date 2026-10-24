import { useState } from 'react'
import { CalendarRange, List } from 'lucide-react'
import { WeekTimetable } from './WeekTimetable'
import { ScheduleList } from '@/components/student/ScheduleList'
import { cn } from '@/lib/utils'
import type { LiveSession, Schedule } from '@/types/student'

type View = 'week' | 'list'
const VIEWS: [View, string, typeof List][] = [['week', 'Thời khoá biểu', CalendarRange], ['list', 'Lịch cố định', List]]

/** Lịch học: thời khoá biểu theo tuần (mặc định) hoặc danh sách lịch cố định. */
export function ScheduleViews({ schedules, sessions, classNames }: {
  schedules: Schedule[]
  sessions?: LiveSession[]
  classNames?: Map<number, string>
}) {
  const [view, setView] = useState<View>('week')
  return (
    <div className="space-y-3">
      <div role="tablist" aria-label="Kiểu hiển thị lịch" className="flex w-fit gap-1 rounded-xl bg-muted/60 p-1">
        {VIEWS.map(([id, label, Icon]) => (
          <button
            key={id}
            type="button"
            role="tab"
            aria-selected={view === id}
            onClick={() => setView(id)}
            className={cn(
              'flex h-9 items-center gap-1.5 rounded-lg px-3 text-sm font-medium transition-colors',
              view === id ? 'bg-card text-foreground shadow-sm' : 'text-muted-foreground hover:text-foreground',
            )}
          >
            <Icon size={15} aria-hidden="true" /> {label}
          </button>
        ))}
      </div>
      {view === 'week'
        ? <WeekTimetable schedules={schedules} sessions={sessions} classNames={classNames} />
        : schedules.length === 0
          ? <p className="rounded-2xl border border-dashed border-border/60 p-6 text-center text-sm text-muted-foreground">Chưa có lịch cố định.</p>
          : <ScheduleList schedules={schedules} classNames={classNames} />}
    </div>
  )
}
