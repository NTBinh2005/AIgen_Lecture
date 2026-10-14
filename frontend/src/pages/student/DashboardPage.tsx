import { Link } from 'react-router-dom'
import { motion } from 'framer-motion'
import { ArrowRight, CalendarDays, ClipboardList, PlaySquare, Users } from 'lucide-react'
import type { LucideIcon } from 'lucide-react'
import { useAuthStore } from '@/store/authStore'
import { useAllAssignments, useAllClassLectures, useMyClasses, useMyLiveSessions } from '@/hooks/useStudentData'
import { AssignmentCard } from '@/components/student/AssignmentCard'
import { LiveSessionCard } from '@/components/student/LiveSessionCard'
import { JoinClassDialog } from '@/components/student/JoinClassDialog'
import { CardSkeleton, EmptyState } from '@/components/student/StudentUi'
import { formatDate, parseDate } from '@/lib/format'

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
        className="group flex h-full items-center gap-4 rounded-2xl border border-border/50 bg-card p-4 transition-all hover:border-primary/30 hover:shadow-lg dark:bg-card/70 sm:block sm:p-5"
      >
        <div className={`flex h-11 w-11 shrink-0 items-center justify-center rounded-xl ${tone} sm:mb-4 sm:h-12 sm:w-12`}>
          <Icon size={22} aria-hidden="true" />
        </div>
        <div>
          <p className="text-sm font-medium text-muted-foreground">{label}</p>
          <p className="text-2xl font-bold text-foreground sm:text-3xl">{value}</p>
          <p className="mt-0.5 text-xs text-muted-foreground">{sub}</p>
        </div>
      </Link>
    </motion.div>
  )
}

function SectionHeader({ title, to, linkLabel = 'Xem tất cả' }: { title: string; to?: string; linkLabel?: string }) {
  return (
    <div className="mb-4 flex items-center justify-between gap-3">
      <h2 className="text-lg font-bold text-foreground sm:text-xl">{title}</h2>
      {to && (
        <Link to={to} className="group/link flex items-center gap-1 text-sm font-semibold text-primary hover:text-primary/80">
          {linkLabel}
          <ArrowRight size={15} className="transition-transform group-hover/link:translate-x-0.5" aria-hidden="true" />
        </Link>
      )}
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

  return (
    <motion.div className="mx-auto max-w-6xl space-y-8" variants={containerVariants} initial="hidden" animate="show">
      {/* ── HEADER ── */}
      <motion.div variants={itemVariants} className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <p className="mb-1 text-sm font-medium capitalize text-muted-foreground">
            {now.toLocaleDateString('vi-VN', { weekday: 'long', day: 'numeric', month: 'long' })}
          </p>
          <h1 className="text-3xl font-bold tracking-tight md:text-4xl">
            Chào <span className="text-primary">{firstName}</span> 👋
          </h1>
          <p className="mt-2 text-muted-foreground">
            {todo.length > 0
              ? `Bạn có ${todo.length} bài kiểm tra cần hoàn thành.`
              : 'Tiếp tục hành trình học tập của bạn hôm nay.'}
          </p>
        </div>
        <JoinClassDialog />
      </motion.div>

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
          <motion.div variants={containerVariants} className="grid grid-cols-1 gap-3 sm:grid-cols-2 sm:gap-4 lg:grid-cols-4">
            <StatCard icon={Users} label="Lớp đang học" value={classes.data?.length ?? '—'} sub="Đã ghi danh" tone="bg-primary/10 text-primary" to="/student/classes" />
            <StatCard icon={ClipboardList} label="Bài cần làm" value={assignments.isLoading ? '—' : todo.length} sub="Bài kiểm tra đang mở" tone="bg-violet-500/10 text-violet-600 dark:text-violet-400" to="/student/classes" />
            <StatCard icon={CalendarDays} label="Buổi học sắp tới" value={live.isLoading ? '—' : upcoming.length} sub="Live và tại lớp" tone="bg-amber-500/10 text-amber-600 dark:text-amber-400" to="/student/schedule" />
            <StatCard icon={PlaySquare} label="Bài giảng" value={lectures.isLoading ? '—' : lectures.data.length} sub="Được giao qua lớp" tone="bg-emerald-500/10 text-emerald-600 dark:text-emerald-400" to="/student/lectures" />
          </motion.div>

          {/* ── BUỔI HỌC SẮP TỚI ── */}
          <motion.section variants={itemVariants}>
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

          {/* ── BÀI KIỂM TRA ── */}
          <motion.section variants={itemVariants}>
            <SectionHeader title="Bài kiểm tra cần làm" />
            {assignments.isLoading ? (
              <CardSkeleton count={3} />
            ) : todo.length === 0 ? (
              <EmptyState icon={ClipboardList} title="Không có bài kiểm tra nào cần làm" description="Bạn đã hoàn thành hết. Tuyệt vời!" />
            ) : (
              <div className="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-3">
                {todo.slice(0, 6).map(a => <AssignmentCard key={a.assignmentId} assignment={a} classLabel={a.className} />)}
              </div>
            )}
          </motion.section>

          {/* ── BÀI GIẢNG MỚI ── */}
          <motion.section variants={itemVariants}>
            <SectionHeader title="Bài giảng mới được giao" to="/student/lectures" />
            {lectures.isLoading ? (
              <CardSkeleton count={3} />
            ) : lectures.data.length === 0 ? (
              <EmptyState icon={PlaySquare} title="Chưa có bài giảng nào được giao" />
            ) : (
              <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 xl:grid-cols-3">
                {lectures.data.slice(0, 6).map(l => (
                  <Link
                    key={`${l.classId}-${l.lectureId}`}
                    to={`/student/lectures/${l.lectureId}`}
                    className="group flex items-center gap-3 rounded-2xl border border-border/50 bg-card p-4 transition-all hover:border-primary/30 hover:shadow-md dark:bg-card/70"
                  >
                    <div className="flex h-11 w-11 shrink-0 items-center justify-center rounded-xl bg-linear-to-br from-primary/20 to-violet-500/20 text-primary">
                      <PlaySquare size={20} aria-hidden="true" />
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
        </>
      )}
    </motion.div>
  )
}
