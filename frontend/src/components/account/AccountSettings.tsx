import { useEffect } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { CheckCircle2, KeyRound, Loader2, UserRound } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { changePassword, getProfile, updateProfile } from '@/api/accountApi'
import { useAuthStore } from '@/store/authStore'
import { ErrorState, PageHeader, Panel } from '@/components/student/StudentUi'
import { formatDate, getErrorMessage } from '@/lib/format'

const profileSchema = z.object({
  name: z.string().trim().min(2, 'Tên phải từ 2-100 ký tự').max(100, 'Tên phải từ 2-100 ký tự'),
  email: z.string().trim().email('Email không hợp lệ'),
})

const passwordSchema = z.object({
  currentPassword: z.string().min(1, 'Nhập mật khẩu hiện tại'),
  newPassword: z.string().min(8, 'Mật khẩu mới phải có ít nhất 8 ký tự'),
  confirmPassword: z.string(),
}).refine(d => d.newPassword === d.confirmPassword, { message: 'Mật khẩu nhập lại không khớp', path: ['confirmPassword'] })

type ProfileForm = z.infer<typeof profileSchema>
type PasswordForm = z.infer<typeof passwordSchema>

function Field({ label, error, children }: { label: string; error?: string; children: React.ReactNode }) {
  return (
    <label className="block space-y-1.5">
      <span className="text-sm font-medium text-foreground">{label}</span>
      {children}
      {error && <span className="block text-xs text-destructive">{error}</span>}
    </label>
  )
}

function SectionTitle({ icon: Icon, title, description }: { icon: typeof UserRound; title: string; description: string }) {
  return (
    <div className="mb-5 flex items-start gap-3">
      <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-primary/10 text-primary">
        <Icon size={20} aria-hidden="true" />
      </div>
      <div>
        <h2 className="font-semibold text-foreground">{title}</h2>
        <p className="text-sm text-muted-foreground">{description}</p>
      </div>
    </div>
  )
}

const ROLE_LABEL: Record<string, string> = { STUDENT: 'Học viên', TEACHER: 'Giảng viên', ADMIN: 'Quản trị viên' }

