import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { BadgeCheck, Check, CheckCircle2, CreditCard, Crown, Download, ExternalLink, FileText, Loader2, Plus, RotateCcw, Undo2, XCircle } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import {
  Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle,
} from '@/components/ui/dialog'
import { Field, FormError, Select, SubmitButton, TextArea } from '@/components/teacher/FormKit'
import { CardSkeleton, EmptyState, ErrorState, PageHeader, Pill } from '@/components/student/StudentUi'
import {
  cancelPayment, cancelSubscription, createPayment, getMyAccess, getMyInvoices, getMyPayments,
  getMyRefunds, getMySubscriptions, getPayment, getPlans, requestRefund, resumeSubscription,
} from '@/api/billingApi'
import { downloadFromApi, formatMoney } from '@/lib/download'
import { formatDateTime, getErrorMessage } from '@/lib/format'
import { cn } from '@/lib/utils'
import { BILLING_CYCLE_LABEL, PAYMENT_STATUS, REFUND_STATUS, SUBSCRIPTION_STATUS, planPeriod } from '@/lib/billing'
import { useAuthStore } from '@/store/authStore'
import type { Payment, PaymentMethod, Subscription, SubscriptionPlan } from '@/types/billing'
import { confirmDialog, notify } from '@/lib/notify'
import { usePagination } from '@/hooks/usePagination'
import { Pagination } from '@/components/common/Pagination'

const KEY = ['billing'] as const

const TABS = [
  { id: 'subscriptions', label: 'Gói đăng ký', icon: Crown },
  { id: 'payments', label: 'Giao dịch', icon: CreditCard },
  { id: 'invoices', label: 'Hoá đơn', icon: FileText },
  { id: 'refunds', label: 'Hoàn tiền', icon: Undo2 },
  { id: 'access', label: 'Quyền sử dụng', icon: BadgeCheck },
] as const
type TabId = (typeof TABS)[number]['id']

