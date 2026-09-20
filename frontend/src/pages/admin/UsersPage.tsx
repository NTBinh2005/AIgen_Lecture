import { useState } from 'react'
import { motion, type Variants } from 'framer-motion'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import {
  Users, Search, Shield, ShieldOff, UserCheck, UserX,
  MoreVertical, Filter, ArrowUpRight, ArrowDownRight,
} from 'lucide-react'
import axiosInstance from '@/api/axiosInstance'

// ── Types ─────────────────────────────────────────────────────────────────────

interface UserResponse {
  userId: number
  role: 'TEACHER' | 'STUDENT' | 'ADMIN'
  name: string
  email: string
  status: 'ACTIVE' | 'INACTIVE'
  createdAt: string
}

// ── API helpers ───────────────────────────────────────────────────────────────

async function fetchAllUsers(): Promise<UserResponse[]> {
  const res = await axiosInstance.get<UserResponse[]>('/users')
  return res.data
}

async function patchUser(params: {
  userId: number
  status?: 'ACTIVE' | 'INACTIVE'
  role?: 'TEACHER' | 'STUDENT' | 'ADMIN'
}): Promise<UserResponse> {
  const { userId, ...body } = params
  const res = await axiosInstance.patch<UserResponse>(`/users/${userId}`, body)
  return res.data
}

// ── Animation ─────────────────────────────────────────────────────────────────

const containerVariants: Variants = {
  hidden: { opacity: 0 },
  show: { opacity: 1, transition: { staggerChildren: 0.06 } },
}
const itemVariants: Variants = {
  hidden: { opacity: 0, y: 16 },
  show: { opacity: 1, y: 0, transition: { type: 'spring', stiffness: 300, damping: 24 } },
}

// ── Role / Status Badges ──────────────────────────────────────────────────────

function RoleBadge({ role }: { role: string }) {
  const styles: Record<string, string> = {
    ADMIN: 'bg-amber-500/10 text-amber-600 border-amber-500/20 dark:text-amber-400',
    TEACHER: 'bg-primary/10 text-primary border-primary/20',
    STUDENT: 'bg-emerald-500/10 text-emerald-600 border-emerald-500/20 dark:text-emerald-400',
  }
  const labels: Record<string, string> = {
    ADMIN: 'Admin', TEACHER: 'Giáo viên', STUDENT: 'Học sinh',
  }
  return (
    <span className={`inline-flex px-2.5 py-1 rounded-lg text-xs font-semibold border ${styles[role] ?? ''}`}>
      {labels[role] ?? role}
    </span>
  )
}

function StatusBadge({ status }: { status: string }) {
  return (
    <div className="flex items-center gap-1.5">
      <div className={`w-2 h-2 rounded-full ${status === 'ACTIVE' ? 'bg-emerald-500 animate-pulse' : 'bg-muted-foreground'}`} />
      <span className={`text-xs font-medium ${status === 'ACTIVE' ? 'text-emerald-600 dark:text-emerald-400' : 'text-muted-foreground'}`}>
        {status === 'ACTIVE' ? 'Hoạt động' : 'Bị khoá'}
      </span>
    </div>
  )
}

// ── Dropdown Action Menu ──────────────────────────────────────────────────────

function ActionMenu({
  user,
  onToggleStatus,
  onChangeRole,
}: {
  user: UserResponse
  onToggleStatus: () => void
  onChangeRole: (role: 'TEACHER' | 'STUDENT') => void
}) {
  const [open, setOpen] = useState(false)
  if (user.role === 'ADMIN') return null

  return (
    <div className="relative">
      <button
        id={`action-menu-${user.userId}`}
        onClick={() => setOpen((o) => !o)}
        className="p-1.5 text-muted-foreground hover:text-foreground hover:bg-muted rounded-lg transition-colors"
        aria-label="Thao tác"
      >
        <MoreVertical size={15} />
      </button>
      {open && (
        <>
          <div className="fixed inset-0 z-10" onClick={() => setOpen(false)} />
          <div className="absolute right-0 top-full mt-1 z-20 w-52 rounded-xl border border-border/50 bg-card shadow-xl shadow-black/10 overflow-hidden py-1">
            {/* Toggle status */}
            <button
              onClick={() => { onToggleStatus(); setOpen(false) }}
              className={`w-full flex items-center gap-2.5 px-4 py-2.5 text-sm transition-colors hover:bg-muted/50 ${
                user.status === 'ACTIVE' ? 'text-destructive' : 'text-emerald-600'
              }`}
            >
              {user.status === 'ACTIVE'
                ? <><UserX size={15} /> Khoá tài khoản</>
                : <><UserCheck size={15} /> Mở khoá tài khoản</>}
            </button>
            <div className="h-px bg-border/40 mx-3 my-1" />
            {/* Change role */}
            {user.role !== 'TEACHER' && (
              <button
                onClick={() => { onChangeRole('TEACHER'); setOpen(false) }}
                className="w-full flex items-center gap-2.5 px-4 py-2.5 text-sm hover:bg-muted/50 transition-colors text-foreground"
              >
                <Shield size={15} className="text-primary" /> Đổi thành Giáo viên
              </button>
            )}
            {user.role !== 'STUDENT' && (
              <button
                onClick={() => { onChangeRole('STUDENT'); setOpen(false) }}
                className="w-full flex items-center gap-2.5 px-4 py-2.5 text-sm hover:bg-muted/50 transition-colors text-foreground"
              >
                <ShieldOff size={15} className="text-emerald-600" /> Đổi thành Học sinh
              </button>
            )}
          </div>
        </>
      )}
    </div>
  )
}