/** Trang cài đặt tài khoản dùng chung cho mọi role (hồ sơ + đổi mật khẩu). */
export function AccountSettings() {
  const queryClient = useQueryClient()
  const { user, token, setAuth } = useAuthStore()
  const profile = useQuery({ queryKey: ['profile'], queryFn: getProfile })

  // ── Hồ sơ ──
  const profileForm = useForm<ProfileForm>({ resolver: zodResolver(profileSchema) })
  useEffect(() => {
    if (profile.data) profileForm.reset({ name: profile.data.name, email: profile.data.email })
  }, [profile.data, profileForm])

  const saveProfile = useMutation({
    mutationFn: updateProfile,
    onSuccess: data => {
      queryClient.setQueryData(['profile'], data)
      // Cập nhật tên hiển thị trên header
      if (user && token) setAuth({ ...user, name: data.name, email: data.email }, token)
    },
  })

  // ── Mật khẩu ──
  const passwordForm = useForm<PasswordForm>({ resolver: zodResolver(passwordSchema) })
  const savePassword = useMutation({
    mutationFn: (d: PasswordForm) => changePassword({ currentPassword: d.currentPassword, newPassword: d.newPassword }),
    onSuccess: () => passwordForm.reset({ currentPassword: '', newPassword: '', confirmPassword: '' }),
  })

  return (
    <div className="mx-auto max-w-3xl space-y-6">
      <PageHeader title="Cài đặt" description="Quản lý thông tin tài khoản của bạn." />

      {profile.isError && <ErrorState />}

      {/* Thẻ tài khoản */}
      <Panel className="flex items-center gap-4">
        <div className="flex h-14 w-14 shrink-0 items-center justify-center rounded-full bg-linear-to-tr from-blue-600 to-indigo-600 text-xl font-bold text-white">
          {(profile.data?.name ?? user?.name ?? 'H').charAt(0).toUpperCase()}
        </div>
        <div className="min-w-0">
          <p className="truncate font-semibold text-foreground">{profile.data?.name ?? user?.name}</p>
          <p className="truncate text-sm text-muted-foreground">{profile.data?.email ?? user?.email}</p>
          {profile.data && <p className="text-xs text-muted-foreground">{ROLE_LABEL[profile.data.role] ?? profile.data.role} · Tham gia {formatDate(profile.data.createdAt)}</p>}
        </div>
      </Panel>

      {/* Hồ sơ */}
      <Panel>
        <SectionTitle icon={UserRound} title="Thông tin cá nhân" description="Tên hiển thị với giáo viên và bạn cùng lớp." />
        <form onSubmit={profileForm.handleSubmit(d => saveProfile.mutate(d))} className="space-y-4">
          <div className="grid gap-4 sm:grid-cols-2">
            <Field label="Họ và tên" error={profileForm.formState.errors.name?.message}>
              <Input className="h-11" {...profileForm.register('name')} disabled={profile.isLoading} />
            </Field>
            <Field label="Email" error={profileForm.formState.errors.email?.message}>
              <Input type="email" className="h-11" {...profileForm.register('email')} disabled={profile.isLoading} />
            </Field>
          </div>
          <FormFooter
            pending={saveProfile.isPending}
            success={saveProfile.isSuccess && !profileForm.formState.isDirty}
            successText="Đã lưu thông tin"
            error={saveProfile.isError ? getErrorMessage(saveProfile.error) : undefined}
            label="Lưu thay đổi"
          />
        </form>
      </Panel>

      {/* Mật khẩu */}
      <Panel>
        <SectionTitle icon={KeyRound} title="Đổi mật khẩu" description="Mật khẩu mới cần ít nhất 8 ký tự." />
        <form onSubmit={passwordForm.handleSubmit(d => savePassword.mutate(d))} className="space-y-4">
          <Field label="Mật khẩu hiện tại" error={passwordForm.formState.errors.currentPassword?.message}>
            <Input type="password" autoComplete="current-password" className="h-11" {...passwordForm.register('currentPassword')} />
          </Field>
          <div className="grid gap-4 sm:grid-cols-2">
            <Field label="Mật khẩu mới" error={passwordForm.formState.errors.newPassword?.message}>
              <Input type="password" autoComplete="new-password" className="h-11" {...passwordForm.register('newPassword')} />
            </Field>
            <Field label="Nhập lại mật khẩu mới" error={passwordForm.formState.errors.confirmPassword?.message}>
              <Input type="password" autoComplete="new-password" className="h-11" {...passwordForm.register('confirmPassword')} />
            </Field>
          </div>
          <FormFooter
            pending={savePassword.isPending}
            success={savePassword.isSuccess}
            successText="Đã đổi mật khẩu"
            error={savePassword.isError ? getErrorMessage(savePassword.error) : undefined}
            label="Đổi mật khẩu"
          />
        </form>
      </Panel>
    </div>
  )
}

interface FormFooterProps {
  pending: boolean
  success: boolean
  successText: string
  error?: string
  label: string
}

function FormFooter({ pending, success, successText, error, label }: FormFooterProps) {
  return (
    <div className="flex flex-col-reverse items-stretch gap-3 pt-1 sm:flex-row sm:items-center sm:justify-end">
      {error && <p className="text-sm text-destructive sm:mr-auto" role="alert">{error}</p>}
      {success && !error && (
        <p className="flex items-center gap-1.5 text-sm text-emerald-600 dark:text-emerald-400 sm:mr-auto" role="status">
          <CheckCircle2 size={16} aria-hidden="true" /> {successText}
        </p>
      )}
      <Button type="submit" className="h-11 rounded-xl px-5" disabled={pending}>
        {pending && <Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" />}
        {label}
      </Button>
    </div>
  )
}