/** Gói đăng ký và thanh toán — dùng chung cho học sinh (/student/payments) và giáo viên (/teacher/payments). */
export default function PaymentsPage() {
  const role = useAuthStore(s => s.user?.role) === 'TEACHER' ? 'TEACHER' : 'STUDENT'
  const [tab, setTab] = useState<TabId>('subscriptions')
  const [buying, setBuying] = useState<{ open: boolean; planId: number | null }>({ open: false, planId: null })
  const queryClient = useQueryClient()
  const refresh = () => queryClient.invalidateQueries({ queryKey: KEY })

  const payments = useQuery({ queryKey: [...KEY, 'payments'], queryFn: getMyPayments })
  const invoices = useQuery({ queryKey: [...KEY, 'invoices'], queryFn: getMyInvoices })
  const refunds = useQuery({
    queryKey: [...KEY, 'refunds'],
    queryFn: getMyRefunds,
    refetchInterval: query => query.state.data?.some(r => r.status === 'APPROVED') ? 5_000 : false,
  })
  const access = useQuery({ queryKey: [...KEY, 'access'], queryFn: getMyAccess })
  const subscriptions = useQuery({ queryKey: [...KEY, 'subscriptions'], queryFn: getMySubscriptions })
  const plans = useQuery({ queryKey: [...KEY, 'plans', role], queryFn: () => getPlans(role) })

  const cancel = useMutation({
    mutationFn: cancelPayment,
    onSuccess: () => { refresh(); notify.success('Đã huỷ giao dịch') },
    onError: e => notify.apiError(e, 'Không huỷ được giao dịch.'),
  })
  const cancelPlan = useMutation({
    mutationFn: cancelSubscription,
    onSuccess: () => { refresh(); notify.success('Đã huỷ gia hạn', 'Bạn vẫn dùng gói tới hết hạn.') },
    onError: e => notify.apiError(e, 'Không huỷ được gói đăng ký.'),
  })
  const resumePlan = useMutation({
    mutationFn: resumeSubscription,
    onSuccess: () => { refresh(); notify.success('Đã tiếp tục gói đăng ký') },
    onError: e => notify.apiError(e, 'Không tiếp tục được gói đăng ký.'),
  })
  const [refundFor, setRefundFor] = useState<Payment | null>(null)
  const [downloadError, setDownloadError] = useState<string | null>(null)

  const refundedIds = new Set((refunds.data ?? []).map(r => r.paymentId))
  const sortedPayments = [...(payments.data ?? [])].sort((a, b) => b.paymentId - a.paymentId)
  const invoiceList = invoices.data ?? []
  const refundList = refunds.data ?? []

  const download = async (invoiceId: number, number: string) => {
    setDownloadError(null)
    try {
      // Backend hiện trả file văn bản thuần dù endpoint tên là /pdf (FIX.md #39)
      await downloadFromApi(`/invoices/${invoiceId}/pdf`, `${number}.pdf`)
    } catch (err) {
      setDownloadError(getErrorMessage(err, 'Không tải được hoá đơn.'))
    }
  }
  const paymentPager = usePagination(sortedPayments, 15, null)
  const invoicePager = usePagination(invoiceList, 15, null)
  const refundPager = usePagination(refundList, 15, null)

  return (
    <div className="mx-auto max-w-5xl space-y-6">
      <PageHeader
        title="Gói & thanh toán"
        description={role === 'TEACHER'
          ? 'Gói dành cho giáo viên, giao dịch, hoá đơn và yêu cầu hoàn tiền của bạn.'
          : 'Gói học viên, giao dịch, hoá đơn, yêu cầu hoàn tiền và quyền học của bạn.'}
        action={(
          <Button className="h-10 rounded-xl px-4" onClick={() => setBuying({ open: true, planId: null })}>
            <Plus size={16} aria-hidden="true" /> Mua gói
          </Button>
        )}
      />

      <div className="-mx-4 overflow-x-auto px-4 scrollbar-none sm:mx-0 sm:px-0" role="tablist" aria-label="Thanh toán">
        <div className="inline-flex min-w-full gap-1 rounded-2xl bg-muted/60 p-1 sm:min-w-0">
          {TABS.map(t => (
            <button
              key={t.id}
              role="tab"
              aria-selected={tab === t.id}
              onClick={() => setTab(t.id)}
              className={cn(
                'flex min-h-10 flex-1 items-center justify-center gap-2 whitespace-nowrap rounded-xl px-4 text-sm font-medium transition-all sm:flex-none',
                tab === t.id ? 'bg-background text-foreground shadow-sm' : 'text-muted-foreground hover:text-foreground',
              )}
            >
              <t.icon size={16} aria-hidden="true" /> {t.label}
            </button>
          ))}
        </div>
      </div>

      {cancel.isError && <FormError message={getErrorMessage(cancel.error)} />}
      {downloadError && <FormError message={downloadError} />}

      <div role="tabpanel">
        {tab === 'payments' && (
          payments.isLoading ? <CardSkeleton count={2} className="md:grid-cols-1 xl:grid-cols-1" />
            : payments.isError ? <ErrorState />
              : sortedPayments.length === 0 ? <EmptyState icon={CreditCard} title="Chưa có giao dịch nào" />
                : (
                <>
                  <ul className="divide-y divide-border/60 overflow-hidden rounded-2xl border border-border/50 bg-card dark:bg-card/70">
                    {paymentPager.pageItems.map(p => {
                      const s = PAYMENT_STATUS[p.status] ?? { label: p.status, tone: 'muted' as const }
                      return (
                        <li key={p.paymentId} className="flex flex-col gap-3 p-4 sm:flex-row sm:items-center">
                          <div className="min-w-0 flex-1">
                            <div className="flex flex-wrap items-center gap-2">
                              <p className="font-semibold tabular-nums text-foreground">{formatMoney(p.amount)}</p>
                              <Pill tone={s.tone}>{s.label}</Pill>
                              <span className="text-xs text-muted-foreground">{p.paymentMethod}</span>
                            </div>
                            <p className="mt-0.5 truncate text-sm text-muted-foreground">{p.planName || p.orderInfo || `Gói #${p.productId}`}</p>
                            <p className="text-xs text-muted-foreground">#{p.paymentId} · {formatDateTime(p.createdAt)}</p>
                          </div>
                          <div className="flex flex-wrap gap-2">
                            {p.status === 'PENDING' && p.paymentUrl && (
                              <a href={p.paymentUrl} target="_blank" rel="noopener noreferrer" className="flex h-9 items-center gap-1.5 rounded-lg bg-primary px-3 text-sm font-medium text-primary-foreground hover:bg-primary/85">
                                <ExternalLink size={14} aria-hidden="true" /> Thanh toán
                              </a>
                            )}
                            {p.status === 'PENDING' && (
                              <Button variant="outline" className="h-9 rounded-lg" disabled={cancel.isPending} onClick={async () => { if (await confirmDialog({ title: 'Huỷ giao dịch này?', confirmText: 'Huỷ giao dịch', cancelText: 'Giữ lại', destructive: true })) cancel.mutate(p.paymentId) }}>
                                <XCircle size={14} aria-hidden="true" /> Huỷ
                              </Button>
                            )}
                            {p.status === 'SUCCESS' && !refundedIds.has(p.paymentId) && (
                              <Button variant="outline" className="h-9 rounded-lg" onClick={() => setRefundFor(p)}>
                                <RotateCcw size={14} aria-hidden="true" /> Yêu cầu hoàn tiền
                              </Button>
                            )}
                          </div>
                        </li>
                      )
                    })}
                  </ul>
                  <Pagination page={paymentPager.page} totalPages={paymentPager.totalPages} total={paymentPager.total} pageSize={paymentPager.pageSize} onChange={paymentPager.setPage} label="Phân trang giao dịch" />
                </>
                )
        )}

        {tab === 'invoices' && (
          invoices.isLoading ? <CardSkeleton count={2} className="md:grid-cols-1 xl:grid-cols-1" />
            : (invoices.data ?? []).length === 0 ? <EmptyState icon={FileText} title="Chưa có hoá đơn" description="Hoá đơn được tạo khi giao dịch thành công." />
              : (
              <>
                <ul className="divide-y divide-border/60 overflow-hidden rounded-2xl border border-border/50 bg-card dark:bg-card/70">
                  {invoicePager.pageItems.map(i => (
                    <li key={i.id} className="flex items-center gap-3 p-4">
                      <div className="min-w-0 flex-1">
                        <p className="font-mono text-sm font-semibold text-foreground">{i.invoiceNumber}</p>
                        <p className="text-xs text-muted-foreground">{formatMoney(i.amount)} · Giao dịch #{i.paymentId} · {formatDateTime(i.issuedAt ?? i.createdAt)}</p>
                      </div>
                      <Pill tone={i.status === 'ISSUED' ? 'emerald' : 'muted'}>{i.status === 'ISSUED' ? 'Đã phát hành' : 'Đã huỷ'}</Pill>
                      <Button variant="outline" className="h-9 rounded-lg" onClick={() => download(i.id, i.invoiceNumber)}>
                        <Download size={14} aria-hidden="true" /> Tải
                      </Button>
                    </li>
                  ))}
                </ul>
                <Pagination page={invoicePager.page} totalPages={invoicePager.totalPages} total={invoicePager.total} pageSize={invoicePager.pageSize} onChange={invoicePager.setPage} label="Phân trang hoá đơn" />
              </>
              )
        )}

        {tab === 'refunds' && (
          refunds.isLoading ? <CardSkeleton count={2} className="md:grid-cols-1 xl:grid-cols-1" />
            : (refunds.data ?? []).length === 0 ? <EmptyState icon={Undo2} title="Chưa có yêu cầu hoàn tiền" description="Gửi yêu cầu từ một giao dịch thành công ở tab Giao dịch." />
              : (
              <>
                <ul className="divide-y divide-border/60 overflow-hidden rounded-2xl border border-border/50 bg-card dark:bg-card/70">
                  {refundPager.pageItems.map(r => {
                    const s = REFUND_STATUS[r.status] ?? { label: r.status, tone: 'blue' as const }
                    return (
                      <li key={r.refundId} className="flex flex-col gap-1 p-4 sm:flex-row sm:items-center sm:gap-3">
                        <div className="min-w-0 flex-1">
                          <p className="font-semibold tabular-nums text-foreground">{formatMoney(r.amount)}</p>
                          <p className="truncate text-sm text-muted-foreground">Lý do: {r.reason}</p>
                          <p className="text-xs text-muted-foreground">Giao dịch #{r.paymentId} · {formatDateTime(r.createdAt)}</p>
                          <p className="mt-1 text-xs text-muted-foreground">
                            {r.bankAccount
                              ? `Nhận tại ${r.bankName} · ${r.bankAccount} · ${r.accountHolder}`
                              : 'Yêu cầu cũ chưa có thông tin tài khoản nhận.'}
                          </p>
                          {r.status === 'APPROVED' && (
                            <p className="mt-1 text-xs text-amber-700 dark:text-amber-400">
                              Đã duyệt, đang chờ ngân hàng xác nhận chuyển tiền.
                            </p>
                          )}
                          {r.status === 'COMPLETED' && r.sepayReferenceCode && (
                            <p className="mt-1 text-xs text-emerald-700 dark:text-emerald-400">
                              Mã đối soát: {r.sepayReferenceCode}
                            </p>
                          )}
                        </div>
                        <Pill tone={s.tone}>{s.label}</Pill>
                      </li>
                    )
                  })}
                </ul>
                <Pagination page={refundPager.page} totalPages={refundPager.totalPages} total={refundPager.total} pageSize={refundPager.pageSize} onChange={refundPager.setPage} label="Phân trang hoàn tiền" />
              </>
              )
        )}

        {tab === 'access' && (
          access.isLoading ? <CardSkeleton count={2} className="md:grid-cols-1 xl:grid-cols-1" />
            : (access.data ?? []).length === 0 ? <EmptyState icon={BadgeCheck} title="Chưa có quyền học nào" description="Quyền học được cấp sau khi thanh toán thành công." />
              : (
                <ul className="grid gap-3 sm:grid-cols-2">
                  {access.data!.map(a => (
                    <li key={a.id} className="rounded-2xl border border-border/50 bg-card p-4 dark:bg-card/70">
                      <div className="flex items-center justify-between gap-2">
                        <p className="font-semibold text-foreground">Sản phẩm #{a.productId}</p>
                        <Pill tone="emerald">{a.accessType}</Pill>
                      </div>
                      <p className="mt-1 text-xs text-muted-foreground">
                        Cấp lúc {formatDateTime(a.grantedAt)} · {a.expiresAt ? `Hết hạn ${formatDateTime(a.expiresAt)}` : 'Không thời hạn'}
                      </p>
                    </li>
                  ))}
                </ul>
              )
        )}

        {tab === 'subscriptions' && (
          <div className="space-y-6">
            <MySubscriptions
              query={subscriptions}
              busy={cancelPlan.isPending || resumePlan.isPending}
              onCancel={async sub => {
                const ok = await confirmDialog({
                  title: `Huỷ gia hạn gói "${sub.plan.name}"?`,
                  description: sub.expiresAt ? `Bạn vẫn dùng gói tới ${formatDateTime(sub.expiresAt)}.` : undefined,
                  confirmText: 'Huỷ gia hạn',
                  cancelText: 'Giữ gói',
                  destructive: true,
                })
                if (ok) cancelPlan.mutate(sub.subscriptionId)
              }}
              onResume={sub => resumePlan.mutate(sub.subscriptionId)}
            />
            <section className="space-y-3">
              <h2 className="text-lg font-semibold text-foreground">Chọn gói</h2>
              {plans.isLoading ? <CardSkeleton count={2} />
                : plans.isError ? <ErrorState />
                  : (plans.data ?? []).length === 0 ? <EmptyState icon={Crown} title="Chưa có gói nào đang bán" />
                    : (
                      <ul className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
                        {plans.data!.map(plan => (
                          <PlanCard
                            key={plan.planId}
                            plan={plan}
                            current={(subscriptions.data ?? []).some(x => x.status === 'ACTIVE' && x.plan.planId === plan.planId)}
                            onBuy={() => setBuying({ open: true, planId: plan.planId })}
                          />
                        ))}
                      </ul>
                    )}
            </section>
          </div>
        )}
      </div>

      <CreatePaymentDialog
        open={buying.open}
        planId={buying.planId}
        plans={plans.data ?? []}
        returnPath={role === 'TEACHER' ? '/teacher/payments' : '/student/payments'}
        onClose={() => setBuying({ open: false, planId: null })}
        onCreated={refresh}
      />

      <RefundDialog payment={refundFor} onClose={() => setRefundFor(null)} onDone={refresh} />
    </div>
  )
}

