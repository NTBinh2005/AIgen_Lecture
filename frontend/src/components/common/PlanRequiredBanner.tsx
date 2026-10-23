import { Link, useLocation } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { Crown } from 'lucide-react'
import { getMySubscriptions } from '@/api/billingApi'
import { useAuthStore } from '@/store/authStore'

/**
 * Backend chặn học sinh / giáo viên chưa có gói còn hạn (APP_SUBSCRIPTIONS_ENFORCE_PREMIUM)
 * nhưng chỉ trả 403 chung chung, nên FE tự kiểm tra gói để nhắc người dùng mua / gia hạn.
 */
export function PlanRequiredBanner() {
  const role = useAuthStore(s => s.user?.role)
  const { pathname } = useLocation()
  const subs = useQuery({
    queryKey: ['billing', 'subscriptions'],
    queryFn: getMySubscriptions,
    enabled: role === 'STUDENT' || role === 'TEACHER',
    staleTime: 60_000,
    // Có gói ACTIVE chưa hết hạn hay không (tính ngoài lúc render)
    select: list => list.some(s => s.status === 'ACTIVE' && (!s.expiresAt || new Date(s.expiresAt).getTime() > Date.now())),
  })

  if (role !== 'STUDENT' && role !== 'TEACHER') return null
  if (subs.data !== false) return null

  const href = role === 'TEACHER' ? '/teacher/payments' : '/student/payments'
  const onPaymentsPage = pathname.startsWith(href)
  const perks = role === 'TEACHER'
    ? 'tạo bài giảng / quiz bằng AI và render video'
    : 'xem bài giảng và làm bài kiểm tra'

  return (
    <div role="status" className="mb-6 flex flex-col gap-3 rounded-2xl border border-amber-500/30 bg-amber-500/10 p-4 sm:flex-row sm:items-center">
      <Crown className="h-5 w-5 shrink-0 text-amber-600 dark:text-amber-400" aria-hidden="true" />
      <p className="flex-1 text-sm text-foreground">
        <b>Bạn chưa có gói đăng ký còn hạn.</b> Cần gói để {perks}.
      </p>
      {!onPaymentsPage && (
        <Link to={href} className="flex h-9 shrink-0 items-center justify-center rounded-lg bg-primary px-4 text-sm font-medium text-primary-foreground hover:bg-primary/85">
          Xem các gói
        </Link>
      )}
    </div>
  )
}
