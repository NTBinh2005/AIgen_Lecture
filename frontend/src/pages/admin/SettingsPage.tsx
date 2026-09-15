import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { motion, type Variants } from 'framer-motion'
import {
  User, Lock, Bell, Shield, Monitor,
  CheckCircle2, Eye, EyeOff, Save, Sun, Moon, Laptop,
} from 'lucide-react'
import { useAuthStore } from '@/store/authStore'

// ── ZOD SCHEMAS ───────────────────────────────────────────────────────────────
const profileSchema = z.object({
  name: z.string().min(2, 'Tên phải có ít nhất 2 ký tự'),
  email: z.string().email('Email không hợp lệ'),
})

const passwordSchema = z
  .object({
    currentPassword: z.string().min(1, 'Vui lòng nhập mật khẩu hiện tại'),
    newPassword: z
      .string()
      .min(8, 'Mật khẩu mới phải có ít nhất 8 ký tự')
      .regex(/[A-Z]/, 'Phải có ít nhất 1 chữ hoa')
      .regex(/[0-9]/, 'Phải có ít nhất 1 chữ số'),
    confirmPassword: z.string(),
  })
  .refine((d) => d.newPassword === d.confirmPassword, {
    message: 'Mật khẩu xác nhận không khớp',
    path: ['confirmPassword'],
  })

type ProfileForm = z.infer<typeof profileSchema>
type PasswordForm = z.infer<typeof passwordSchema>

// ── ANIMATION VARIANTS ────────────────────────────────────────────────────────
const containerVariants: Variants = {
  hidden: { opacity: 0 },
  show: { opacity: 1, transition: { staggerChildren: 0.06 } },
}
const itemVariants: Variants = {
  hidden: { opacity: 0, y: 16 },
  show: { opacity: 1, y: 0, transition: { type: 'spring', stiffness: 300, damping: 24 } },
}

// ── TABS CONFIG ───────────────────────────────────────────────────────────────
const tabs = [
  { id: 'profile', label: 'Hồ sơ', icon: User },
  { id: 'security', label: 'Bảo mật', icon: Lock },
  { id: 'notifications', label: 'Thông báo', icon: Bell },
  { id: 'appearance', label: 'Giao diện', icon: Monitor },
  { id: 'permissions', label: 'Phân quyền', icon: Shield },
]

// ── REUSABLE INPUT ────────────────────────────────────────────────────────────
function FormField({
  label, id, error, children,
}: {
  label: string; id: string; error?: string; children: React.ReactNode
}) {
  return (
    <div className="space-y-1.5">
      <label htmlFor={id} className="block text-sm font-medium text-foreground">
        {label}
      </label>
      {children}
      {error && <p className="text-xs text-destructive">{error}</p>}
    </div>
  )
}

function TextInput({
  id, type = 'text', placeholder, register, error,
  rightElement,
}: {
  id: string; type?: string; placeholder?: string
  register: object; error?: string; rightElement?: React.ReactNode
}) {
  return (
    <div className="relative">
      <input
        id={id}
        type={type}
        placeholder={placeholder}
        {...register}
        className={`w-full h-10 px-4 rounded-xl bg-muted/30 border text-sm transition-all
          focus:outline-none focus:ring-2 focus:ring-primary/25 focus:border-primary/40
          ${error ? 'border-destructive/50 focus:ring-destructive/25' : 'border-border/50'}
          ${rightElement ? 'pr-10' : ''}`}
      />
      {rightElement && (
        <div className="absolute right-3 top-1/2 -translate-y-1/2">{rightElement}</div>
      )}
    </div>
  )
}

// ── TOAST HELPER ──────────────────────────────────────────────────────────────
function SuccessToast({ message }: { message: string }) {
  return (
    <motion.div
      initial={{ opacity: 0, y: -16 }}
      animate={{ opacity: 1, y: 0 }}
      exit={{ opacity: 0, y: -16 }}
      className="flex items-center gap-2 bg-emerald-500/10 border border-emerald-500/30
        text-emerald-600 dark:text-emerald-400 rounded-xl px-4 py-3 text-sm font-medium mb-6"
    >
      <CheckCircle2 size={16} />
      {message}
    </motion.div>
  )
}