function MySubscriptions({ query, busy, onCancel, onResume }: {
  query: { data?: Subscription[]; isLoading: boolean; isError: boolean }
  busy: boolean
  onCancel: (sub: Subscription) => void
  onResume: (sub: Subscription) => void
}) {
  if (query.isLoading) return <CardSkeleton count={1} className="md:grid-cols-1 xl:grid-cols-1" />
  if (query.isError) return <ErrorState />
  const list = [...(query.data ?? [])].sort((a, b) =>
    Number(b.status === 'ACTIVE') - Number(a.status === 'ACTIVE') || b.subscriptionId - a.subscriptionId)
  if (list.length === 0) {
    return <EmptyState icon={Crown} title="Bạn chưa có gói nào" description="Chọn một gói bên dưới và hoàn tất thanh toán để kích hoạt." />
  }
  return (
    <section className="space-y-3">
      <h2 className="text-lg font-semibold text-foreground">Gói của bạn</h2>
      <ul className="grid gap-3 sm:grid-cols-2">
        {list.map(sub => {
          const st = SUBSCRIPTION_STATUS[sub.status] ?? { label: sub.status, tone: 'muted' as const }
          return (
            <li key={sub.subscriptionId} className={cn('rounded-2xl border bg-card p-4 dark:bg-card/70', sub.status === 'ACTIVE' ? 'border-primary/30' : 'border-border/50')}>
              <div className="flex items-start justify-between gap-3">
                <div className="min-w-0">
                  <p className="truncate font-semibold text-foreground">{sub.plan.name}</p>
                  <p className="text-xs text-muted-foreground">{BILLING_CYCLE_LABEL[sub.plan.billingCycle] ?? sub.plan.billingCycle}</p>
                </div>
                <Pill tone={st.tone}>{st.label}</Pill>
              </div>
              <p className="mt-3 text-xs text-muted-foreground">
                Bắt đầu {formatDateTime(sub.startedAt)} · {sub.expiresAt ? `Hết hạn ${formatDateTime(sub.expiresAt)}` : 'Không thời hạn'}
              </p>
              {sub.status === 'ACTIVE' && sub.cancelAtPeriodEnd && (
                <p className="mt-1 text-xs text-amber-600 dark:text-amber-400">Đã huỷ gia hạn — gói kết thúc khi hết hạn.</p>
              )}
              {sub.status === 'ACTIVE' && (
                <div className="mt-3">
                  {sub.cancelAtPeriodEnd
                    ? <Button variant="outline" className="h-9 rounded-lg" disabled={busy} onClick={() => onResume(sub)}>Tiếp tục gia hạn</Button>
                    : <Button variant="outline" className="h-9 rounded-lg" disabled={busy} onClick={() => onCancel(sub)}>Huỷ gia hạn</Button>}
                </div>
              )}
            </li>
          )
        })}
      </ul>
    </section>
  )
}

