import { useState } from 'react'
import { Link, useParams, useSearchParams } from 'react-router-dom'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import {
  Archive, ArrowLeft, CalendarDays, ClipboardList, Hash, Loader2, Lock, Pencil, PlaySquare, Rocket, Users, Video,
} from 'lucide-react'
import { Button } from '@/components/ui/button'
import { useTeacherClass, teacherKeys } from '@/hooks/useTeacherData'
import { activateClass, archiveClass, closeClass } from '@/api/teacherClassApi'
import { ClassFormDialog } from '@/components/teacher/ClassFormDialog'
import { ClassStatusPill } from '@/components/teacher/ClassStatusPill'
import { FormError } from '@/components/teacher/FormKit'
import { StudentsTab } from '@/components/teacher/class/StudentsTab'
import { LecturesTab } from '@/components/teacher/class/LecturesTab'
import { QuizzesTab } from '@/components/teacher/class/QuizzesTab'
import { LiveTab } from '@/components/teacher/class/LiveTab'
import { ScheduleTab } from '@/components/teacher/class/ScheduleTab'
import { CardSkeleton, ErrorState } from '@/components/student/StudentUi'
import { formatDate, getErrorMessage } from '@/lib/format'
import { cn } from '@/lib/utils'

const TABS = [
  { id: 'students', label: 'Học sinh', icon: Users },
  { id: 'lectures', label: 'Bài giảng', icon: PlaySquare },
  { id: 'quizzes', label: 'Bài kiểm tra', icon: ClipboardList },
  { id: 'live', label: 'Buổi học', icon: Video },
  { id: 'schedule', label: 'Lịch', icon: CalendarDays },
] as const

type TabId = (typeof TABS)[number]['id']

