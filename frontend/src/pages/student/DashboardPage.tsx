import { Link } from 'react-router-dom'
import { motion } from 'framer-motion'
import { ArrowRight, CalendarDays, ClipboardList, PlaySquare, Sparkles, Users } from 'lucide-react'
import type { LucideIcon } from 'lucide-react'
import { useAuthStore } from '@/store/authStore'
import { useAllAssignments, useAllClassLectures, useMyClasses, useMyLiveSessions } from '@/hooks/useStudentData'
import { AnimatedNumber } from '@/components/common/AnimatedNumber'
import { AssignmentCard } from '@/components/student/AssignmentCard'
import { LiveSessionCard } from '@/components/student/LiveSessionCard'
import { JoinClassDialog } from '@/components/student/JoinClassDialog'
import { CardSkeleton, EmptyState } from '@/components/student/StudentUi'
import { classGradient } from '@/lib/classColors'
import { formatDate, formatDateTime, formatTime, formatWeekday, parseDate } from '@/lib/format'

const containerVariants = {
  hidden: { opacity: 0 },
  show: { opacity: 1, transition: { staggerChildren: 0.08 } },
}

const itemVariants = {
  hidden: { opacity: 0, y: 16 },
  show: { opacity: 1, y: 0, transition: { type: 'spring' as const, stiffness: 300, damping: 24 } },
}

interface StatCardProps {
  icon: LucideIcon
  label: string
  value: number | string
  sub: string
  tone: string
  to: string
}

function StatCard({ icon: Icon, label, value, sub, tone, to }: StatCardProps) {
  return (
    <motion.div variants={itemVariants}>
      <Link
        to={to}
        className="bento-card group block h-full rounded-2xl border border-border/50 bg-card shadow-sm p-4 transition-all hover:border-primary/30 dark:bg-card/70 sm:p-5"
      >
        <div className="flex items-start justify-between gap-2">
          <p className="text-sm font-semibold text-muted-foreground">{label}</p>
          <div className={`flex h-10 w-10 shrink-0 items-center justify-center rounded-xl ${tone}`}>
            <Icon size={20} aria-hidden="true" />
          </div>
        </div>
        <p className="mt-1 font-heading text-3xl font-extrabold tracking-tight text-foreground sm:text-4xl"><AnimatedNumber value={value} /></p>
        <p className="mt-1 text-xs text-muted-foreground">{sub}</p>
      </Link>
    </motion.div>
  )
}

function SectionHeader({ title, to, linkLabel = 'Xem tất cả' }: { title: string; to?: string; linkLabel?: string }) {
  return (
    <div className="mb-4 flex items-center justify-between gap-3">
      <h2 className="text-xl font-bold text-foreground">{title}</h2>
      {to && (
        <Link to={to} className="group/link flex shrink-0 items-center gap-1 text-sm font-semibold text-primary hover:text-primary/80">
          {linkLabel}
          <ArrowRight size={15} className="transition-transform group-hover/link:translate-x-0.5" aria-hidden="true" />
        </Link>
      )}
    </div>
  )
}

interface NextStep {
  eyebrow: string
  title: string
  detail: string
  label: string
  to: string
}

function NextStepCard({ step }: { step: NextStep }) {
  return (
    <div className="w-full shrink-0 rounded-2xl bg-white/12 p-5 ring-1 ring-white/20 backdrop-blur-sm lg:w-96">
      <p className="text-xs font-semibold uppercase tracking-wider text-white/80">{step.eyebrow}</p>
      <p className="mt-1 line-clamp-2 font-heading text-lg font-bold leading-snug">{step.title}</p>
      <p className="mt-1 truncate text-sm text-white/85">{step.detail}</p>
      <Link
        to={step.to}
        className="group/next mt-4 inline-flex min-h-11 items-center gap-1.5 rounded-xl bg-white px-4 text-sm font-semibold text-violet-700 transition-colors hover:bg-white/90"
      >
        {step.label}
        <ArrowRight size={15} className="transition-transform group-hover/next:translate-x-0.5" aria-hidden="true" />
      </Link>
    </div>
  )
}