// ── Main Component ─────────────────────────────────────────────────────────────

type RoleFilter = 'ALL' | 'TEACHER' | 'STUDENT' | 'ADMIN'
type StatusFilter = 'ALL' | 'ACTIVE' | 'INACTIVE'

export default function UsersPage() {
  const queryClient = useQueryClient()
  const [search, setSearch] = useState('')
  const [roleFilter, setRoleFilter] = useState<RoleFilter>('ALL')
  const [statusFilter, setStatusFilter] = useState<StatusFilter>('ALL')

  const { data: users = [], isLoading } = useQuery({
    queryKey: ['admin-users'],
    queryFn: fetchAllUsers,
  })

  const patchMutation = useMutation({
    mutationFn: patchUser,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['admin-users'] }),
  })

  const filtered = users.filter((u) => {
    const matchSearch =
      u.name.toLowerCase().includes(search.toLowerCase()) ||
      u.email.toLowerCase().includes(search.toLowerCase())
    const matchRole = roleFilter === 'ALL' || u.role === roleFilter
    const matchStatus = statusFilter === 'ALL' || u.status === statusFilter
    return matchSearch && matchRole && matchStatus
  })

  const kpis = [
    {
      label: 'Tổng người dùng', value: users.length,
      change: `+${users.filter(u => u.status === 'ACTIVE').length} hoạt động`,
      isPositive: true, color: 'text-violet-500', bg: 'bg-violet-500/10',
    },
    {
      label: 'Giáo viên', value: users.filter(u => u.role === 'TEACHER').length,
      change: '', isPositive: true, color: 'text-primary', bg: 'bg-primary/10',
    },
    {
      label: 'Học sinh', value: users.filter(u => u.role === 'STUDENT').length,
      change: '', isPositive: true, color: 'text-emerald-500', bg: 'bg-emerald-500/10',
    },
    {
      label: 'Bị khoá', value: users.filter(u => u.status === 'INACTIVE').length,
      change: '', isPositive: false, color: 'text-destructive', bg: 'bg-destructive/10',
    },
  ]

  return (
    <motion.div
      className="space-y-6 max-w-7xl mx-auto"
      variants={containerVariants}
      initial="hidden"
      animate="show"
    >
      {/* ── HEADER ── */}
      <motion.div variants={itemVariants}>
        <p className="text-sm font-medium text-muted-foreground mb-1">Quản trị hệ thống</p>
        <h1 className="text-3xl md:text-4xl font-bold tracking-tight text-foreground">
          Quản lý <span className="text-violet-500">Người dùng</span>
        </h1>
        <p className="text-muted-foreground mt-2">Xem, phân quyền và khoá tài khoản người dùng trong hệ thống.</p>
      </motion.div>

      {/* ── KPI CARDS ── */}
      <motion.div variants={containerVariants} className="grid grid-cols-2 lg:grid-cols-4 gap-4">
        {kpis.map((kpi) => (
          <motion.div
            key={kpi.label}
            variants={itemVariants}
            whileHover={{ y: -3, transition: { type: 'spring', stiffness: 400, damping: 20 } }}
            className="relative overflow-hidden rounded-2xl border border-border/50 bg-card dark:bg-card/70
              dark:backdrop-blur-sm p-5 hover:border-violet-500/25 hover:shadow-lg transition-shadow duration-300"
          >
            <div className="absolute -right-4 -top-4 w-16 h-16 rounded-full bg-violet-500/5 blur-xl" />
            <div className="relative z-10">
              <div className={`w-10 h-10 rounded-xl flex items-center justify-center mb-3 ${kpi.bg} ${kpi.color}`}>
                <Users size={20} />
              </div>
              <p className="text-xs text-muted-foreground font-medium mb-1">{kpi.label}</p>
              <p className="text-2xl font-bold text-foreground">{kpi.value}</p>
              {kpi.change && (
                <p className={`text-xs mt-1 flex items-center gap-1 ${kpi.isPositive ? 'text-emerald-500' : 'text-destructive'}`}>
                  {kpi.isPositive ? <ArrowUpRight size={12} /> : <ArrowDownRight size={12} />}
                  {kpi.change}
                </p>
              )}
            </div>
          </motion.div>
        ))}
      </motion.div>

      {/* ── FILTERS + TABLE ── */}
      <motion.div
        variants={itemVariants}
        className="rounded-2xl border border-border/50 bg-card dark:bg-card/70 dark:backdrop-blur-sm shadow-sm overflow-hidden"
      >
        {/* Toolbar */}
        <div className="p-5 border-b border-border/50 flex flex-col sm:flex-row gap-3">
          {/* Search */}
          <div className="relative flex-1 max-w-md">
            <Search size={15} className="absolute left-3 top-1/2 -translate-y-1/2 text-muted-foreground" />
            <input
              id="user-search"
              type="text"
              placeholder="Tìm theo tên hoặc email..."
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              className="w-full h-9 pl-9 pr-4 bg-muted/30 border border-border/50 rounded-xl text-sm
                focus:outline-none focus:ring-2 focus:ring-primary/25 focus:border-primary/40 transition-all"
            />
          </div>

          {/* Filters */}
          <div className="flex gap-2 items-center shrink-0">
            <Filter size={14} className="text-muted-foreground shrink-0" />
            <select
              id="role-filter"
              value={roleFilter}
              onChange={(e) => setRoleFilter(e.target.value as RoleFilter)}
              className="h-9 px-3 rounded-xl border border-border/50 bg-muted/30 text-sm focus:outline-none focus:ring-2 focus:ring-primary/25"
            >
              <option value="ALL">Tất cả Role</option>
              <option value="TEACHER">Giáo viên</option>
              <option value="STUDENT">Học sinh</option>
              <option value="ADMIN">Admin</option>
            </select>
            <select
              id="status-filter"
              value={statusFilter}
              onChange={(e) => setStatusFilter(e.target.value as StatusFilter)}
              className="h-9 px-3 rounded-xl border border-border/50 bg-muted/30 text-sm focus:outline-none focus:ring-2 focus:ring-primary/25"
            >
              <option value="ALL">Tất cả Status</option>
              <option value="ACTIVE">Hoạt động</option>
              <option value="INACTIVE">Bị khoá</option>
            </select>
          </div>

          <div className="text-sm text-muted-foreground self-center whitespace-nowrap">
            Hiển thị <span className="font-bold text-foreground">{filtered.length}</span> / {users.length}
          </div>
        </div>

        {/* Table */}
        <div className="overflow-x-auto">
          <table className="w-full text-sm text-left">
            <thead className="text-xs text-muted-foreground uppercase bg-muted/20 dark:bg-muted/10">
              <tr>
                <th className="px-5 py-3.5 font-semibold">Người dùng</th>
                <th className="px-5 py-3.5 font-semibold">Vai trò</th>
                <th className="px-5 py-3.5 font-semibold">Trạng thái</th>
                <th className="px-5 py-3.5 font-semibold hidden md:table-cell">Ngày tạo</th>
                <th className="px-5 py-3.5 font-semibold text-right">Thao tác</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-border/40">
              {isLoading ? (
                Array.from({ length: 6 }).map((_, i) => (
                  <tr key={i}>
                    <td colSpan={5} className="px-5 py-4">
                      <div className="h-9 bg-muted/40 rounded-lg animate-pulse" />
                    </td>
                  </tr>
                ))
              ) : filtered.length === 0 ? (
                <tr>
                  <td colSpan={5} className="py-16 text-center text-muted-foreground text-sm">
                    Không tìm thấy người dùng nào phù hợp với bộ lọc.
                  </td>
                </tr>
              ) : (
                filtered.map((user) => (
                  <tr
                    key={user.userId}
                    className="hover:bg-muted/10 dark:hover:bg-muted/8 transition-colors group"
                  >
                    {/* Name + email */}
                    <td className="px-5 py-4">
                      <div className="flex items-center gap-3">
                        <div className={`w-9 h-9 rounded-full flex items-center justify-center text-white text-sm font-bold shrink-0 ${
                          user.role === 'TEACHER' ? 'bg-primary'
                          : user.role === 'ADMIN' ? 'bg-amber-500'
                          : 'bg-emerald-500'
                        }`}>
                          {user.name.charAt(0).toUpperCase()}
                        </div>
                        <div className="min-w-0">
                          <p className="font-semibold text-foreground truncate group-hover:text-violet-500 transition-colors">
                            {user.name}
                          </p>
                          <p className="text-xs text-muted-foreground truncate">{user.email}</p>
                        </div>
                      </div>
                    </td>
                    <td className="px-5 py-4"><RoleBadge role={user.role} /></td>
                    <td className="px-5 py-4"><StatusBadge status={user.status} /></td>
                    <td className="px-5 py-4 text-muted-foreground text-xs hidden md:table-cell">
                      {new Date(user.createdAt).toLocaleDateString('vi-VN')}
                    </td>
                    <td className="px-5 py-4 text-right">
                      <ActionMenu
                        user={user}
                        onToggleStatus={() =>
                          patchMutation.mutate({
                            userId: user.userId,
                            status: user.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE',
                          })
                        }
                        onChangeRole={(role) =>
                          patchMutation.mutate({ userId: user.userId, role })
                        }
                      />
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </motion.div>
    </motion.div>
  )
}