// ── PROFILE TAB ───────────────────────────────────────────────────────────────
function ProfileTab() {
  const { user } = useAuthStore()
  const [saved, setSaved] = useState(false)

  const { register, handleSubmit, formState: { errors, isDirty } } = useForm<ProfileForm>({
    resolver: zodResolver(profileSchema),
    defaultValues: { name: user?.name ?? '', email: user?.email ?? '' },
  })

  const onSubmit = (_data: ProfileForm) => {
    // TODO: connect to PUT /api/settings/profile
    setSaved(true)
    setTimeout(() => setSaved(false), 3000)
  }

  return (
    <form onSubmit={handleSubmit(onSubmit)} className="space-y-6">
      {saved && <SuccessToast message="Cập nhật hồ sơ thành công!" />}

      {/* Avatar */}
      <div className="flex items-center gap-4">
        <div className="w-16 h-16 rounded-2xl bg-gradient-to-br from-violet-500 to-primary
          flex items-center justify-center text-white text-2xl font-bold shadow-lg shadow-violet-500/25">
          {user?.name?.charAt(0) ?? 'A'}
        </div>
        <div>
          <p className="font-semibold text-foreground">{user?.name}</p>
          <p className="text-sm text-muted-foreground">{user?.email}</p>
          <span className="inline-flex mt-1 px-2 py-0.5 bg-violet-500/10 text-violet-500
            text-xs font-semibold rounded-full border border-violet-500/20">
            {user?.role}
          </span>
        </div>
      </div>

      <div className="h-px bg-border/50" />

      <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
        <FormField label="Họ và tên" id="name" error={errors.name?.message}>
          <TextInput id="name" placeholder="Nhập tên của bạn" register={register('name')} error={errors.name?.message} />
        </FormField>
        <FormField label="Email" id="email" error={errors.email?.message}>
          <TextInput id="email" type="email" placeholder="email@edumind.vn" register={register('email')} error={errors.email?.message} />
        </FormField>
      </div>

      <FormField label="Vai trò" id="role">
        <input
          id="role"
          disabled
          value={user?.role ?? ''}
          className="w-full h-10 px-4 rounded-xl bg-muted/20 border border-border/30
            text-sm text-muted-foreground cursor-not-allowed"
        />
      </FormField>

      <div className="flex justify-end pt-2">
        <button
          type="submit"
          disabled={!isDirty}
          className="inline-flex items-center gap-2 px-5 py-2.5 bg-violet-600 hover:bg-violet-500
            disabled:opacity-40 disabled:cursor-not-allowed text-white font-semibold text-sm
            rounded-xl transition-all duration-200 shadow-lg shadow-violet-600/25 hover:shadow-violet-500/30"
        >
          <Save size={16} />
          Lưu thay đổi
        </button>
      </div>
    </form>
  )
}