export default function StudentDashboard() {
  const { user } = useAuthStore()
  const classes = useMyClasses()
  const assignments = useAllAssignments()
  const lectures = useAllClassLectures()
  const live = useMyLiveSessions()

  const firstName = user?.name?.split(' ').pop() || 'bạn'
  const now = new Date()

  const todo = assignments.data
    .filter(a => a.status === 'OPEN' && (a.studentStatus === 'TODO' || a.studentStatus === 'IN_PROGRESS'))
    .sort((a, b) => (a.closeAt ?? '9999').localeCompare(b.closeAt ?? '9999'))

  const upcoming = (live.data ?? [])
    .filter(s => s.status === 'LIVE' || s.status === 'OPEN' || (s.status === 'SCHEDULED' && (parseDate(s.endsAt) ?? now) >= now))
    .sort((a, b) => {
      // Buổi đang diễn ra lên đầu, sau đó theo giờ bắt đầu
      const rank = (st: string) => (st === 'LIVE' ? 0 : st === 'OPEN' ? 1 : 2)
      return rank(a.status) - rank(b.status) || a.startsAt.localeCompare(b.startsAt)
    })

  const noClasses = !classes.isLoading && (classes.data?.length ?? 0) === 0

  // Việc nên làm ngay: buổi học đang mở → bài kiểm tra gần hạn → buổi học kế tiếp → bài giảng mới
  const nextSession = upcoming[0]
  const nextQuiz = todo.find(a => (parseDate(a.openAt) ?? now) <= now)
  const nextLecture = lectures.data[0]
  let nextStep: NextStep | null = null
  if (nextSession && nextSession.status !== 'SCHEDULED') {
    nextStep = { eyebrow: 'Đang diễn ra', title: nextSession.title, detail: `${nextSession.className} · ${formatTime(nextSession.startsAt)} – ${formatTime(nextSession.endsAt)}`, label: 'Vào lớp ngay', to: '/student/schedule' }
  } else if (nextQuiz) {
    nextStep = { eyebrow: 'Bài kiểm tra cần làm', title: nextQuiz.quizTitle, detail: nextQuiz.closeAt ? `${nextQuiz.className} · Hạn ${formatDateTime(nextQuiz.closeAt)}` : nextQuiz.className, label: nextQuiz.studentStatus === 'IN_PROGRESS' ? 'Làm tiếp' : 'Bắt đầu làm', to: `/student/quizzes/${nextQuiz.assignmentId}` }
  } else if (nextSession) {
    nextStep = { eyebrow: 'Buổi học tiếp theo', title: nextSession.title, detail: `${nextSession.className} · ${formatWeekday(nextSession.startsAt)}, ${formatTime(nextSession.startsAt)}`, label: 'Xem lịch học', to: '/student/schedule' }
  } else if (nextLecture) {
    nextStep = { eyebrow: 'Bài giảng mới', title: nextLecture.title, detail: nextLecture.className, label: 'Xem bài giảng', to: `/student/lectures/${nextLecture.lectureId}` }
  }

  return (
    <motion.div className="space-y-6 lg:space-y-8" variants={containerVariants} initial="hidden" animate="show">
      {/* ── HERO ── nền gradient cố định ở cả 2 mode để chữ trắng luôn đủ tương phản */}
      <motion.section
        variants={itemVariants}
        className="relative overflow-hidden rounded-3xl bg-linear-to-br from-violet-700 via-indigo-700 to-indigo-600 p-6 text-white shadow-lg shadow-indigo-700/20 sm:p-8"
      >
        <div className="pointer-events-none absolute inset-0" aria-hidden="true">
          <div className="absolute -right-16 -top-24 h-72 w-72 rounded-full bg-fuchsia-400/25 blur-3xl" />
          <div className="absolute -bottom-32 left-1/3 h-72 w-72 rounded-full bg-sky-400/20 blur-3xl" />
          {/* Từ màn lg, thẻ việc cần làm nằm đúng vị trí này và sẽ che mất hình */}
          <Sparkles className={`absolute right-6 top-4 h-32 w-32 text-white/10 ${nextStep ? 'lg:hidden' : ''}`} />
        </div>

        <div className="relative flex flex-col gap-6 lg:flex-row lg:items-center lg:justify-between">
          <div className="min-w-0">
            <p className="text-sm font-medium capitalize text-white/80">
              {now.toLocaleDateString('vi-VN', { weekday: 'long', day: 'numeric', month: 'long' })}
            </p>
            <h1 className="mt-1 text-3xl font-extrabold tracking-tight md:text-4xl xl:text-5xl">
              Chào {firstName} 👋
            </h1>
            <p className="mt-2 max-w-xl text-white/85">
              {todo.length > 0
                ? `Bạn có ${todo.length} bài kiểm tra cần hoàn thành.`
                : 'Tiếp tục hành trình học tập của bạn hôm nay.'}
            </p>
            {!noClasses && <JoinClassDialog className="mt-5 min-h-11 bg-white/15 text-white ring-1 ring-white/30 hover:bg-white/25" />}
          </div>
          {nextStep && <NextStepCard step={nextStep} />}
        </div>
      </motion.section>

      {noClasses ? (
        <motion.div variants={itemVariants}>
          <EmptyState
            icon={Users}
            title="Bạn chưa tham gia lớp học nào"
            description="Nhập mã lớp giáo viên gửi để xem bài giảng, bài kiểm tra và lịch học của lớp."
            action={<JoinClassDialog size="lg" />}
            className="py-16"
          />
        </motion.div>
      ) : (
        <>
          {/* ── STATS ── */}
          <motion.div variants={containerVariants} className="grid grid-cols-2 gap-3 sm:gap-4 lg:grid-cols-4">
            <StatCard icon={Users} label="Lớp đang học" value={classes.data?.length ?? '—'} sub="Đã ghi danh" tone="bg-primary/10 text-primary" to="/student/classes" />
            <StatCard icon={ClipboardList} label="Bài cần làm" value={assignments.isLoading ? '—' : todo.length} sub="Bài kiểm tra đang mở" tone="bg-violet-500/10 text-violet-600 dark:text-violet-400" to="/student/classes" />
            <StatCard icon={CalendarDays} label="Buổi học sắp tới" value={live.isLoading ? '—' : upcoming.length} sub="Live và tại lớp" tone="bg-amber-500/10 text-amber-600 dark:text-amber-400" to="/student/schedule" />
            <StatCard icon={PlaySquare} label="Bài giảng" value={lectures.isLoading ? '—' : lectures.data.length} sub="Được giao qua lớp" tone="bg-emerald-500/10 text-emerald-600 dark:text-emerald-400" to="/student/lectures" />
          </motion.div>

          <div className="grid grid-cols-1 gap-6 lg:gap-8 xl:grid-cols-[minmax(0,1fr)_24rem]">
            {/* ── BUỔI HỌC SẮP TỚI ── đứng đầu trên mobile, thành cột phải từ màn xl */}
            <motion.section variants={itemVariants} className="xl:col-start-2 xl:row-start-1">
              <SectionHeader title="Buổi học sắp tới" to="/student/schedule" />
              {live.isLoading ? (
                <CardSkeleton count={2} className="md:grid-cols-1 xl:grid-cols-1" />
              ) : upcoming.length === 0 ? (
                <EmptyState icon={CalendarDays} title="Chưa có buổi học nào sắp tới" />
              ) : (
                <div className="space-y-3">
                  {upcoming.slice(0, 3).map(s => <LiveSessionCard key={s.sessionId} session={s} compact />)}
                </div>
              )}
            </motion.section>

            <div className="min-w-0 space-y-6 lg:space-y-8 xl:col-start-1 xl:row-start-1">
              {/* ── BÀI KIỂM TRA ── */}
              <motion.section variants={itemVariants}>
                <SectionHeader title="Bài kiểm tra cần làm" />
                {assignments.isLoading ? (
                  <CardSkeleton count={2} className="xl:grid-cols-2" />
                ) : todo.length === 0 ? (
                  <EmptyState icon={ClipboardList} title="Không có bài kiểm tra nào cần làm" description="Bạn đã hoàn thành hết. Tuyệt vời!" />
                ) : (
                  <div className="grid grid-cols-1 gap-4 md:grid-cols-2 2xl:grid-cols-3">
                    {todo.slice(0, 6).map(a => <AssignmentCard key={a.assignmentId} assignment={a} classLabel={a.className} />)}
                  </div>
                )}
              </motion.section>

              {/* ── BÀI GIẢNG MỚI ── */}
              <motion.section variants={itemVariants}>
                <SectionHeader title="Bài giảng mới được giao" to="/student/lectures" />
                {lectures.isLoading ? (
                  <CardSkeleton count={2} className="xl:grid-cols-2" />
                ) : lectures.data.length === 0 ? (
                  <EmptyState icon={PlaySquare} title="Chưa có bài giảng nào được giao" />
                ) : (
                  <div className="grid grid-cols-1 gap-3 md:grid-cols-2 2xl:grid-cols-3">
                    {lectures.data.slice(0, 6).map(l => (
                      <Link
                        key={`${l.classId}-${l.lectureId}`}
                        to={`/student/lectures/${l.lectureId}`}
                        className="bento-card group flex items-center gap-3 rounded-2xl border border-border/50 bg-card shadow-sm p-3 transition-all hover:border-primary/30 dark:bg-card/70"
                      >
                        <div className={`flex h-14 w-14 shrink-0 items-center justify-center rounded-xl bg-linear-to-br ${classGradient(l.classId)} text-white`}>
                          <PlaySquare size={22} aria-hidden="true" />
                        </div>
                        <div className="min-w-0">
                          <p className="truncate font-semibold text-foreground group-hover:text-primary">{l.title}</p>
                          <p className="truncate text-xs text-muted-foreground">{l.className} · Giao {formatDate(l.assignedAt)}</p>
                        </div>
                      </Link>
                    ))}
                  </div>
                )}
              </motion.section>
            </div>
          </div>
        </>
      )}
    </motion.div>
  )
}
