import { useMemo, useState } from 'react'
import type { FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { CalendarPlus, Search, UserPlus, Users, XCircle } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Field, FormError, Select, SubmitButton } from '@/components/teacher/FormKit'
import { CardSkeleton, EmptyState, ErrorState, Pill } from '@/components/student/StudentUi'
import { cancelSubscriptionNow, extendSubscription, getAllSubscriptions, grantSubscription } from '@/api/adminApi'
import { adminKeys, useAdminUsers } from '@/hooks/useAdminData'
import { SUBSCRIPTION_STATUS } from '@/lib/billing'
import { formatDateTime, getErrorMessage } from '@/lib/format'
import { confirmDialog, notify, promptDialog } from '@/lib/notify'
import type { AdminUser } from '@/types/admin'
import type { Subscription, SubscriptionPlan } from '@/types/billing'

/** Danh sách gói của mọi người dùng + cấp / gia hạn / huỷ thủ công (không qua cổng thanh toán). */
export function SubscribersPanel({ plans }: { plans: SubscriptionPlan[] }) {
  const queryClient = useQueryClient()
  const subs = useQuery({ queryKey: adminKeys.subscriptions, queryFn: getAllSubscriptions })
  const users = useAdminUsers()
  const [search, setSearch] = useState('')
  const [granting, setGranting] = useState(false)

  const userById = useMemo(() => new Map((users.data ?? []).map(u => [u.userId, u])), [users.data])
  const refresh = () => queryClient.invalidateQueries({ queryKey: adminKeys.subscriptions })

  const extend = useMutation({
    mutationFn: ({ id, days }: { id: number; days: number }) => extendSubscription(id, days),
    onSuccess: (_d, v) => { refresh(); notify.success(`Đã gia hạn thêm ${v.days} ngày`) },
    onError: e => notify.apiError(e, 'Không gia hạn được gói.'),
  })
  const cancel = useMutation({
    mutationFn: cancelSubscriptionNow,
    onSuccess: () => { refresh(); notify.success('Đã huỷ gói') },
    onError: e => notify.apiError(e, 'Không huỷ được gói.'),
  })

  const askExtend = async (sub: Subscription) => {
    const value = await promptDialog({
      title: `Gia hạn gói "${sub.plan.name}"`,
      description: `Cho ${label(userById.get(sub.userId), sub.userId)}.`,
      label: 'Số ngày cộng thêm',
      defaultValue: String(sub.plan.durationDays ?? 30),
      confirmText: 'Gia hạn',
      required: true,
    })
    const days = Number(value)
    if (value === null) return
    if (!Number.isInteger(days) || days < 1 || days > 3650) { notify.error('Số ngày phải là số nguyên từ 1 đến 3650'); return }
    extend.mutate({ id: sub.subscriptionId, days })
  }

  const askCancel = async (sub: Subscription) => {
    const ok = await confirmDialog({
      title: `Huỷ ngay gói "${sub.plan.name}"?`,
      description: `${label(userById.get(sub.userId), sub.userId)} sẽ mất quyền sử dụng ngay lập tức. Không tự hoàn tiền.`,
      confirmText: 'Huỷ gói',
      destructive: true,
    })
    if (ok) cancel.mutate(sub.subscriptionId)
  }

  const keyword = search.trim().toLowerCase()
  const list = (subs.data ?? [])
    .filter(s => !keyword || label(userById.get(s.userId), s.userId).toLowerCase().includes(keyword) || s.plan.name.toLowerCase().includes(keyword))
    .sort((a, b) => Number(b.status === 'ACTIVE') - Number(a.status === 'ACTIVE') || b.subscriptionId - a.subscriptionId)

  return (
    <div className="space-y-4">
      <div className="flex flex-col gap-3 sm:flex-row">
        <div className="relative flex-1">
          <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" aria-hidden="true" />
          <Input type="search" value={search} onChange={e => setSearch(e.target.value)} placeholder="Tìm theo tên, email hoặc tên gói..." aria-label="Tìm người đăng ký" className="h-10 bg-card pl-9" />
        </div>
        <Button className="h-10 rounded-xl px-4" onClick={() => setGranting(true)}>
          <UserPlus size={16} aria-hidden="true" /> Cấp gói
        </Button>
      </div>

      {subs.isError ? <ErrorState /> : subs.isLoading ? <CardSkeleton count={2} className="md:grid-cols-1 xl:grid-cols-1" /> : list.length === 0 ? (
        <EmptyState icon={Users} title="Chưa có ai đăng ký" description="Gói được tạo khi người dùng thanh toán thành công hoặc khi admin cấp thủ công." />
      ) : (
        <ul className="divide-y divide-border/60 overflow-hidden rounded-2xl border border-border/50 bg-card dark:bg-card/70">
          {list.map(sub => {
            const st = SUBSCRIPTION_STATUS[sub.status] ?? { label: sub.status, tone: 'muted' as const }
            const busy = (extend.isPending && extend.variables?.id === sub.subscriptionId) || (cancel.isPending && cancel.variables === sub.subscriptionId)
            return (
              <li key={sub.subscriptionId} className="flex flex-col gap-3 p-4 sm:flex-row sm:items-center">
                <div className="min-w-0 flex-1">
                  <div className="flex flex-wrap items-center gap-2">
                    <p className="truncate font-medium text-foreground">{label(userById.get(sub.userId), sub.userId)}</p>
                    <Pill tone={st.tone}>{st.label}</Pill>
                  </div>
                  <p className="mt-0.5 text-sm text-muted-foreground">{sub.plan.name}</p>
                  <p className="text-xs text-muted-foreground">
                    Từ {formatDateTime(sub.startedAt)} · {sub.expiresAt ? `hết hạn ${formatDateTime(sub.expiresAt)}` : 'không thời hạn'}
                    {sub.lastPaymentId == null && ' · cấp thủ công'}
                  </p>
                </div>
                {sub.status === 'ACTIVE' && (
                  <div className="flex shrink-0 gap-2">
                    <Button variant="outline" className="h-9 rounded-lg" disabled={busy} onClick={() => askExtend(sub)}>
                      <CalendarPlus size={14} aria-hidden="true" /> Gia hạn
                    </Button>
                    <Button variant="outline" className="h-9 rounded-lg text-destructive hover:bg-destructive/10" disabled={busy} onClick={() => askCancel(sub)}>
                      <XCircle size={14} aria-hidden="true" /> Huỷ
                    </Button>
                  </div>
                )}
              </li>
            )
          })}
        </ul>
      )}

      <Dialog open={granting} onOpenChange={setGranting}>
        <DialogContent className="sm:max-w-md">
          {granting && (
            <GrantForm
              users={(users.data ?? []).filter(u => (u.role === 'STUDENT' || u.role === 'TEACHER') && u.status === 'ACTIVE')}
              plans={plans.filter(p => p.active)}
              onDone={() => { setGranting(false); refresh() }}
            />
          )}
        </DialogContent>
      </Dialog>
    </div>
  )
}