// ── SECURITY TAB ──────────────────────────────────────────────────────────────
function SecurityTab() {
  const [show, setShow] = useState({ current: false, newPw: false, confirm: false })
  const [saved, setSaved] = useState(false)

  const { register, handleSubmit, reset, formState: { errors } } = useForm<PasswordForm>({
    resolver: zodResolver(passwordSchema),
  })

  const onSubmit = (_data: PasswordForm) => {
    // TODO: connect to PUT /api/settings/password
    setSaved(true)
    reset()
    setTimeout(() => setSaved(false), 3000)
  }

  const EyeBtn = ({ field }: { field: 'current' | 'newPw' | 'confirm' }) => (
    <button
      type="button"
      onClick={() => setShow((s) => ({ ...s, [field]: !s[field] }))}
      className="text-muted-foreground hover:text-foreground transition-colors"
      aria-label="Toggle visibility"
    >
      {show[field] ? <EyeOff size={16} /> : <Eye size={16} />}
    </button>
  )

  return (
    <form onSubmit={handleSubmit(onSubmit)} className="space-y-5">
      {saved && <SuccessToast message="Đổi mật khẩu thành công!" />}

      <FormField label="Mật khẩu hiện tại" id="currentPassword" error={errors.currentPassword?.message}>
        <TextInput
          id="currentPassword"
          type={show.current ? 'text' : 'password'}
          placeholder="••••••••"
          register={register('currentPassword')}
          error={errors.currentPassword?.message}
          rightElement={<EyeBtn field="current" />}
        />
      </FormField>

      <FormField label="Mật khẩu mới" id="newPassword" error={errors.newPassword?.message}>
        <TextInput
          id="newPassword"
          type={show.newPw ? 'text' : 'password'}
          placeholder="Tối thiểu 8 ký tự, 1 chữ hoa, 1 số"
          register={register('newPassword')}
          error={errors.newPassword?.message}
          rightElement={<EyeBtn field="newPw" />}
        />
      </FormField>

      <FormField label="Xác nhận mật khẩu mới" id="confirmPassword" error={errors.confirmPassword?.message}>
        <TextInput
          id="confirmPassword"
          type={show.confirm ? 'text' : 'password'}
          placeholder="Nhập lại mật khẩu mới"
          register={register('confirmPassword')}
          error={errors.confirmPassword?.message}
          rightElement={<EyeBtn field="confirm" />}
        />
      </FormField>

      {/* Password strength hints */}
      <div className="rounded-xl bg-muted/30 border border-border/40 p-4 space-y-1.5 text-xs text-muted-foreground">
        <p className="font-semibold text-foreground mb-2">Yêu cầu mật khẩu:</p>
        <p>• Ít nhất 8 ký tự</p>
        <p>• Ít nhất 1 chữ hoa (A-Z)</p>
        <p>• Ít nhất 1 chữ số (0-9)</p>
      </div>

      <div className="flex justify-end pt-1">
        <button
          type="submit"
          className="inline-flex items-center gap-2 px-5 py-2.5 bg-violet-600 hover:bg-violet-500
            text-white font-semibold text-sm rounded-xl transition-all duration-200
            shadow-lg shadow-violet-600/25 hover:shadow-violet-500/30"
        >
          <Lock size={16} />
          Cập nhật mật khẩu
        </button>
      </div>
    </form>
  )
}

// ── NOTIFICATION TAB ──────────────────────────────────────────────────────────
type NotifKey = 'newUser' | 'lectureGen' | 'systemAlert' | 'weeklyReport'

function NotificationsTab() {
  const [settings, setSettings] = useState<Record<NotifKey, boolean>>({
    newUser: true,
    lectureGen: true,
    systemAlert: true,
    weeklyReport: false,
  })
  const [saved, setSaved] = useState(false)

  const notifItems: Array<{ key: NotifKey; label: string; desc: string }> = [
    { key: 'newUser', label: 'Người dùng mới đăng ký', desc: 'Nhận thông báo khi có user mới tham gia hệ thống' },
    { key: 'lectureGen', label: 'Bài giảng AI hoàn thành', desc: 'Khi video bài giảng render xong' },
    { key: 'systemAlert', label: 'Cảnh báo hệ thống', desc: 'Lỗi server, downtime hoặc API quota vượt ngưỡng' },
    { key: 'weeklyReport', label: 'Báo cáo tuần', desc: 'Tóm tắt hoạt động hàng tuần qua email' },
  ]

  const handleSave = () => {
    // TODO: connect to PUT /api/settings/notifications
    setSaved(true)
    setTimeout(() => setSaved(false), 3000)
  }

  return (
    <div className="space-y-5">
      {saved && <SuccessToast message="Cài đặt thông báo đã được lưu!" />}
      {notifItems.map((item) => (
        <div key={item.key} className="flex items-center justify-between p-4 rounded-xl
          border border-border/40 bg-muted/10 hover:bg-muted/20 transition-colors">
          <div>
            <p className="text-sm font-semibold text-foreground">{item.label}</p>
            <p className="text-xs text-muted-foreground mt-0.5">{item.desc}</p>
          </div>
          {/* Toggle switch */}
          <button
            id={`toggle-${item.key}`}
            type="button"
            role="switch"
            aria-checked={settings[item.key]}
            onClick={() => setSettings((s) => ({ ...s, [item.key]: !s[item.key] }))}
            className={`relative inline-flex h-6 w-11 shrink-0 cursor-pointer rounded-full
              border-2 border-transparent transition-colors duration-200 focus:outline-none
              focus:ring-2 focus:ring-primary/25 ${settings[item.key] ? 'bg-violet-600' : 'bg-muted'}`}
          >
            <span
              className={`pointer-events-none inline-block h-5 w-5 transform rounded-full
                bg-white shadow-lg ring-0 transition duration-200
                ${settings[item.key] ? 'translate-x-5' : 'translate-x-0'}`}
            />
          </button>
        </div>
      ))}
      <div className="flex justify-end pt-1">
        <button
          type="button"
          onClick={handleSave}
          className="inline-flex items-center gap-2 px-5 py-2.5 bg-violet-600 hover:bg-violet-500
            text-white font-semibold text-sm rounded-xl transition-all duration-200
            shadow-lg shadow-violet-600/25"
        >
          <Save size={16} />
          Lưu cài đặt
        </button>
      </div>
    </div>
  )
}

