import { useState } from 'react'
import { Link } from 'react-router-dom'
import { ArrowRight, CalendarDays, Hash, Plus, Users } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { useTeacherClasses } from '@/hooks/useTeacherData'
import { ClassFormDialog } from '@/components/teacher/ClassFormDialog'
import { ClassStatusPill } from '@/components/teacher/ClassStatusPill'
import { CardSkeleton, EmptyState, ErrorState, PageHeader } from '@/components/student/StudentUi'
import { formatDate } from '@/lib/format'
import { cn } from '@/lib/utils'
import type { ClassStatus } from '@/types/student'

const FILTERS: Array<{ id: ClassStatus | 'all'; label: string }> = [
  { id: 'all', label: 'Tất cả' },
  { id: 'ACTIVE', label: 'Đang hoạt động' },
  { id: 'DRAFT', label: 'Nháp' },
  { id: 'CLOSED', label: 'Đã đóng' },
  { id: 'ARCHIVED', label: 'Lưu trữ' },
]

export default function TeacherClassesPage() {
  const { data: classes = [], isLoading, isError } = useTeacherClasses()
  const [filter, setFilter] = useState<ClassStatus | 'all'>('all')

  const visible = classes
    .filter(c => filter === 'all' || c.status === filter)
    .sort((a, b) => b.classId - a.classId)

  const createButton = (
    <Button className="h-10 rounded-xl px-4">
      <Plus size={16} aria-hidden="true" /> Tạo lớp
    </Button>
  )

  return (
    <div className="mx-auto max-w-6xl space-y-6">
      <PageHeader
        title="Lớp học"
        description="Quản lý lớp, học sinh, bài giảng và bài kiểm tra của từng lớp."
        action={<ClassFormDialog trigger={createButton} />}
      />

      {classes.length > 0 && (
        <div className="-mx-4 flex gap-2 overflow-x-auto px-4 pb-1 scrollbar-none sm:mx-0 sm:flex-wrap sm:px-0">
          {FILTERS.map(f => {
            const count = f.id === 'all' ? classes.length : classes.filter(c => c.status === f.id).length
            return (
              <button
                key={f.id}
                onClick={() => setFilter(f.id)}
                className={cn(
                  'min-h-9 whitespace-nowrap rounded-full border px-4 text-sm font-medium transition-colors',
                  filter === f.id ? 'border-primary bg-primary text-primary-foreground' : 'border-border bg-card text-muted-foreground hover:text-foreground',
                )}
              >
                {f.label} <span className="opacity-70">({count})</span>
              </button>
            )
          })}
        </div>
      )}

      {isError && <ErrorState />}

      {isLoading ? (
        <CardSkeleton count={3} />
      ) : classes.length === 0 ? (
        <EmptyState
          icon={Users}
          title="Bạn chưa có lớp học nào"
          description="Tạo lớp, kích hoạt rồi gửi mã lớp cho học sinh để các em tự tham gia."
          action={<ClassFormDialog trigger={createButton} />}
          className="py-16"
        />
      ) : visible.length === 0 ? (
        <EmptyState icon={Users} title="Không có lớp nào ở trạng thái này" />
      ) : (
        <div className="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-3">
          {visible.map(c => (
            <Link
              key={c.classId}
              to={`/teacher/classes/${c.classId}`}
              className="group flex flex-col rounded-2xl border border-border/50 bg-card p-5 transition-all hover:border-primary/30 hover:shadow-xl hover:shadow-primary/5 dark:bg-card/70"
            >
              <div className="mb-3 flex items-start justify-between gap-3">
                <div className="flex h-11 w-11 shrink-0 items-center justify-center rounded-xl bg-linear-to-br from-primary to-violet-500 text-lg font-bold text-primary-foreground">
                  {c.className.charAt(0).toUpperCase()}
                </div>
                <ClassStatusPill status={c.status} />
              </div>
              <h3 className="line-clamp-2 text-lg font-bold leading-snug text-foreground group-hover:text-primary">{c.className}</h3>
              <dl className="mt-3 space-y-1.5 text-sm text-muted-foreground">
                <div className="flex items-center gap-2">
                  <Hash size={14} aria-hidden="true" /><dt className="sr-only">Mã lớp</dt>
                  <dd className="font-mono">{c.classCode}</dd>
                  {c.semester && <span className="text-xs">· {c.semester}</span>}
                </div>
                <div className="flex items-center gap-2">
                  <CalendarDays size={14} aria-hidden="true" /><dt className="sr-only">Thời gian</dt>
                  <dd>{formatDate(c.startsAt)} – {formatDate(c.endsAt)}</dd>
                </div>
              </dl>
              <span className="mt-4 flex items-center gap-1 text-sm font-semibold text-primary">
                Quản lý lớp <ArrowRight size={15} className="transition-transform group-hover:translate-x-0.5" aria-hidden="true" />
              </span>
            </Link>
          ))}
        </div>
      )}
    </div>
  )
}
