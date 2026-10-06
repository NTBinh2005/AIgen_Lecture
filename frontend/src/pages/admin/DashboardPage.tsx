import { Link } from 'react-router-dom'
import { Activity, ArrowRight, BarChart3, ClipboardList, School, UserPlus, Users } from 'lucide-react'
import type { LucideIcon } from 'lucide-react'
import { RolePill, UserStatusPill } from '@/components/admin/AdminPills'
import { Pill } from '@/components/student/StudentUi'
import {
  useAdminClasses, useAdminQuizzes, useAdminUsers, useAuditLogs, useStatisticsOverview,
} from '@/hooks/useAdminData'
import { useAuthStore } from '@/store/authStore'
import { AUDIT_ACTION_LABEL } from '@/lib/admin'
import { formatDate, formatDateTime } from '@/lib/format'

interface TileProps {
  icon: LucideIcon
  label: string
  value: number | string | undefined
  sub: string
  to: string
}

function Tile({ icon: Icon, label, value, sub, to }: TileProps) {
  return (
    <Link to={to} className="group rounded-2xl border border-border/50 bg-card p-5 transition-all hover:border-primary/30 hover:shadow-lg dark:bg-card/70">
      <div className="mb-3 flex h-10 w-10 items-center justify-center rounded-xl bg-primary/10 text-primary">
        <Icon size={20} aria-hidden="true" />
      </div>
      <p className="text-sm text-muted-foreground">{label}</p>
      <p className="text-3xl font-bold tabular-nums text-foreground">{value ?? '—'}</p>
      <p className="mt-1 text-xs text-muted-foreground">{sub}</p>
    </Link>
  )
}

function Section({ title, to, children }: { title: string; to: string; children: React.ReactNode }) {
  return (
    <section className="rounded-2xl border border-border/50 bg-card p-5 dark:bg-card/70">
      <div className="mb-3 flex items-center justify-between">
        <h2 className="font-semibold text-foreground">{title}</h2>
        <Link to={to} className="flex items-center gap-1 text-sm font-medium text-primary hover:underline">
          Xem tất cả <ArrowRight size={14} aria-hidden="true" />
        </Link>
      </div>
      {children}
    </section>
  )
}

export default function AdminDashboard() {
  const me = useAuthStore(s => s.user)
  const overview = useStatisticsOverview()
  const users = useAdminUsers()
  const classes = useAdminClasses()
  const quizzes = useAdminQuizzes()
  const logs = useAuditLogs({})

  const recentUsers = [...(users.data ?? [])].sort((a, b) => b.createdAt.localeCompare(a.createdAt)).slice(0, 5)
  const recentLogs = [...(logs.data ?? [])].sort((a, b) => b.createdAt.localeCompare(a.createdAt)).slice(0, 6)
  const userName = new Map((users.data ?? []).map(u => [u.userId, u.name]))
  const locked = (users.data ?? []).filter(u => u.status === 'INACTIVE').length

  return (
    <div className="mx-auto max-w-6xl space-y-6">
      <div>
        <p className="text-sm text-muted-foreground">Bảng điều khiển quản trị</p>
        <h1 className="text-3xl font-bold tracking-tight text-foreground">Xin chào, <span className="text-primary">{me?.name ?? 'Admin'}</span></h1>
      </div>

      <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
        <Tile icon={Users} label="Người dùng" value={overview.data?.totalUsers ?? users.data?.length} sub={`${locked} tài khoản đã khoá`} to="/admin/users" />
        <Tile icon={School} label="Lớp đang hoạt động" value={classes.data?.filter(c => c.status === 'ACTIVE').length} sub={`${classes.data?.length ?? '—'} lớp tất cả`} to="/admin/classes" />
        <Tile icon={BarChart3} label="Bài giảng" value={overview.data?.totalLectures} sub={`${overview.data?.totalAiGeneratedLectures ?? '—'} tạo bằng AI`} to="/admin/statistics" />
        <Tile icon={ClipboardList} label="Bài kiểm tra" value={quizzes.data?.totalElements} sub={`${quizzes.data?.content.filter(q => q.status === 'PUBLISHED').length ?? '—'} đã xuất bản`} to="/admin/quizzes" />
      </div>

      <div className="grid gap-4 lg:grid-cols-2">
        <Section title="Người dùng mới" to="/admin/users">
          {recentUsers.length === 0 ? (
            <p className="py-6 text-center text-sm text-muted-foreground">{users.isLoading ? 'Đang tải...' : 'Chưa có người dùng.'}</p>
          ) : (
            <ul className="divide-y divide-border/50">
              {recentUsers.map(u => (
                <li key={u.userId} className="flex items-center gap-3 py-2.5">
                  <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-linear-to-tr from-primary to-violet-500 text-sm font-bold text-primary-foreground">
                    {u.name.charAt(0).toUpperCase()}
                  </div>
                  <div className="min-w-0 flex-1">
                    <p className="truncate text-sm font-medium text-foreground">{u.name}</p>
                    <p className="truncate text-xs text-muted-foreground">{u.email} · {formatDate(u.createdAt)}</p>
                  </div>
                  <div className="hidden gap-1 sm:flex"><RolePill role={u.role} />{u.status === 'INACTIVE' && <UserStatusPill status={u.status} />}</div>
                </li>
              ))}
            </ul>
          )}
        </Section>

        <Section title="Hoạt động gần đây" to="/admin/logs">
          {recentLogs.length === 0 ? (
            <p className="py-6 text-center text-sm text-muted-foreground">{logs.isLoading ? 'Đang tải...' : 'Chưa có hoạt động.'}</p>
          ) : (
            <ul className="divide-y divide-border/50">
              {recentLogs.map(l => (
                <li key={l.id} className="flex items-center gap-3 py-2.5">
                  <Activity size={16} className="shrink-0 text-muted-foreground" aria-hidden="true" />
                  <div className="min-w-0 flex-1">
                    <p className="truncate text-sm text-foreground">
                      {l.userId != null ? userName.get(l.userId) ?? `User #${l.userId}` : 'Hệ thống'}
                    </p>
                    <p className="text-xs text-muted-foreground">{formatDateTime(l.createdAt)}</p>
                  </div>
                  <Pill tone="muted">{AUDIT_ACTION_LABEL[l.action] ?? l.action}</Pill>
                </li>
              ))}
            </ul>
          )}
        </Section>
      </div>

      <div className="grid gap-3 sm:grid-cols-3">
        {[
          { to: '/admin/users', icon: UserPlus, label: 'Tạo / khoá tài khoản' },
          { to: '/admin/classes', icon: School, label: 'Quản lý lớp học' },
          { to: '/admin/statistics', icon: BarChart3, label: 'Xem thống kê' },
        ].map(a => (
          <Link key={a.to} to={a.to} className="flex min-h-12 items-center gap-3 rounded-xl border border-border/50 bg-card px-4 text-sm font-medium text-foreground hover:border-primary/30 dark:bg-card/70">
            <a.icon size={18} className="text-primary" aria-hidden="true" /> {a.label}
            <ArrowRight size={15} className="ml-auto text-muted-foreground" aria-hidden="true" />
          </Link>
        ))}
      </div>
    </div>
  )
}