function PlanCard({ plan, current, onBuy }: { plan: SubscriptionPlan; current: boolean; onBuy: () => void }) {
  return (
    <li className="flex flex-col rounded-2xl border border-border/50 bg-card p-5 dark:bg-card/70">
      <div className="flex items-start justify-between gap-2">
        <h3 className="font-semibold text-foreground">{plan.name}</h3>
        {current && <Pill tone="emerald">Đang dùng</Pill>}
      </div>
      {plan.description && <p className="mt-1 text-sm text-muted-foreground">{plan.description}</p>}
      <p className="mt-4">
        <span className="text-2xl font-bold tabular-nums tracking-tight text-foreground">{formatMoney(plan.price)}</span>
        <span className="ml-1 text-sm text-muted-foreground">{planPeriod(plan.billingCycle, plan.durationDays)}</span>
      </p>
      <ul className="mt-4 flex-1 space-y-2 text-sm text-foreground">
        {plan.features.map(f => (
          <li key={f} className="flex items-start gap-2">
            <Check size={16} className="mt-0.5 shrink-0 text-emerald-600 dark:text-emerald-400" aria-hidden="true" />{f}
          </li>
        ))}
      </ul>
      <Button className="mt-5 h-10 w-full rounded-xl" variant={current ? 'outline' : 'default'} onClick={onBuy}>
        {current ? 'Gia hạn thêm' : 'Chọn gói này'}
      </Button>
    </li>
  )
}

