import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { Check, Loader2 } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { getPlans } from '@/api/billingApi'
import { formatMoney } from '@/lib/download'
import { planPeriod } from '@/lib/billing'
import { cn } from '@/lib/utils'
import { useAuthStore } from '@/store/authStore'
import { HOME_BY_ROLE } from '@/lib/roles'

type Audience = 'STUDENT' | 'TEACHER'

/** Bảng giá đọc trực tiếp từ GET /plans (công khai), chia theo đối tượng. */
export function PricingSection({ onLogin }: { onLogin: () => void }) {
  const user = useAuthStore(s => (s.isAuthenticated ? s.user : null))
  const [audience, setAudience] = useState<Audience>(user?.role === 'TEACHER' ? 'TEACHER' : 'STUDENT')
  const navigate = useNavigate()
  const plans = useQuery({ queryKey: ['landing', 'plans', audience], queryFn: () => getPlans(audience), staleTime: 5 * 60_000 })
  const list = [...(plans.data ?? [])].sort((a, b) => a.price - b.price)
  // Gói đắt nhất tính theo ngày thường là gói dài hạn → đánh dấu "Tiết kiệm nhất"
  const bestValue = list.length > 1
    ? list.reduce((best, p) => (p.price / (p.durationDays ?? 30) < best.price / (best.durationDays ?? 30) ? p : best), list[0])
    : null

  const choose = () => {
    if (!user) { onLogin(); return }
    if (user.role === 'ADMIN') { navigate('/admin/plans'); return }
    navigate(`${HOME_BY_ROLE[user.role]}/payments`)
  }

  return (
    <div>
      <div role="tablist" aria-label="Đối tượng" className="mx-auto flex w-fit rounded-xl bg-muted p-1">
        {([['STUDENT', 'Học sinh'], ['TEACHER', 'Giáo viên']] as const).map(([id, label]) => (
          <button
            key={id}
            role="tab"
            aria-selected={audience === id}
            onClick={() => setAudience(id)}
            className={cn('h-9 rounded-lg px-5 text-sm font-medium transition-colors', audience === id ? 'bg-card text-foreground shadow-sm' : 'text-muted-foreground hover:text-foreground')}
          >
            {label}
          </button>
        ))}
      </div>

      {plans.isLoading ? (
        <div className="flex justify-center py-16"><Loader2 className="h-6 w-6 animate-spin text-muted-foreground" /></div>
      ) : plans.isError || list.length === 0 ? (
        <p className="py-16 text-center text-sm text-muted-foreground">Chưa có gói nào đang mở bán. Vui lòng quay lại sau.</p>
      ) : (
        <ul className={cn('mx-auto mt-10 grid gap-6', list.length === 1 ? 'max-w-sm' : list.length === 2 ? 'max-w-3xl md:grid-cols-2' : 'md:grid-cols-3')}>
          {list.map(plan => {
            const highlight = bestValue?.planId === plan.planId
            return (
              <li
                key={plan.planId}
                className={cn(
                  'relative flex flex-col rounded-3xl border bg-card/80 p-6 backdrop-blur dark:bg-card/60',
                  highlight ? 'border-primary shadow-xl shadow-primary/15' : 'border-border/60',
                )}
              >
                {highlight && (
                  <span className="absolute -top-3 left-6 rounded-full bg-primary px-3 py-1 text-xs font-semibold text-primary-foreground">Tiết kiệm nhất</span>
                )}
                <h3 className="text-lg font-semibold text-foreground">{plan.name}</h3>
                {plan.description && <p className="mt-1 text-sm text-muted-foreground">{plan.description}</p>}
                <p className="mt-5">
                  <span className="text-3xl font-bold tabular-nums tracking-tight text-foreground">{formatMoney(plan.price)}</span>
                  <span className="ml-1 text-sm text-muted-foreground">{planPeriod(plan.billingCycle, plan.durationDays)}</span>
                </p>
                <ul className="mt-6 flex-1 space-y-2.5 text-sm text-foreground">
                  {plan.features.map(f => (
                    <li key={f} className="flex items-start gap-2.5">
                      <Check size={16} className="mt-0.5 shrink-0 text-emerald-600 dark:text-emerald-400" aria-hidden="true" />{f}
                    </li>
                  ))}
                </ul>
                <Button className="mt-8 h-11 w-full rounded-xl font-semibold" variant={highlight ? 'default' : 'outline'} onClick={choose}>
                  {user ? 'Chọn gói này' : 'Đăng nhập để mua'}
                </Button>
              </li>
            )
          })}
        </ul>
      )}
      <p className="mt-6 text-center text-xs text-muted-foreground">Thanh toán qua VNPay hoặc MoMo. Gói không tự gia hạn.</p>
    </div>
  )
}
