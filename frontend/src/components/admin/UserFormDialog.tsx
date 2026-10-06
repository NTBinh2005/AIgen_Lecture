import { useState } from 'react'
import type { FormEvent, ReactNode } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { AlertTriangle } from 'lucide-react'
import { Input } from '@/components/ui/input'
import {
  Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle, DialogTrigger,
} from '@/components/ui/dialog'
import { Field, FormError, Select, SubmitButton } from '@/components/teacher/FormKit'
import { createUser, updateUser } from '@/api/adminApi'
import { adminKeys } from '@/hooks/useAdminData'
import { useAuthStore } from '@/store/authStore'
import type { Role } from '@/store/authStore'
import { getErrorMessage } from '@/lib/format'
import type { AdminUser, UserStatus, UserUpdatePayload } from '@/types/admin'

interface UserFormDialogProps {
  /** Có giá trị → sửa; không có → tạo mới */
  user?: AdminUser
  trigger: ReactNode
}

export function UserFormDialog({ user, trigger }: UserFormDialogProps) {
  const [open, setOpen] = useState(false)
  const me = useAuthStore(s => s.user)
  const isSelf = !!user && String(user.userId) === me?.id
  const queryClient = useQueryClient()

  const initial = () => ({
    name: user?.name ?? '',
    email: user?.email ?? '',
    role: (user?.role ?? 'STUDENT') as Role,
    status: (user?.status ?? 'ACTIVE') as UserStatus,
    password: '',
  })
  const [form, setForm] = useState(initial)
  const [localError, setLocalError] = useState<string | null>(null)
  const set = (key: keyof ReturnType<typeof initial>) => (e: { target: { value: string } }) =>
    setForm(p => ({ ...p, [key]: e.target.value }))

  const mutation = useMutation({
    mutationFn: async () => {
      if (!user) {
        return createUser({ role: form.role, name: form.name.trim(), email: form.email.trim(), passwordHash: form.password })
      }
      // Chỉ gửi trường thay đổi
      const payload: UserUpdatePayload = {}
      if (form.name.trim() !== user.name) payload.name = form.name.trim()
      if (form.email.trim() !== user.email) payload.email = form.email.trim()
      if (form.role !== user.role) payload.role = form.role
      if (form.status !== user.status) payload.status = form.status
      if (form.password) payload.passwordHash = form.password
      return updateUser(user.userId, payload)
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: adminKeys.all })
      setOpen(false)
    },
  })

  const handleSubmit = (e: FormEvent) => {
    e.preventDefault()
    if (!user && form.password.length < 8) { setLocalError('Mật khẩu cần ít nhất 8 ký tự.'); return }
    if (user && form.password && form.password.length < 8) { setLocalError('Mật khẩu mới cần ít nhất 8 ký tự.'); return }
    if (isSelf && (form.role !== 'ADMIN' || form.status !== 'ACTIVE')) {
      setLocalError('Bạn không thể tự hạ quyền hoặc tự khoá tài khoản admin đang đăng nhập.')
      return
    }
    setLocalError(null)
    mutation.mutate()
  }

  return (
    <Dialog open={open} onOpenChange={next => { setOpen(next); if (next) { setForm(initial()); setLocalError(null); mutation.reset() } }}>
      <DialogTrigger asChild>{trigger}</DialogTrigger>
      <DialogContent className="max-h-[90vh] overflow-y-auto sm:max-w-md">
        <form onSubmit={handleSubmit} className="space-y-4">
          <DialogHeader>
            <DialogTitle>{user ? 'Sửa người dùng' : 'Tạo người dùng'}</DialogTitle>
            <DialogDescription>{user ? `ID ${user.userId} · ${user.email}` : 'Tài khoản mới sẽ ở trạng thái hoạt động.'}</DialogDescription>
          </DialogHeader>

          <Field label="Họ và tên *">
            <Input required maxLength={100} value={form.name} onChange={set('name')} className="h-11" />
          </Field>
          <Field label="Email *">
            <Input required type="email" maxLength={100} value={form.email} onChange={set('email')} className="h-11" />
          </Field>
          <div className="grid gap-4 sm:grid-cols-2">
            <Field label="Vai trò">
              <Select value={form.role} onChange={set('role')} disabled={isSelf}>
                <option value="STUDENT">Học sinh</option>
                <option value="TEACHER">Giáo viên</option>
                <option value="ADMIN">Quản trị viên</option>
              </Select>
            </Field>
            {user && (
              <Field label="Trạng thái">
                <Select value={form.status} onChange={set('status')} disabled={isSelf}>
                  <option value="ACTIVE">Hoạt động</option>
                  <option value="INACTIVE">Đã khoá</option>
                </Select>
              </Field>
            )}
          </div>
          <Field label={user ? 'Mật khẩu mới' : 'Mật khẩu *'} hint={user ? 'Để trống nếu không đổi' : 'Ít nhất 8 ký tự'}>
            <Input type="password" autoComplete="new-password" value={form.password} onChange={set('password')} className="h-11" required={!user} />
          </Field>

          {/* Backend chưa băm mật khẩu khi admin tạo/đặt lại (FIX.md #29) */}
          {(!user || form.password) && (
            <p className="flex gap-2 rounded-xl bg-amber-500/10 p-3 text-xs text-amber-800 dark:text-amber-300">
              <AlertTriangle size={14} className="mt-0.5 shrink-0" aria-hidden="true" />
              Lưu ý: backend hiện chưa mã hoá mật khẩu khi admin tạo/đặt lại, nên tài khoản sẽ chưa đăng nhập được bằng mật khẩu này
              cho tới khi nhóm backend sửa (FIX.md #29). Người dùng vẫn có thể tự đăng ký.
            </p>
          )}
          {isSelf && <p className="text-xs text-muted-foreground">Đây là tài khoản của bạn — không đổi được vai trò và trạng thái.</p>}

          <FormError message={localError ?? (mutation.isError ? getErrorMessage(mutation.error) : null)} />
          <DialogFooter>
            <SubmitButton pending={mutation.isPending}>{user ? 'Lưu thay đổi' : 'Tạo người dùng'}</SubmitButton>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
