import { useState, useRef, useEffect } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import {
  Wallet,
  Settings,
  LogOut,
  ChevronDown,
  ShieldAlert,
  ArrowUpRight
} from 'lucide-react'
import { useAuthStore } from '@/store/authStore'
import { getMyWallet } from '@/api/walletApi'
import { formatMoney } from '@/lib/download'
import { cn } from '@/lib/utils'

export function UserAccountDropdown() {
  const { user, logout } = useAuthStore()
  const [open, setOpen] = useState(false)
  const dropdownRef = useRef<HTMLDivElement>(null)
  const navigate = useNavigate()

  // Fetch số dư ví cho Student và Teacher
  const isStudentOrTeacher = user?.role === 'STUDENT' || user?.role === 'TEACHER'
  const walletQuery = useQuery({
    queryKey: ['myWallet'],
    queryFn: getMyWallet,
    enabled: !!user && isStudentOrTeacher,
    staleTime: 30_000,
  })

  // Đóng khi click bên ngoài
  useEffect(() => {
    function handleClickOutside(event: MouseEvent) {
      if (dropdownRef.current && !dropdownRef.current.contains(event.target as Node)) {
        setOpen(false)
      }
    }
    if (open) {
      document.addEventListener('mousedown', handleClickOutside)
    }
    return () => {
      document.removeEventListener('mousedown', handleClickOutside)
    }
  }, [open])

  // Đóng khi nhấn Escape
  useEffect(() => {
    function handleKeyDown(event: KeyboardEvent) {
      if (event.key === 'Escape') {
        setOpen(false)
      }
    }
    if (open) {
      document.addEventListener('keydown', handleKeyDown)
    }
    return () => {
      document.removeEventListener('keydown', handleKeyDown)
    }
  }, [open])

  if (!user) return null

  const roleLabel =
    user.role === 'STUDENT' ? 'Học viên' : user.role === 'TEACHER' ? 'Giảng viên' : 'Quản trị viên'

  const settingsHref =
    user.role === 'STUDENT'
      ? '/student/settings'
      : user.role === 'TEACHER'
      ? '/teacher/settings'
      : '/admin/settings'

  const walletHref =
    user.role === 'STUDENT'
      ? '/student/wallet'
      : user.role === 'TEACHER'
      ? '/teacher/wallet'
      : '/admin/withdrawals'

  const avatarGradient =
    user.role === 'STUDENT'
      ? 'from-blue-600 to-indigo-600'
      : user.role === 'TEACHER'
      ? 'from-violet-600 to-purple-600'
      : 'from-amber-600 to-rose-600'

  const wallet = walletQuery.data

  return (
    <div className="relative" ref={dropdownRef}>
      {/* Trigger Button */}
      <button
        onClick={() => setOpen((prev) => !prev)}
        className={cn(
          'flex items-center gap-2.5 rounded-2xl p-1.5 transition-all duration-200 select-none outline-none focus-visible:ring-2 focus-visible:ring-primary',
          open ? 'bg-muted/80 shadow-inner' : 'hover:bg-muted/60'
        )}
        aria-expanded={open}
        aria-haspopup="true"
        aria-label="Menu tài khoản"
      >
        <div className="hidden sm:flex flex-col items-end text-right">
          <span className="text-sm font-bold text-foreground leading-tight max-w-[140px] truncate">
            {user.name}
          </span>
          <span className="text-[11px] font-medium text-muted-foreground">{roleLabel}</span>
        </div>

        <div className="relative">
          <div
            className={cn(
              'w-9 h-9 rounded-full bg-linear-to-tr flex items-center justify-center text-white font-bold text-sm shadow-md ring-2 ring-background transition-transform duration-200',
              avatarGradient,
              open && 'scale-105 ring-primary/40'
            )}
          >
            {user.role === 'ADMIN' ? (
              <ShieldAlert size={18} aria-hidden="true" />
            ) : (
              user.name?.charAt(0).toUpperCase() || 'U'
            )}
          </div>
        </div>

        <ChevronDown
          size={14}
          className={cn(
            'text-muted-foreground transition-transform duration-200 -ml-1 hidden sm:block',
            open && 'rotate-180'
          )}
          aria-hidden="true"
        />
      </button>

      {/* Popup Menu (Facebook Style) */}
      {open && (
        <div
          className="absolute right-0 top-full mt-2 w-72 sm:w-80 rounded-2xl border border-border/80 bg-card/95 backdrop-blur-xl shadow-2xl shadow-black/20 z-50 p-3 overflow-hidden animate-in fade-in-0 zoom-in-95 slide-in-from-top-2 duration-150"
          role="menu"
          aria-orientation="vertical"
        >
          {/* Header Thông tin người dùng */}
          <div className="flex items-center gap-3 p-2 rounded-xl bg-muted/40">
            <div
              className={cn(
                'w-12 h-12 rounded-full bg-linear-to-tr flex items-center justify-center text-white font-extrabold text-lg shadow-sm shrink-0',
                avatarGradient
              )}
            >
              {user.role === 'ADMIN' ? (
                <ShieldAlert size={22} aria-hidden="true" />
              ) : (
                user.name?.charAt(0).toUpperCase() || 'U'
              )}
            </div>
            <div className="flex flex-col min-w-0">
              <span className="font-bold text-foreground text-sm truncate leading-snug">
                {user.name}
              </span>
              <span className="text-xs text-muted-foreground truncate">{user.email}</span>
              <span className="inline-block mt-0.5 text-[10px] font-semibold text-primary uppercase tracking-wider">
                {roleLabel}
              </span>
            </div>
          </div>

          {/* Thẻ Số dư ví (Dành cho Student & Teacher) */}
          {isStudentOrTeacher && (
            <div className="mt-2.5 rounded-xl border border-primary/20 bg-linear-to-br from-primary/10 via-primary/5 to-background p-3 shadow-xs">
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-2 text-xs font-semibold text-muted-foreground">
                  <div className="flex h-6 w-6 items-center justify-center rounded-lg bg-primary/15 text-primary">
                    <Wallet size={13} />
                  </div>
                  <span>Số dư ví</span>
                </div>
                <Link
                  to={walletHref}
                  onClick={() => setOpen(false)}
                  className="flex items-center gap-0.5 text-[11px] font-semibold text-primary hover:underline"
                >
                  {user.role === 'STUDENT' ? 'Nạp tiền' : 'Rút tiền'}
                  <ArrowUpRight size={12} />
                </Link>
              </div>

              <div className="mt-2 flex items-baseline justify-between">
                <span className="text-xl font-extrabold tracking-tight text-foreground">
                  {wallet ? formatMoney(wallet.balance) : '0 đ'}
                </span>
                <span className="text-[10px] text-muted-foreground font-medium">Khả dụng</span>
              </div>

              {user.role === 'TEACHER' && wallet && wallet.pendingBalance > 0 && (
                <div className="mt-1.5 pt-1.5 border-t border-border/40 flex items-center justify-between text-[11px]">
                  <span className="text-muted-foreground">Tạm giữ 4 ngày:</span>
                  <span className="font-semibold text-amber-600 dark:text-amber-400">
                    {formatMoney(wallet.pendingBalance)}
                  </span>
                </div>
              )}
            </div>
          )}

          {/* Danh sách hành động */}
          <div className="mt-2 pt-1 border-t border-border/50 space-y-1">
            {/* Mục Ví cá nhân */}
            {isStudentOrTeacher && (
              <Link
                to={walletHref}
                onClick={() => setOpen(false)}
                className="flex items-center gap-3 px-3 py-2.5 rounded-xl text-sm font-medium text-foreground hover:bg-muted/70 transition-colors group"
                role="menuitem"
              >
                <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-muted text-muted-foreground group-hover:text-primary group-hover:bg-primary/10 transition-colors shrink-0">
                  <Wallet size={16} />
                </div>
                <div className="flex flex-col">
                  <span>{user.role === 'STUDENT' ? 'Ví học tập của tôi' : 'Ví & Doanh thu'}</span>
                  <span className="text-[11px] text-muted-foreground font-normal">
                    {user.role === 'STUDENT' ? 'Nạp tiền & Lịch sử mua khóa học' : 'Rút tiền & Quản lý thu nhập'}
                  </span>
                </div>
              </Link>
            )}

            {/* Mục Cài đặt tài khoản */}
            <Link
              to={settingsHref}
              onClick={() => setOpen(false)}
              className="flex items-center gap-3 px-3 py-2.5 rounded-xl text-sm font-medium text-foreground hover:bg-muted/70 transition-colors group"
              role="menuitem"
            >
              <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-muted text-muted-foreground group-hover:text-primary group-hover:bg-primary/10 transition-colors shrink-0">
                <Settings size={16} />
              </div>
              <div className="flex flex-col">
                <span>Cài đặt tài khoản</span>
                <span className="text-[11px] text-muted-foreground font-normal">
                  Thông tin cá nhân & Mật khẩu
                </span>
              </div>
            </Link>

            {/* Mục Đăng xuất */}
            <button
              onClick={() => {
                setOpen(false)
                logout()
                navigate('/')
              }}
              className="flex items-center gap-3 w-full px-3 py-2.5 rounded-xl text-sm font-medium text-destructive hover:bg-destructive/10 transition-colors group text-left"
              role="menuitem"
            >
              <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-destructive/10 text-destructive group-hover:bg-destructive/20 transition-colors shrink-0">
                <LogOut size={16} />
              </div>
              <div className="flex flex-col">
                <span className="font-semibold text-destructive">Đăng xuất</span>
                <span className="text-[11px] text-muted-foreground font-normal">
                  Thoát khỏi phiên đăng nhập
                </span>
              </div>
            </button>
          </div>
        </div>
      )}
    </div>
  )
}
