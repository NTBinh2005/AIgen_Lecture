import { Link } from 'react-router-dom'
import { ArrowRight, CalendarCheck, Users } from 'lucide-react'
import { useMyClasses } from '@/hooks/useStudentData'
import { JoinClassDialog } from '@/components/student/JoinClassDialog'
import { CardSkeleton, EmptyState, ErrorState, PageHeader, Pill } from '@/components/student/StudentUi'
import { formatDate } from '@/lib/format'

/** Gradient theo classId để mỗi lớp có màu nhận diện riêng. */
const GRADIENTS = [
  'from-blue-500 to-indigo-600',
  'from-violet-500 to-fuchsia-600',
  'from-emerald-500 to-teal-600',
  'from-amber-500 to-orange-600',
  'from-rose-500 to-pink-600',
]

export default function StudentClassesPage() {
  const { data: classes = [], isLoading, isError } = useMyClasses()

  return (
    <div className="mx-auto max-w-6xl space-y-6">
      <PageHeader
        title="Lớp học của tôi"
        description="Các lớp bạn đã tham gia. Chọn một lớp để xem bài giảng, bài kiểm tra và lịch học."
        action={<JoinClassDialog />}
      />

      {isError && <ErrorState />}

      {isLoading ? (
        <CardSkeleton count={3} />
      ) : classes.length === 0 ? (
        <EmptyState
          icon={Users}
          title="Bạn chưa tham gia lớp học nào"
          description="Nhập mã lớp giáo viên gửi để bắt đầu."
          action={<JoinClassDialog size="lg" />}
          className="py-16"
        />
      ) : (
        <div className="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-3">
          {classes.map(c => (
            <Link
              key={c.classId}
              to={`/student/classes/${c.classId}`}
              className="group flex flex-col overflow-hidden rounded-2xl border border-border/50 bg-card transition-all hover:border-primary/30 hover:shadow-xl hover:shadow-primary/5 dark:bg-card/70"
            >
              <div className={`relative h-24 bg-linear-to-br ${GRADIENTS[c.classId % GRADIENTS.length]} p-4`}>
                <div className="absolute inset-0 bg-[radial-gradient(circle_at_80%_20%,rgba(255,255,255,0.25),transparent_50%)]" aria-hidden="true" />
                <div className="relative flex items-start justify-between">
                  <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-white/20 text-lg font-bold text-white backdrop-blur-sm">
                    {c.className.charAt(0).toUpperCase()}
                  </div>
                  {c.status === 'COMPLETED' && <Pill tone="muted">Đã hoàn thành</Pill>}
                </div>
              </div>
              <div className="flex flex-1 flex-col p-5">
                <h3 className="line-clamp-2 text-lg font-bold leading-snug text-foreground group-hover:text-primary">
                  {c.className}
                </h3>
                <p className="mt-2 flex items-center gap-1.5 text-sm text-muted-foreground">
                  <CalendarCheck size={14} aria-hidden="true" />
                  Tham gia {formatDate(c.enrolledAt)}
                </p>
                <span className="mt-4 flex items-center gap-1 text-sm font-semibold text-primary">
                  Vào lớp
                  <ArrowRight size={15} className="transition-transform group-hover:translate-x-0.5" aria-hidden="true" />
                </span>
              </div>
            </Link>
          ))}
        </div>
      )}
    </div>
  )
}