function CreatePaymentDialog({ open, planId, plans, returnPath, onClose, onCreated }: {
  open: boolean
  planId: number | null
  plans: SubscriptionPlan[]
  returnPath: string
  onClose: () => void
  onCreated: () => void
}) {
  return (
    <Dialog open={open} onOpenChange={next => { if (!next) onClose() }}>
      <DialogContent className="sm:max-w-md">
        {open && (
          <CreatePaymentForm
            key={planId ?? 'none'}
            initialPlanId={planId}
            plans={plans}
            returnPath={returnPath}
            onClose={onClose}
            onCreated={onCreated}
          />
        )}
      </DialogContent>
    </Dialog>
  )
}

function CreatePaymentForm({ initialPlanId, plans, returnPath, onClose, onCreated }: {
  initialPlanId: number | null
  plans: SubscriptionPlan[]
  returnPath: string
  onClose: () => void
  onCreated: () => void
}) {
  const queryClient = useQueryClient()
  const [form, setForm] = useState({ planId: initialPlanId ? String(initialPlanId) : '', method: 'SEPAY' as PaymentMethod, orderInfo: '' })
  const set = (k: keyof typeof form) => (e: { target: { value: string } }) => setForm(p => ({ ...p, [k]: e.target.value }))
  const selected = plans.find(x => String(x.planId) === form.planId)

  const mutation = useMutation({
    mutationFn: () => createPayment({
      planId: Number(form.planId),
      paymentMethod: form.method,
      orderInfo: form.orderInfo.trim() || undefined,
      returnUrl: `${window.location.origin}${returnPath}`,
    }),
    onSuccess: onCreated,
  })

  const paymentId = mutation.data?.paymentId

  const statusQuery = useQuery({
    queryKey: ['payment', paymentId],
    queryFn: () => getPayment(paymentId!),
    enabled: Boolean(paymentId),
    refetchInterval: (query) => {
      const s = query.state.data?.status
      return s === 'SUCCESS' || s === 'FAILED' || s === 'CANCELLED' ? false : 2000
    },
  })

  const isSuccess = statusQuery.data?.status === 'SUCCESS'

  useEffect(() => {
    if (isSuccess) {
      queryClient.invalidateQueries({ queryKey: KEY })
      queryClient.invalidateQueries({ queryKey: ['billing'] })
      queryClient.invalidateQueries({ queryKey: ['my-access'] })
      queryClient.invalidateQueries({ queryKey: ['my-subscriptions'] })
      notify.success('Thanh toán thành công! Gói cước đã được kích hoạt.')
    }
  }, [isSuccess, queryClient])

  const handleSubmit = (e: FormEvent) => {
    e.preventDefault()
    mutation.mutate()
  }

  if (mutation.data) {
    if (isSuccess) {
      return (
        <div className="space-y-4 py-4 text-center">
          <div className="mx-auto flex h-16 w-16 items-center justify-center rounded-full bg-emerald-500/10 text-emerald-500">
            <CheckCircle2 size={40} className="animate-in zoom-in" />
          </div>
          <DialogHeader>
            <DialogTitle className="text-xl text-center">Thanh toán thành công!</DialogTitle>
            <DialogDescription className="text-center">
              Gói <strong>{mutation.data.planName ?? 'đăng ký'}</strong> đã được kích hoạt cho tài khoản của bạn.
            </DialogDescription>
          </DialogHeader>
          <div className="rounded-xl bg-muted/40 p-3 text-xs text-muted-foreground text-center">
            Mã giao dịch: <span className="font-mono font-semibold text-foreground">{mutation.data.transactionId}</span>
          </div>
          <DialogFooter className="justify-center sm:justify-center">
            <Button className="h-10 rounded-xl px-6" onClick={() => { onCreated(); onClose() }}>
              Hoàn tất & Sử dụng ngay
            </Button>
          </DialogFooter>
        </div>
      )
    }

    return (
      <div className="space-y-4">
        <DialogHeader>
          <DialogTitle>Đã tạo giao dịch #{mutation.data.paymentId}</DialogTitle>
          <DialogDescription>
            {mutation.data.planName ?? 'Gói đăng ký'} · {formatMoney(mutation.data.amount)}. Quét QR bằng ứng dụng ngân hàng; gói được kích hoạt ngay khi SePay xác nhận tiền vào.
          </DialogDescription>
        </DialogHeader>
        {mutation.data.paymentUrl && <img src={mutation.data.paymentUrl} alt={`Mã QR thanh toán ${mutation.data.transactionId}`} className="mx-auto w-full max-w-xs rounded-xl border border-border" />}
        <p className="text-center font-mono text-sm font-semibold text-foreground">Nội dung: {mutation.data.transactionId}</p>

        <div className="flex items-center justify-center gap-2 rounded-xl bg-muted/40 py-2.5 text-xs text-muted-foreground border border-border/40">
          <Loader2 size={14} className="animate-spin text-primary shrink-0" />
          <span>Hệ thống đang tự động chờ SePay xác nhận giao dịch...</span>
        </div>

        <DialogFooter className="gap-2">
          <Button variant="outline" className="h-10 rounded-xl" onClick={onClose}>Để sau</Button>
          {mutation.data.paymentUrl && <a href={mutation.data.paymentUrl} target="_blank" rel="noopener noreferrer" className="flex h-10 items-center justify-center gap-1.5 rounded-xl bg-primary px-4 text-sm font-medium text-primary-foreground hover:bg-primary/85"><ExternalLink size={15} aria-hidden="true" /> Mở ảnh QR</a>}
        </DialogFooter>
      </div>
    )
  }

  return (
    <form onSubmit={handleSubmit} className="space-y-4">
      <DialogHeader>
        <DialogTitle>Mua gói</DialogTitle>
        <DialogDescription>Giá được hệ thống tính theo gói bạn chọn.</DialogDescription>
      </DialogHeader>
      <Field label="Gói đăng ký *">
        <Select required value={form.planId} onChange={set('planId')}>
          <option value="">Chọn gói</option>
          {plans.map(plan => <option key={plan.planId} value={plan.planId}>{plan.name} — {formatMoney(plan.price)}</option>)}
        </Select>
      </Field>
      {selected && (
        <p className="rounded-xl bg-muted/60 px-3 py-2 text-sm text-foreground">
          Thanh toán <b>{formatMoney(selected.price)}</b> {planPeriod(selected.billingCycle, selected.durationDays)}
        </p>
      )}
      <Field label="Phương thức">
        <Select value={form.method} onChange={set('method')}><option value="SEPAY">SePay (QR chuyển khoản)</option></Select>
      </Field>
      <Field label="Ghi chú (tuỳ chọn)">
        <Input maxLength={200} value={form.orderInfo} onChange={set('orderInfo')} className="h-11" placeholder="Để trống sẽ ghi tên gói" />
      </Field>
      <FormError message={mutation.isError ? getErrorMessage(mutation.error, 'Không tạo được giao dịch.') : null} />
      <DialogFooter><SubmitButton pending={mutation.isPending}>Tạo giao dịch</SubmitButton></DialogFooter>
    </form>
  )
}

