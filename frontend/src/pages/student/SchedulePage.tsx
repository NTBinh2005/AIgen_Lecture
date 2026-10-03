import { useMemo, useState } from 'react'
import { CalendarDays, History, Video } from 'lucide-react'
import { useMyClasses, useMyLiveSessions, useMySchedules } from '@/hooks/useStudentData'
import { LiveSessionCard } from '@/components/student/LiveSessionCard'
import { ScheduleList } from '@/components/student/ScheduleList'
import { CardSkeleton, EmptyState, ErrorState, PageHeader } from '@/components/student/StudentUi'
import { parseDate } from '@/lib/format'
import { cn } from '@/lib/utils'
import type { LiveSession } from '@/types/student'

function isPast(s: LiveSession, now: Date) {
  return s.status === 'ENDED' || s.status === 'CANCELLED' || (s.status === 'SCHEDULED' && (parseDate(s.endsAt) ?? now) < now)
}

export default function StudentSchedulePage() {
  const [showPast, setShowPast] = useState(false)
  const live = useMyLiveSessions()
  const schedules = useMySchedules()
  const classes = useMyClasses()

  const classNames = useMemo(
    () => new Map((classes.data ?? []).map(c => [c.classId, c.className])),
    [classes.data],
  )

  const { liveNow, upcoming, past } = useMemo(() => {
    const now = new Date()
    const sessions = [...(live.data ?? [])].sort((a, b) => a.startsAt.localeCompare(b.startsAt))
    return {
      liveNow: sessions.filter(s => s.status === 'LIVE' || s.status === 'OPEN'),
      upcoming: sessions.filter(s => s.status === 'SCHEDULED' && !isPast(s, now)),
      past: sessions.filter(s => isPast(s, now)).reverse(),
    }
  }, [live.data])

  return (
    <div className="mx-auto max-w-5xl space-y-8">
      <PageHeader title="Lịch học & Live" description="Buổi học trực tuyến, buổi học tại lớp và lịch cố định của các lớp bạn tham gia." />

      {live.isError && <ErrorState />}

      {/* ── Đang diễn ra ── */}
      {liveNow.length > 0 && (
        <section className="space-y-3">
          <h2 className="flex items-center gap-2 text-lg font-bold text-foreground">
            <span className="h-2.5 w-2.5 animate-pulse rounded-full bg-red-500" aria-hidden="true" />
            Đang diễn ra
          </h2>
          {liveNow.map(s => <LiveSessionCard key={s.sessionId} session={s} />)}
        </section>
      )}

      {/* ── Sắp tới ── */}
      <section className="space-y-3">
        <h2 className="flex items-center gap-2 text-lg font-bold text-foreground">
          <Video size={18} className="text-primary" aria-hidden="true" />
          Buổi học sắp tới
        </h2>
        {live.isLoading ? (
          <CardSkeleton count={2} className="md:grid-cols-1 xl:grid-cols-1" />
        ) : upcoming.length === 0 ? (
          <EmptyState icon={Video} title="Không có buổi học nào sắp tới" />
        ) : (
          upcoming.map(s => <LiveSessionCard key={s.sessionId} session={s} />)
        )}
      </section>

      {/* ── Lịch cố định ── */}
      <section className="space-y-3">
        <h2 className="flex items-center gap-2 text-lg font-bold text-foreground">
          <CalendarDays size={18} className="text-primary" aria-hidden="true" />
          Lịch học của lớp
        </h2>
        {schedules.isLoading ? (
          <CardSkeleton count={2} className="md:grid-cols-1 xl:grid-cols-1" />
        ) : (schedules.data ?? []).length === 0 ? (
          <EmptyState icon={CalendarDays} title="Chưa có lịch học" />
        ) : (
          <ScheduleList schedules={schedules.data!} classNames={classNames} />
        )}
      </section>

      {/* ── Đã qua ── */}
      {past.length > 0 && (
        <section className="space-y-3">
          <button
            onClick={() => setShowPast(v => !v)}
            aria-expanded={showPast}
            className="flex min-h-10 items-center gap-2 text-sm font-semibold text-muted-foreground hover:text-foreground"
          >
            <History size={16} aria-hidden="true" />
            Buổi học đã qua ({past.length})
            <span className={cn('transition-transform', showPast && 'rotate-180')} aria-hidden="true">▾</span>
          </button>
          {showPast && past.map(s => <LiveSessionCard key={s.sessionId} session={s} />)}
        </section>
      )}
    </div>
  )
}
