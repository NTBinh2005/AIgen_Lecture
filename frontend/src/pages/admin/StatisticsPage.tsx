import { useMemo } from 'react'
import { Info } from 'lucide-react'
import { BarChartCard } from '@/components/admin/BarChartCard'
import type { BarDatum } from '@/components/admin/BarChartCard'
import { PageHeader } from '@/components/student/StudentUi'
import {
  useAdminClasses, useAdminEnrollments, useAdminQuizzes, useAdminUsers, useAuditLogs, useStatisticsOverview,
} from '@/hooks/useAdminData'
import { ROLE_LABEL, monthLabel } from '@/lib/admin'
import { parseDate } from '@/lib/format'

const CLASS_STATUS_LABEL: Record<string, string> = { ACTIVE: 'Đang hoạt động', DRAFT: 'Nháp', CLOSED: 'Đã đóng', ARCHIVED: 'Lưu trữ' }
const QUIZ_STATUS_LABEL: Record<string, string> = { DRAFT: 'Nháp', REVIEWED: 'Nháp', PUBLISHED: 'Đã xuất bản', CLOSED: 'Đã đóng', ARCHIVED: 'Lưu trữ' }

function countBy<T>(items: T[], key: (t: T) => string, order?: string[]): BarDatum[] {
  const m = new Map<string, number>()
  items.forEach(i => m.set(key(i), (m.get(key(i)) ?? 0) + 1))
  const labels = order ? order.filter(o => m.has(o)) : [...m.keys()]
  return labels.map(label => ({ label, value: m.get(label)! }))
}

/** Khoá "YYYY-MM" của n tháng gần nhất, cũ → mới */
function lastMonths(n: number): string[] {
  const now = new Date()
  return Array.from({ length: n }, (_, i) => {
    const d = new Date(now.getFullYear(), now.getMonth() - (n - 1 - i), 1)
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`
  })
}

/**
 * Thống kê tính từ dữ liệu thật (users, lớp, ghi danh, quiz, audit log).
 * Không dùng GET /statistics/charts vì backend đang trả số viết cứng (FIX.md #16b).
 */
export default function AdminStatisticsPage() {
  const overview = useStatisticsOverview()
  const users = useAdminUsers()
  const classes = useAdminClasses()
  const enrollments = useAdminEnrollments()
  const quizzes = useAdminQuizzes()
  const logs = useAuditLogs({})

  const charts = useMemo(() => {
    const months = lastMonths(6)
    const signups = new Map(months.map(m => [m, 0]))
    ;(users.data ?? []).forEach(u => {
      const k = u.createdAt?.slice(0, 7)
      if (k && signups.has(k)) signups.set(k, signups.get(k)! + 1)
    })

    const classNames = new Map((classes.data ?? []).map(c => [c.classId, c.className]))
    const perClass = countBy((enrollments.data ?? []).filter(e => e.status === 'ACTIVE'), e => classNames.get(e.classId) ?? e.className)
      .sort((a, b) => b.value - a.value)
      .slice(0, 8)

    // Hoạt động 7 ngày gần nhất theo audit log
    const days = Array.from({ length: 7 }, (_, i) => {
      const d = new Date()
      d.setHours(0, 0, 0, 0)
      d.setDate(d.getDate() - (6 - i))
      return d
    })
    const activity = days.map(d => ({
      label: d.toLocaleDateString('vi-VN', { weekday: 'short', day: '2-digit', month: '2-digit' }),
      value: (logs.data ?? []).filter(l => {
        const t = parseDate(l.createdAt)
        return t != null && t >= d && t < new Date(d.getTime() + 86_400_000)
      }).length,
    }))

    return {
      signups: months.map(m => ({ label: monthLabel(m), value: signups.get(m)! })),
      roles: countBy(users.data ?? [], u => ROLE_LABEL[u.role] ?? u.role, ['Học sinh', 'Giáo viên', 'Quản trị viên']),
      classStatus: countBy(classes.data ?? [], c => CLASS_STATUS_LABEL[c.status] ?? c.status, ['Đang hoạt động', 'Nháp', 'Đã đóng', 'Lưu trữ']),
      quizStatus: countBy(quizzes.data?.content ?? [], q => QUIZ_STATUS_LABEL[q.status] ?? q.status, ['Nháp', 'Đã xuất bản', 'Đã đóng', 'Lưu trữ']),
      perClass,
      activity,
    }
  }, [users.data, classes.data, enrollments.data, quizzes.data, logs.data])

  const o = overview.data
  const kpis = [
    { label: 'Người dùng', value: o?.totalUsers, sub: `${o?.totalStudents ?? '—'} học sinh · ${o?.totalTeachers ?? '—'} giáo viên` },
    { label: 'Bài giảng', value: o?.totalLectures, sub: `${o?.totalAiGeneratedLectures ?? '—'} tạo bằng AI` },
    { label: 'Lớp học', value: classes.data?.length, sub: `${classes.data?.filter(c => c.status === 'ACTIVE').length ?? '—'} đang hoạt động` },
    { label: 'Lượt ghi danh', value: enrollments.data?.filter(e => e.status === 'ACTIVE').length, sub: 'Đang học' },
  ]

  return (
    <div className="mx-auto max-w-6xl space-y-6">
      <PageHeader title="Thống kê" description="Số liệu tính trực tiếp từ dữ liệu hệ thống." />

      <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
        {kpis.map(k => (
          <div key={k.label} className="rounded-2xl border border-border/50 bg-card p-4 dark:bg-card/70">
            <p className="text-sm text-muted-foreground">{k.label}</p>
            <p className="mt-1 text-3xl font-bold tabular-nums text-foreground">{k.value ?? '—'}</p>
            <p className="mt-1 text-xs text-muted-foreground">{k.sub}</p>
          </div>
        ))}
      </div>

      <p className="flex gap-2 rounded-xl bg-muted/60 p-3 text-xs text-muted-foreground">
        <Info size={14} className="mt-0.5 shrink-0" aria-hidden="true" />
        Chưa hiển thị lượt tương tác, chi phí AI và uptime vì backend đang trả số cố định cho các chỉ số này (FIX.md #16b).
      </p>

      <div className="grid gap-4 lg:grid-cols-2">
        <BarChartCard title="Người dùng mới" description="6 tháng gần nhất" unit="người dùng" data={charts.signups} loading={users.isLoading} />
        <BarChartCard title="Hoạt động hệ thống" description="Số sự kiện trong nhật ký, 7 ngày gần nhất" unit="sự kiện" data={charts.activity} loading={logs.isLoading} />
        <BarChartCard title="Người dùng theo vai trò" unit="người dùng" data={charts.roles} horizontal loading={users.isLoading} />
        <BarChartCard title="Sĩ số các lớp" description="Học sinh đang học, 8 lớp đông nhất" unit="học sinh" data={charts.perClass} horizontal loading={enrollments.isLoading} emptyText="Chưa có học sinh nào ghi danh" />
        <BarChartCard title="Trạng thái lớp học" unit="lớp" data={charts.classStatus} horizontal loading={classes.isLoading} />
        <BarChartCard title="Trạng thái bài kiểm tra" unit="quiz" data={charts.quizStatus} horizontal loading={quizzes.isLoading} />
      </div>
    </div>
  )
}
