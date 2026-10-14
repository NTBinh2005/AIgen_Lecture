import { useMemo, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { BookOpen, Loader2, Lock, Pencil, Plus, Search, Unlock, Users } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { UserFormDialog } from '@/components/admin/UserFormDialog'
import { RolePill, UserStatusPill } from '@/components/admin/AdminPills'
import { FormError, Select } from '@/components/teacher/FormKit'
import { CardSkeleton, EmptyState, ErrorState, PageHeader } from '@/components/student/StudentUi'
import { deactivateUser, getStudentClasses, updateUser } from '@/api/adminApi'
import { adminKeys, useAdminUsers } from '@/hooks/useAdminData'
import { useAuthStore } from '@/store/authStore'
import type { Role } from '@/store/authStore'
import { formatDate, getErrorMessage } from '@/lib/format'
import type { AdminUser, UserStatus } from '@/types/admin'

export default function AdminUsersPage() {
  const { data: users = [], isLoading, isError } = useAdminUsers()
  const me = useAuthStore(s => s.user)
  const queryClient = useQueryClient()
  const [search, setSearch] = useState('')
  const [role, setRole] = useState<Role | 'all'>('all')
  const [status, setStatus] = useState<UserStatus | 'all'>('all')
  const [classesOf, setClassesOf] = useState<AdminUser | null>(null)

  const toggleLock = useMutation({
    mutationFn: (u: AdminUser) => (u.status === 'ACTIVE' ? deactivateUser(u.userId) : updateUser(u.userId, { status: 'ACTIVE' }).then(() => undefined)),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: adminKeys.users }),
  })

  const filtered = useMemo(() => {
    const k = search.trim().toLowerCase()
    return users
      .filter(u => (role === 'all' || u.role === role) && (status === 'all' || u.status === status))
      .filter(u => !k || u.name.toLowerCase().includes(k) || u.email.toLowerCase().includes(k) || String(u.userId) === k)
      .sort((a, b) => b.userId - a.userId)
  }, [users, search, role, status])

  const count = (r: Role) => users.filter(u => u.role === r).length
  const isSelf = (u: AdminUser) => String(u.userId) === me?.id

  const lock = (u: AdminUser) => {
    const msg = u.status === 'ACTIVE'
      ? `Khoá tài khoản ${u.name}? Người dùng sẽ không đăng nhập được.`
      : `Mở khoá tài khoản ${u.name}?`
    if (window.confirm(msg)) toggleLock.mutate(u)
  }

  return (
    <div className="mx-auto max-w-6xl space-y-6">
      <PageHeader
        title="Người dùng"
        description="Quản lý tài khoản, vai trò và trạng thái của mọi người dùng."
        action={<UserFormDialog trigger={<Button className="h-10 rounded-xl px-4"><Plus size={16} aria-hidden="true" /> Tạo người dùng</Button>} />}
      />

      {/* Thống kê nhanh */}
      <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
        {[
          { label: 'Tất cả', value: users.length },
          { label: 'Học sinh', value: count('STUDENT') },
          { label: 'Giáo viên', value: count('TEACHER') },
          { label: 'Đã khoá', value: users.filter(u => u.status === 'INACTIVE').length },
        ].map(s => (
          <div key={s.label} className="rounded-2xl border border-border/50 bg-card p-4 dark:bg-card/70">
            <p className="text-sm text-muted-foreground">{s.label}</p>
            <p className="mt-1 text-2xl font-bold tabular-nums text-foreground">{isLoading ? '—' : s.value}</p>
          </div>
        ))}
      </div>

      {/* Bộ lọc */}
      <div className="flex flex-col gap-3 sm:flex-row">
        <div className="relative flex-1">
          <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" aria-hidden="true" />
          <Input type="search" value={search} onChange={e => setSearch(e.target.value)} placeholder="Tìm theo tên, email hoặc ID..." aria-label="Tìm người dùng" className="h-10 bg-card pl-9" />
        </div>
        <Select aria-label="Lọc vai trò" value={role} onChange={e => setRole(e.target.value as Role | 'all')} className="h-10 sm:w-44">
          <option value="all">Mọi vai trò</option>
          <option value="STUDENT">Học sinh</option>
          <option value="TEACHER">Giáo viên</option>
          <option value="ADMIN">Quản trị viên</option>
        </Select>
        <Select aria-label="Lọc trạng thái" value={status} onChange={e => setStatus(e.target.value as UserStatus | 'all')} className="h-10 sm:w-40">
          <option value="all">Mọi trạng thái</option>
          <option value="ACTIVE">Hoạt động</option>
          <option value="INACTIVE">Đã khoá</option>
        </Select>
      </div>

      {isError && <ErrorState />}
      {toggleLock.isError && <FormError message={getErrorMessage(toggleLock.error)} />}

      {isLoading ? (
        <CardSkeleton count={3} className="md:grid-cols-1 xl:grid-cols-1" />
      ) : filtered.length === 0 ? (
        <EmptyState icon={Users} title="Không có người dùng phù hợp" />
      ) : (
        <div className="overflow-hidden rounded-2xl border border-border/50 bg-card dark:bg-card/70">
          <table className="w-full text-sm">
            <thead className="hidden border-b border-border/60 bg-muted/30 text-left text-muted-foreground md:table-header-group">
              <tr>
                <th className="px-4 py-3 font-medium">Người dùng</th>
                <th className="px-4 py-3 font-medium">Vai trò</th>
                <th className="px-4 py-3 font-medium">Trạng thái</th>
                <th className="px-4 py-3 font-medium">Ngày tạo</th>
                <th className="px-4 py-3 text-right font-medium">Thao tác</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-border/50">
              {filtered.map(u => (
                <tr key={u.userId} className="flex flex-col gap-2 p-4 md:table-row md:p-0">
                  <td className="md:px-4 md:py-3">
                    <div className="flex items-center gap-3">
                      <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-linear-to-tr from-primary to-violet-500 text-sm font-bold text-primary-foreground">
                        {u.name.charAt(0).toUpperCase()}
                      </div>
                      <div className="min-w-0">
                        <p className="truncate font-medium text-foreground">{u.name}{isSelf(u) && <span className="ml-1 text-xs text-muted-foreground">(bạn)</span>}</p>
                        <p className="truncate text-xs text-muted-foreground">#{u.userId} · {u.email}</p>
                      </div>
                    </div>
                  </td>
                  <td className="md:px-4 md:py-3"><RolePill role={u.role} /></td>
                  <td className="md:px-4 md:py-3"><UserStatusPill status={u.status} /></td>
                  <td className="text-muted-foreground md:px-4 md:py-3">{formatDate(u.createdAt)}</td>
                  <td className="md:px-4 md:py-3">
                    <div className="flex gap-1 md:justify-end">
                      {u.role === 'STUDENT' && (
                        <IconAction label={`Lớp của ${u.name}`} onClick={() => setClassesOf(u)}><BookOpen size={15} /></IconAction>
                      )}
                      <UserFormDialog user={u} trigger={<IconAction label={`Sửa ${u.name}`}><Pencil size={15} /></IconAction>} />
                      {!isSelf(u) && (
                        <IconAction
                          label={u.status === 'ACTIVE' ? `Khoá ${u.name}` : `Mở khoá ${u.name}`}
                          danger={u.status === 'ACTIVE'}
                          disabled={toggleLock.isPending && toggleLock.variables?.userId === u.userId}
                          onClick={() => lock(u)}
                        >
                          {u.status === 'ACTIVE' ? <Lock size={15} /> : <Unlock size={15} />}
                        </IconAction>
                      )}
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      <StudentClassesDialog user={classesOf} onClose={() => setClassesOf(null)} />
    </div>
  )
}

interface IconActionProps extends React.ButtonHTMLAttributes<HTMLButtonElement> {
  label: string
  danger?: boolean
}

function IconAction({ label, danger, children, ...props }: IconActionProps) {
  return (
    <button
      {...props}
      aria-label={label}
      title={label}
      className={`flex h-9 w-9 items-center justify-center rounded-lg text-muted-foreground transition-colors disabled:opacity-40 ${
        danger ? 'hover:bg-destructive/10 hover:text-destructive' : 'hover:bg-muted hover:text-foreground'
      }`}
    >
      {children}
    </button>
  )
}

function StudentClassesDialog({ user, onClose }: { user: AdminUser | null; onClose: () => void }) {
  const classes = useQuery({
    queryKey: ['admin', 'student-classes', user?.userId],
    queryFn: () => getStudentClasses(user!.userId),
    enabled: user != null,
  })
  return (
    <Dialog open={user != null} onOpenChange={open => { if (!open) onClose() }}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>Lớp của {user?.name}</DialogTitle>
          <DialogDescription>{user?.email}</DialogDescription>
        </DialogHeader>
        {classes.isLoading ? (
          <div className="flex justify-center py-6"><Loader2 className="h-5 w-5 animate-spin text-muted-foreground" /></div>
        ) : classes.isError ? (
          <FormError message={getErrorMessage(classes.error)} />
        ) : (classes.data ?? []).length === 0 ? (
          <p className="py-6 text-center text-sm text-muted-foreground">Chưa ghi danh lớp nào.</p>
        ) : (
          <ul className="divide-y divide-border/50 rounded-xl border border-border/50">
            {classes.data!.map(c => (
              <li key={c.classId} className="flex items-center justify-between gap-3 p-3 text-sm">
                <span className="font-medium text-foreground">{c.className}</span>
                <span className="text-xs text-muted-foreground">{c.status} · {formatDate(c.enrolledAt)}</span>
              </li>
            ))}
          </ul>
        )}
      </DialogContent>
    </Dialog>
  )
}