// ── APPEARANCE TAB ────────────────────────────────────────────────────────────
type ThemeMode = 'light' | 'dark' | 'system'

function AppearanceTab() {
  const [theme, setTheme] = useState<ThemeMode>('dark')
  const [saved, setSaved] = useState(false)

  const themes: Array<{ id: ThemeMode; label: string; icon: React.ElementType; desc: string }> = [
    { id: 'light', label: 'Sáng', icon: Sun, desc: 'Giao diện sáng, phù hợp ban ngày' },
    { id: 'dark', label: 'Tối', icon: Moon, desc: 'Giao diện tối, bảo vệ mắt ban đêm' },
    { id: 'system', label: 'Hệ thống', icon: Laptop, desc: 'Tự động theo cài đặt thiết bị' },
  ]

  const handleSave = () => {
    if (theme === 'dark') document.documentElement.classList.add('dark')
    else document.documentElement.classList.remove('dark')
    setSaved(true)
    setTimeout(() => setSaved(false), 3000)
  }

  return (
    <div className="space-y-5">
      {saved && <SuccessToast message="Đã áp dụng giao diện mới!" />}
      <div>
        <p className="text-sm font-semibold text-foreground mb-3">Chế độ màu</p>
        <div className="grid grid-cols-3 gap-3">
          {themes.map((t) => (
            <button
              key={t.id}
              id={`theme-${t.id}`}
              type="button"
              onClick={() => setTheme(t.id)}
              className={`flex flex-col items-center gap-2 p-4 rounded-xl border-2 transition-all duration-200 text-center
                ${theme === t.id
                  ? 'border-violet-500 bg-violet-500/10 text-violet-500'
                  : 'border-border/40 hover:border-border text-muted-foreground hover:text-foreground'}`}
            >
              <t.icon size={20} />
              <span className="text-sm font-semibold">{t.label}</span>
              <span className="text-xs opacity-70">{t.desc}</span>
            </button>
          ))}
        </div>
      </div>
      <div className="flex justify-end pt-1">
        <button
          type="button"
          onClick={handleSave}
          className="inline-flex items-center gap-2 px-5 py-2.5 bg-violet-600 hover:bg-violet-500
            text-white font-semibold text-sm rounded-xl transition-all duration-200
            shadow-lg shadow-violet-600/25"
        >
          <Save size={16} />
          Áp dụng
        </button>
      </div>
    </div>
  )
}

// ── PERMISSIONS TAB ───────────────────────────────────────────────────────────
const rolePermissions = [
  {
    role: 'ADMIN', color: 'text-amber-500', bg: 'bg-amber-500/10 border-amber-500/20',
    permissions: ['Quản lý toàn bộ người dùng', 'Xem thống kê hệ thống', 'Cấu hình AI', 'Nhật ký hệ thống', 'Cài đặt hệ thống'],
  },
  {
    role: 'TEACHER', color: 'text-primary', bg: 'bg-primary/10 border-primary/20',
    permissions: ['Tạo &amp; quản lý bài giảng', 'Xem bài giảng của mình', 'Upload tài liệu', 'Sinh kịch bản AI'],
  },
  {
    role: 'STUDENT', color: 'text-emerald-500', bg: 'bg-emerald-500/10 border-emerald-500/20',
    permissions: ['Xem bài giảng', 'Làm quiz', 'Ôn flashcard', 'Ghi chú bài học'],
  },
]