function RefundDialog({ payment, onClose, onDone }: { payment: Payment | null; onClose: () => void; onDone: () => void }) {
  const emptyForm = { reason: '', bankName: '', bankAccount: '', accountHolder: '' }
  const [form, setForm] = useState(emptyForm)
  const mutation = useMutation({
    mutationFn: () => requestRefund({
      paymentId: payment!.paymentId,
      reason: form.reason.trim(),
      bankName: form.bankName.trim(),
      bankAccount: form.bankAccount.trim(),
      accountHolder: form.accountHolder.trim(),
    }),
    onSuccess: () => { onDone(); setForm(emptyForm); onClose() },
  })
  const set = (key: keyof typeof form) => (e: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) =>
    setForm(current => ({ ...current, [key]: e.target.value }))
  const valid = form.reason.trim() && /^[A-Za-z0-9]{2,30}$/.test(form.bankName.trim())
    && /^[A-Za-z0-9]{4,34}$/.test(form.bankAccount.trim()) && form.accountHolder.trim()
  const handleSubmit = (e: FormEvent) => {
    e.preventDefault()
    if (valid) mutation.mutate()
  }
  return (
    <Dialog open={payment != null} onOpenChange={open => { if (!open) { mutation.reset(); setForm(emptyForm); onClose() } }}>
      <DialogContent className="sm:max-w-md">
        <form onSubmit={handleSubmit} className="space-y-4">
          <DialogHeader>
            <DialogTitle>Yêu cầu hoàn tiền</DialogTitle>
            <DialogDescription>
              Giao dịch #{payment?.paymentId} · {payment ? formatMoney(payment.amount) : ''}. Mỗi giao dịch chỉ gửi được một yêu cầu.
            </DialogDescription>
          </DialogHeader>
          <Field label="Lý do *"><TextArea required maxLength={1000} rows={3} value={form.reason} onChange={set('reason')} /></Field>
          <div className="grid gap-4 sm:grid-cols-2">
            <Field label="Ngân hàng nhận *">
              <Input required maxLength={30} pattern="[A-Za-z0-9]{2,30}" placeholder="VD: Vietcombank" value={form.bankName} onChange={set('bankName')} />
            </Field>
            <Field label="Số tài khoản *">
              <Input required maxLength={34} pattern="[A-Za-z0-9]{4,34}" inputMode="numeric" autoComplete="off" value={form.bankAccount} onChange={set('bankAccount')} />
            </Field>
          </div>
          <Field label="Tên chủ tài khoản *">
            <Input required maxLength={150} autoComplete="name" placeholder="Nhập đúng tên hiển thị tại ngân hàng" value={form.accountHolder} onChange={set('accountHolder')} />
          </Field>
          <p className="text-xs text-muted-foreground">Thông tin này được khóa sau khi admin duyệt để tránh chuyển nhầm tài khoản.</p>
          <FormError message={mutation.isError ? getErrorMessage(mutation.error) : null} />
          <DialogFooter><SubmitButton pending={mutation.isPending} disabled={!valid}>Gửi yêu cầu</SubmitButton></DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