export default function TeacherClassDetailPage() {
  const classId = Number(useParams<{ classId: string }>().classId)
  const [searchParams, setSearchParams] = useSearchParams()
  const [tab, setTab] = useState<TabId>(() => TABS.find(t => t.id === searchParams.get('tab'))?.id ?? 'students')
  const data = useTeacherClass(classId)
  const queryClient = useQueryClient()

  const lifecycle = useMutation({
    mutationFn: (action: 'activate' | 'close' | 'archive') =>
      action === 'activate' ? activateClass(classId) : action === 'close' ? closeClass(classId) : archiveClass(classId),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: teacherKeys.all }),
  })

  const selectTab = (id: TabId) => {
    setTab(id)
    setSearchParams(id === 'students' ? {} : { tab: id }, { replace: true })
  }

  if (data.detail.isError) {
    return (
      <div className="mx-auto max-w-3xl space-y-4">
        <BackLink />
        <ErrorState message="Không tìm thấy lớp hoặc bạn không phụ trách lớp này." />
      </div>
    )
  }

  const c = data.detail.data
  const counts: Record<TabId, number | undefined> = {
    students: data.students.data?.filter(s => s.status === 'ACTIVE').length,
    lectures: data.lectures.data?.length,
    quizzes: data.assignments.data?.length,
    live: data.live.data?.length,
    schedule: data.schedules.data?.length,
  }

  const confirmLifecycle = (action: 'activate' | 'close' | 'archive', message: string) => {
    if (window.confirm(message)) lifecycle.mutate(action)
  }

  return (
    <div className="mx-auto max-w-6xl space-y-6">
      <BackLink />

      {/* ── Thông tin lớp ── */}
      <div className="rounded-3xl border border-border/50 bg-linear-to-br from-primary/10 via-card to-violet-500/10 p-5 sm:p-8">
        {c ? (
          <div className="flex flex-col gap-5 lg:flex-row lg:items-start lg:justify-between">
            <div className="min-w-0">
              <div className="flex flex-wrap items-center gap-2">
                <ClassStatusPill status={c.status} />
                {c.semester && <span className="text-sm text-muted-foreground">{c.semester}</span>}
              </div>
              <h1 className="mt-2 text-2xl font-bold tracking-tight text-foreground sm:text-3xl">{c.className}</h1>
              {c.description && <p className="mt-2 max-w-2xl text-muted-foreground">{c.description}</p>}
              <p className="mt-3 flex flex-wrap gap-x-5 gap-y-1 text-sm text-muted-foreground">
                <span className="flex items-center gap-1.5"><Hash size={14} aria-hidden="true" /><span className="font-mono">{c.classCode}</span></span>
                <span className="flex items-center gap-1.5"><CalendarDays size={14} aria-hidden="true" />{formatDate(c.startsAt)} – {formatDate(c.endsAt)}</span>
              </p>
            </div>
            <div className="flex flex-wrap gap-2">
              {c.status !== 'ARCHIVED' && (
                <ClassFormDialog
                  existing={c}
                  trigger={<Button variant="outline" className="h-10 rounded-xl"><Pencil size={15} aria-hidden="true" /> Sửa</Button>}
                />
              )}
              {c.status === 'DRAFT' && (
                <Button className="h-10 rounded-xl" disabled={lifecycle.isPending} onClick={() => confirmLifecycle('activate', 'Kích hoạt lớp? Học sinh sẽ tham gia được bằng mã lớp.')}>
                  {lifecycle.isPending ? <Loader2 className="h-4 w-4 animate-spin" /> : <Rocket size={15} aria-hidden="true" />} Kích hoạt
                </Button>
              )}
              {c.status === 'ACTIVE' && (
                <Button variant="outline" className="h-10 rounded-xl" disabled={lifecycle.isPending} onClick={() => confirmLifecycle('close', 'Đóng lớp? Lớp sẽ không nhận thêm học sinh.')}>
                  <Lock size={15} aria-hidden="true" /> Đóng lớp
                </Button>
              )}
              {c.status === 'CLOSED' && (
                <Button variant="outline" className="h-10 rounded-xl" disabled={lifecycle.isPending} onClick={() => confirmLifecycle('archive', 'Lưu trữ lớp này?')}>
                  <Archive size={15} aria-hidden="true" /> Lưu trữ
                </Button>
              )}
            </div>
          </div>
        ) : (
          <div className="space-y-3">
            <div className="h-5 w-24 animate-pulse rounded-full bg-muted" />
            <div className="h-8 w-2/3 animate-pulse rounded-lg bg-muted" />
          </div>
        )}
        {lifecycle.isError && <div className="mt-4"><FormError message={getErrorMessage(lifecycle.error)} /></div>}
        {c?.status === 'DRAFT' && (
          <p className="mt-4 text-sm text-amber-700 dark:text-amber-400">Lớp đang ở trạng thái nháp: cần kích hoạt trước khi thêm học sinh.</p>
        )}
      </div>

      {/* ── Tabs ── */}
      <div className="-mx-4 overflow-x-auto px-4 scrollbar-none sm:mx-0 sm:px-0" role="tablist" aria-label="Quản lý lớp">
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
              {!!counts[t.id] && <span className="rounded-full bg-primary/10 px-1.5 text-xs font-semibold text-primary">{counts[t.id]}</span>}
            </button>
          ))}
        </div>
      </div>

      <div role="tabpanel">
        {!c ? <CardSkeleton count={2} /> : (
          <>
            {tab === 'students' && (data.students.isLoading ? <CardSkeleton count={2} /> : <StudentsTab classInfo={c} students={data.students.data ?? []} />)}
            {tab === 'lectures' && (data.lectures.isLoading ? <CardSkeleton count={2} /> : <LecturesTab classId={classId} lectures={data.lectures.data ?? []} />)}
            {tab === 'quizzes' && (data.assignments.isLoading ? <CardSkeleton count={2} /> : <QuizzesTab classId={classId} assignments={data.assignments.data ?? []} />)}
            {tab === 'live' && (data.live.isLoading ? <CardSkeleton count={2} /> : <LiveTab classId={classId} sessions={data.live.data ?? []} students={data.students.data ?? []} />)}
            {tab === 'schedule' && (data.schedules.isLoading ? <CardSkeleton count={2} /> : <ScheduleTab classId={classId} schedules={data.schedules.data ?? []} />)}
          </>
        )}
      </div>
    </div>
  )
}

function BackLink() {
  return (
    <Link to="/teacher/classes" className="inline-flex min-h-10 items-center gap-1.5 text-sm font-medium text-muted-foreground hover:text-foreground">
      <ArrowLeft size={16} aria-hidden="true" /> Lớp học
    </Link>
  )
}