function label(user: AdminUser | undefined, userId: number): string {
  return user ? `${user.name} · ${user.email}` : `Người dùng #${userId}`
}

function GrantForm({ users, plans, onDone }: { users: AdminUser[]; plans: SubscriptionPlan[]; onDone: () => void }) {
  const [userQuery, setUserQuery] = useState('')
  const [userId, setUserId] = useState('')
  const [planId, setPlanId] = useState('')
  const [days, setDays] = useState('')
  const user = users.find(u => String(u.userId) === userId)
  const plansForUser = user ? plans.filter(p => p.targetRole === user.role) : []
  const plan = plansForUser.find(p => String(p.planId) === planId)

  const q = userQuery.trim().toLowerCase()
  const matches = q ? users.filter(u => u.name.toLowerCase().includes(q) || u.email.toLowerCase().includes(q)).slice(0, 30) : users.slice(0, 30)

  const grant = useMutation({
    mutationFn: () => grantSubscription(Number(userId), Number(planId), Number(days || plan?.durationDays || 30)),
    onSuccess: s => { notify.success(`Đã cấp gói "${s.plan.name}"`); onDone() },
  })

  const submit = (e: FormEvent) => {
    e.preventDefault()
    grant.mutate()
  }

  return (
    <form onSubmit={submit} className="space-y-4">
      <DialogHeader>
        <DialogTitle>Cấp gói thủ công</DialogTitle>
        <DialogDescription>Dùng khi người dùng đã thanh toán ngoài hệ thống hoặc chưa có khoá cổng thanh toán. Không tạo giao dịch hay hoá đơn.</DialogDescription>
      </DialogHeader>
      <Field label="Tìm người dùng">
        <Input value={userQuery} onChange={e => setUserQuery(e.target.value)} placeholder="Tên hoặc email" className="h-11" />
      </Field>
      <Field label="Người dùng *">
        <Select required value={userId} onChange={e => { setUserId(e.target.value); setPlanId('') }}>
          <option value="">Chọn người dùng</option>
          {matches.map(u => <option key={u.userId} value={u.userId}>{u.name} · {u.email} ({u.role === 'TEACHER' ? 'Giáo viên' : 'Học sinh'})</option>)}
        </Select>
      </Field>
      <div className="grid gap-4 sm:grid-cols-2">
        <Field label="Gói *">
          <Select required value={planId} onChange={e => setPlanId(e.target.value)} disabled={!user}>
            <option value="">{user ? 'Chọn gói' : 'Chọn người dùng trước'}</option>
            {plansForUser.map(p => <option key={p.planId} value={p.planId}>{p.name}</option>)}
          </Select>
        </Field>
        <Field label="Số ngày" hint={plan ? `Trống = ${plan.durationDays ?? 30} ngày` : undefined}>
          <Input type="number" min={1} max={3650} value={days} onChange={e => setDays(e.target.value)} className="h-11" />
        </Field>
      </div>
      <FormError message={grant.isError ? getErrorMessage(grant.error, 'Không cấp được gói.') : null} />
      <DialogFooter><SubmitButton pending={grant.isPending}>Cấp gói</SubmitButton></DialogFooter>
    </form>
  )
}
