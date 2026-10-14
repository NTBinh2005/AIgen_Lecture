import { useState } from 'react'
import { Link, useParams, useSearchParams } from 'react-router-dom'
import { ArrowLeft, CalendarDays, ClipboardList, Hash, PlaySquare, User, Video } from 'lucide-react'
import {
  useClassAssignments, useClassDetail, useClassLectures, useClassLiveSessions, useClassSchedules,
} from '@/hooks/useStudentData'
import { AssignmentCard } from '@/components/student/AssignmentCard'
import { LiveSessionCard } from '@/components/student/LiveSessionCard'
import { ScheduleList } from '@/components/student/ScheduleList'
import { CardSkeleton, EmptyState, ErrorState, Pill } from '@/components/student/StudentUi'
import { formatDate, getErrorStatus } from '@/lib/format'
import { cn } from '@/lib/utils'

const TABS = [
  { id: 'lectures', label: 'Bài giảng', icon: PlaySquare },
  { id: 'quizzes', label: 'Bài kiểm tra', icon: ClipboardList },
  { id: 'live', label: 'Buổi học', icon: Video },
  { id: 'schedule', label: 'Lịch', icon: CalendarDays },
] as const

type TabId = (typeof TABS)[number]['id']

export default function StudentClassDetailPage() {
  const { classId: classIdParam } = useParams<{ classId: string }>()
  const classId = Number(classIdParam)
  const [searchParams, setSearchParams] = useSearchParams()
  const initialTab = (TABS.find(t => t.id === searchParams.get('tab'))?.id ?? 'lectures') as TabId
  const [tab, setTab] = useState<TabId>(initialTab)

  const detail = useClassDetail(classId)
  const lectures = useClassLectures(classId)
  const assignments = useClassAssignments(classId)
  const live = useClassLiveSessions(classId)
  const schedules = useClassSchedules(classId)

  const selectTab = (id: TabId) => {
    setTab(id)
    setSearchParams(id === 'lectures' ? {} : { tab: id }, { replace: true })
  }

  const counts: Record<TabId, number | undefined> = {
    lectures: lectures.data?.length,
    quizzes: assignments.data?.length,
    live: live.data?.length,
    schedule: schedules.data?.length,
  }

  if (detail.isError) {
    const status = getErrorStatus(detail.error)
    return (
      <div className="mx-auto max-w-3xl space-y-4">
        <BackLink />
        <ErrorState message={status === 403 || status === 404 ? 'Bạn không thuộc lớp này hoặc lớp không tồn tại.' : undefined} />
      </div>
    )
  }

  const c = detail.data

  return (
    <div className="mx-auto max-w-6xl space-y-6">
      <BackLink />

      {/* ── Thông tin lớp ── */}
      <div className="relative overflow-hidden rounded-3xl border border-border/50 bg-linear-to-br from-primary/10 via-card to-violet-500/10 p-5 sm:p-8">
        {c ? (
          <>
            <div className="flex flex-wrap items-center gap-2">
              {c.semester && <Pill tone="blue">{c.semester}</Pill>}
              {c.status !== 'ACTIVE' && <Pill tone="muted">{c.status === 'CLOSED' ? 'Đã đóng' : c.status}</Pill>}
            </div>
            <h1 className="mt-3 text-2xl font-bold tracking-tight text-foreground sm:text-3xl">{c.className}</h1>
            {c.description && <p className="mt-2 max-w-2xl text-muted-foreground">{c.description}</p>}
            <dl className="mt-4 flex flex-wrap gap-x-6 gap-y-2 text-sm text-muted-foreground">
              <div className="flex items-center gap-1.5"><User size={15} aria-hidden="true" /><dt className="sr-only">Giáo viên</dt><dd>{c.teacherName}</dd></div>
              <div className="flex items-center gap-1.5"><Hash size={15} aria-hidden="true" /><dt className="sr-only">Mã lớp</dt><dd className="font-mono">{c.classCode}</dd></div>
              <div className="flex items-center gap-1.5"><CalendarDays size={15} aria-hidden="true" /><dt className="sr-only">Thời gian</dt><dd>{formatDate(c.startsAt)} – {formatDate(c.endsAt)}</dd></div>
            </dl>
          </>
        ) : (
          <div className="space-y-3">
            <div className="h-5 w-24 animate-pulse rounded-full bg-muted" />
            <div className="h-8 w-2/3 animate-pulse rounded-lg bg-muted" />
            <div className="h-4 w-1/2 animate-pulse rounded-lg bg-muted" />
          </div>
        )}
      </div>

      {/* ── Tabs ── */}
      <div className="-mx-4 overflow-x-auto px-4 scrollbar-none sm:mx-0 sm:px-0" role="tablist" aria-label="Nội dung lớp">
        <div className="inline-flex min-w-full gap-1 rounded-2xl bg-muted/60 p-1 sm:min-w-0">
          {TABS.map(t => (
            <button
              key={t.id}
              role="tab"
              aria-selected={tab === t.id}
              onClick={() => selectTab(t.id)}
              className={cn(
                'flex min-h-10 flex-1 items-center justify-center gap-2 whitespace-nowrap rounded-xl px-4 text-sm font-medium transition-all sm:flex-none',
                tab === t.id ? 'bg-background text-foreground shadow-sm' : 'text-muted-foreground hover:text-foreground',
              )}
            >
              <t.icon size={16} aria-hidden="true" />
              {t.label}
              {counts[t.id] !== undefined && counts[t.id]! > 0 && (
                <span className="rounded-full bg-primary/10 px-1.5 text-xs font-semibold text-primary">{counts[t.id]}</span>
              )}
            </button>
          ))}
        </div>
      </div>

      {/* ── Nội dung tab ── */}
      <div role="tabpanel">
        {tab === 'lectures' && (
          lectures.isLoading ? <CardSkeleton /> : (lectures.data ?? []).length === 0 ? (
            <EmptyState icon={PlaySquare} title="Lớp chưa có bài giảng nào" description="Giáo viên sẽ giao bài giảng cho lớp sau." />
          ) : (
            <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 xl:grid-cols-3">
              {lectures.data!.map(l => (
                <Link
                  key={l.lectureId}
                  to={`/student/lectures/${l.lectureId}`}
                  className="group flex flex-col overflow-hidden rounded-2xl border border-border/50 bg-card transition-all hover:border-primary/30 hover:shadow-lg dark:bg-card/70"
                >
                  <div className="flex aspect-[16/7] items-center justify-center bg-linear-to-br from-primary/15 via-violet-500/10 to-indigo-500/15">
                    <div className="flex h-12 w-12 items-center justify-center rounded-2xl border border-border/50 bg-background/70 shadow-sm transition-transform group-hover:scale-110">
                      <PlaySquare size={24} className="text-primary" aria-hidden="true" />
                    </div>
                  </div>
                  <div className="p-4">
                    <p className="line-clamp-2 font-semibold text-foreground group-hover:text-primary">{l.title}</p>
                    <p className="mt-1 text-xs text-muted-foreground">Giao ngày {formatDate(l.assignedAt)}</p>
                  </div>
                </Link>
              ))}
            </div>
          )
        )}

        {tab === 'quizzes' && (
          assignments.isLoading ? <CardSkeleton /> : (assignments.data ?? []).length === 0 ? (
            <EmptyState icon={ClipboardList} title="Chưa có bài kiểm tra nào" />
          ) : (
            <div className="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-3">
              {assignments.data!.map(a => <AssignmentCard key={a.assignmentId} assignment={a} />)}
            </div>
          )
        )}

        {tab === 'live' && (
          live.isLoading ? <CardSkeleton count={2} className="md:grid-cols-1 xl:grid-cols-1" /> : (live.data ?? []).length === 0 ? (
            <EmptyState icon={Video} title="Chưa có buổi học nào" />
          ) : (
            <div className="space-y-3">
              {[...live.data!].sort((a, b) => a.startsAt.localeCompare(b.startsAt)).map(s => (
                <LiveSessionCard key={s.sessionId} session={s} showClass={false} />
              ))}
            </div>
          )
        )}

        {tab === 'schedule' && (
          schedules.isLoading ? <CardSkeleton count={2} className="md:grid-cols-1 xl:grid-cols-1" /> : (schedules.data ?? []).length === 0 ? (
            <EmptyState icon={CalendarDays} title="Lớp chưa có lịch học" />
          ) : (
            <ScheduleList schedules={schedules.data!} />
          )
        )}
      </div>
    </div>
  )
}

function BackLink() {
  return (
    <Link to="/student/classes" className="inline-flex min-h-10 items-center gap-1.5 text-sm font-medium text-muted-foreground hover:text-foreground">
      <ArrowLeft size={16} aria-hidden="true" />
      Lớp học của tôi
    </Link>
  )
}