function PermissionsTab() {
  return (
    <div className="space-y-4">
      <p className="text-sm text-muted-foreground">
        Bảng phân quyền hiện tại của hệ thống. Thay đổi cần chỉnh sửa trực tiếp backend.
      </p>
      {rolePermissions.map((rp) => (
        <div key={rp.role} className={`rounded-xl border p-4 ${rp.bg}`}>
          <p className={`text-sm font-bold mb-3 ${rp.color}`}>{rp.role}</p>
          <ul className="space-y-1.5">
            {rp.permissions.map((p) => (
              <li key={p} className="flex items-center gap-2 text-sm text-foreground">
                <CheckCircle2 size={14} className={rp.color} />
                <span dangerouslySetInnerHTML={{ __html: p }} />
              </li>
            ))}
          </ul>
        </div>
      ))}
    </div>
  )
}

// ── MAIN COMPONENT ────────────────────────────────────────────────────────────
export default function SettingsPage() {
  const [activeTab, setActiveTab] = useState('profile')

  const renderContent = () => {
    switch (activeTab) {
      case 'profile': return <ProfileTab />
      case 'security': return <SecurityTab />
      case 'notifications': return <NotificationsTab />
      case 'appearance': return <AppearanceTab />
      case 'permissions': return <PermissionsTab />
      default: return null
    }
  }

  return (
    <motion.div
      className="max-w-5xl mx-auto space-y-6"
      variants={containerVariants}
      initial="hidden"
      animate="show"
    >
      {/* ── HEADER ── */}
      <motion.div variants={itemVariants}>
        <p className="text-sm font-medium text-muted-foreground mb-1">Hệ thống</p>
        <h1 className="text-3xl md:text-4xl font-bold tracking-tight text-foreground">
          Cài đặt <span className="text-violet-500">hệ thống</span>
        </h1>
        <p className="text-muted-foreground mt-2">Quản lý tài khoản, bảo mật và tuỳ chọn hệ thống.</p>
      </motion.div>

      {/* ── LAYOUT: SIDEBAR TABS + CONTENT ── */}
      <motion.div variants={itemVariants} className="flex flex-col lg:flex-row gap-6">
        {/* Sidebar */}
        <nav className="lg:w-52 shrink-0">
          <div className="flex flex-row lg:flex-col gap-1 overflow-x-auto lg:overflow-visible
            pb-2 lg:pb-0 rounded-2xl border border-border/50 bg-card dark:bg-card/70
            dark:backdrop-blur-sm p-2">
            {tabs.map((tab) => (
              <button
                key={tab.id}
                id={`settings-tab-${tab.id}`}
                type="button"
                onClick={() => setActiveTab(tab.id)}
                className={`flex items-center gap-2.5 px-3.5 py-2.5 rounded-xl text-sm font-medium
                  transition-all duration-200 whitespace-nowrap relative group
                  ${activeTab === tab.id
                    ? 'bg-violet-600/10 text-violet-600 font-semibold'
                    : 'text-muted-foreground hover:bg-muted/50 hover:text-foreground'}`}
              >
                {activeTab === tab.id && (
                  <motion.div
                    layoutId="settings-active-pill"
                    className="absolute inset-0 rounded-xl bg-violet-600/10"
                    transition={{ type: 'spring', stiffness: 350, damping: 28 }}
                  />
                )}
                <tab.icon size={16} className="relative z-10" aria-hidden="true" />
                <span className="relative z-10">{tab.label}</span>
              </button>
            ))}
          </div>
        </nav>

        {/* Content Panel */}
        <motion.div
          key={activeTab}
          initial={{ opacity: 0, x: 12 }}
          animate={{ opacity: 1, x: 0 }}
          transition={{ type: 'spring', stiffness: 300, damping: 26 }}
          className="flex-1 rounded-2xl border border-border/50 bg-card dark:bg-card/70
            dark:backdrop-blur-sm p-6 shadow-sm"
        >
          {renderContent()}
        </motion.div>
      </motion.div>
    </motion.div>
  )
}
