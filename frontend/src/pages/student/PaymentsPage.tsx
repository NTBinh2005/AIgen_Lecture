import { useState } from 'react'
import type { FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { BadgeCheck, CreditCard, Download, ExternalLink, FileText, Plus, RotateCcw, Undo2, XCircle } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import {
  Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle, DialogTrigger,
} from '@/components/ui/dialog'
import { Field, FormError, Select, SubmitButton, TextArea } from '@/components/teacher/FormKit'
import { CardSkeleton, EmptyState, ErrorState, PageHeader, Pill } from '@/components/student/StudentUi'
import {
  cancelPayment, createPayment, getMyAccess, getMyInvoices, getMyPayments, getMyRefunds, requestRefund,
} from '@/api/billingApi'
import { downloadFromApi, formatMoney } from '@/lib/download'
import { formatDateTime, getErrorMessage } from '@/lib/format'
import { cn } from '@/lib/utils'
import type { Payment, PaymentMethod, PaymentStatus, RefundStatus } from '@/types/billing'
import { confirmDialog, notify } from '@/lib/notify'

const KEY = ['billing'] as const

const PAYMENT_STATUS: Record<PaymentStatus, { label: string; tone: 'amber' | 'emerald' | 'red' | 'muted' | 'violet' }> = {
  PENDING: { label: 'Chờ thanh toán', tone: 'amber' },
  SUCCESS: { label: 'Thành công', tone: 'emerald' },
  FAILED: { label: 'Thất bại', tone: 'red' },
  CANCELLED: { label: 'Đã huỷ', tone: 'muted' },
  REFUNDED: { label: 'Đã hoàn tiền', tone: 'violet' },
}

const REFUND_STATUS: Record<RefundStatus, { label: string; tone: 'amber' | 'emerald' | 'red' | 'blue' }> = {
  REQUESTED: { label: 'Đã gửi yêu cầu', tone: 'amber' },
  PROCESSING: { label: 'Đang xử lý', tone: 'blue' },
  APPROVED: { label: 'Đã duyệt', tone: 'emerald' },
  COMPLETED: { label: 'Đã hoàn tiền', tone: 'emerald' },
  REJECTED: { label: 'Bị từ chối', tone: 'red' },
  FAILED: { label: 'Thất bại', tone: 'red' },
}

const TABS = [
  { id: 'payments', label: 'Giao dịch', icon: CreditCard },
  { id: 'invoices', label: 'Hoá đơn', icon: FileText },
  { id: 'refunds', label: 'Hoàn tiền', icon: Undo2 },
  { id: 'access', label: 'Quyền học', icon: BadgeCheck },
] as const
type TabId = (typeof TABS)[number]['id']

export default function StudentPaymentsPage() {
  const [tab, setTab] = useState<TabId>('payments')
  const queryClient = useQueryClient()
  const refresh = () => queryClient.invalidateQueries({ queryKey: KEY })

  const payments = useQuery({ queryKey: [...KEY, 'payments'], queryFn: getMyPayments })
  const invoices = useQuery({ queryKey: [...KEY, 'invoices'], queryFn: getMyInvoices })
  const refunds = useQuery({ queryKey: [...KEY, 'refunds'], queryFn: getMyRefunds })
  const access = useQuery({ queryKey: [...KEY, 'access'], queryFn: getMyAccess })

  const cancel = useMutation({
    mutationFn: cancelPayment,
    onSuccess: () => { refresh(); notify.success('Đã huỷ giao dịch') },
    onError: e => notify.apiError(e, 'Không huỷ được giao dịch.'),
  })
  const [refundFor, setRefundFor] = useState<Payment | null>(null)
  const [downloadError, setDownloadError] = useState<string | null>(null)

  const refundedIds = new Set((refunds.data ?? []).map(r => r.paymentId))
  const sortedPayments = [...(payments.data ?? [])].sort((a, b) => b.paymentId - a.paymentId)

  const download = async (invoiceId: number, number: string) => {
    setDownloadError(null)
    try {
      // Backend hiện trả file văn bản thuần dù endpoint tên là /pdf (FIX.md #39)
      await downloadFromApi(`/invoices/${invoiceId}/pdf`, `${number}.txt`)
    } catch (err) {
      setDownloadError(getErrorMessage(err, 'Không tải được hoá đơn.'))
    }
  }

  return (
    <div className="mx-auto max-w-5xl space-y-6">
      <PageHeader
        title="Thanh toán"
        description="Giao dịch, hoá đơn, yêu cầu hoàn tiền và quyền học của bạn."
        action={<CreatePaymentDialog onCreated={refresh} />}
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
                  <ul className="divide-y divide-border/60 overflow-hidden rounded-2xl border border-border/50 bg-card dark:bg-card/70">
                    {sortedPayments.map(p => {
                      const s = PAYMENT_STATUS[p.status] ?? { label: p.status, tone: 'muted' as const }
                      return (
                        <li key={p.paymentId} className="flex flex-col gap-3 p-4 sm:flex-row sm:items-center">
                          <div className="min-w-0 flex-1">
                            <div className="flex flex-wrap items-center gap-2">
                              <p className="font-semibold tabular-nums text-foreground">{formatMoney(p.amount)}</p>
                              <Pill tone={s.tone}>{s.label}</Pill>
                              <span className="text-xs text-muted-foreground">{p.paymentMethod}</span>
                            </div>
                            <p className="mt-0.5 truncate text-sm text-muted-foreground">{p.orderInfo || `Sản phẩm #${p.productId}`}</p>
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
                )
        )}

        {tab === 'invoices' && (
          invoices.isLoading ? <CardSkeleton count={2} className="md:grid-cols-1 xl:grid-cols-1" />
            : (invoices.data ?? []).length === 0 ? <EmptyState icon={FileText} title="Chưa có hoá đơn" description="Hoá đơn được tạo khi giao dịch thành công." />
              : (
                <ul className="divide-y divide-border/60 overflow-hidden rounded-2xl border border-border/50 bg-card dark:bg-card/70">
                  {invoices.data!.map(i => (
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
              )
        )}

        {tab === 'refunds' && (
          refunds.isLoading ? <CardSkeleton count={2} className="md:grid-cols-1 xl:grid-cols-1" />
            : (refunds.data ?? []).length === 0 ? <EmptyState icon={Undo2} title="Chưa có yêu cầu hoàn tiền" description="Gửi yêu cầu từ một giao dịch thành công ở tab Giao dịch." />
              : (
                <ul className="divide-y divide-border/60 overflow-hidden rounded-2xl border border-border/50 bg-card dark:bg-card/70">
                  {refunds.data!.map(r => {
                    const s = REFUND_STATUS[r.status] ?? { label: r.status, tone: 'blue' as const }
                    return (
                      <li key={r.refundId} className="flex flex-col gap-1 p-4 sm:flex-row sm:items-center sm:gap-3">
                        <div className="min-w-0 flex-1">
                          <p className="font-semibold tabular-nums text-foreground">{formatMoney(r.amount)}</p>
                          <p className="truncate text-sm text-muted-foreground">Lý do: {r.reason}</p>
                          <p className="text-xs text-muted-foreground">Giao dịch #{r.paymentId} · {formatDateTime(r.createdAt)}</p>
                        </div>
                        <Pill tone={s.tone}>{s.label}</Pill>
                      </li>
                    )
                  })}
                </ul>
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
      </div>

      <RefundDialog payment={refundFor} onClose={() => setRefundFor(null)} onDone={refresh} />
    </div>
  )
}

function CreatePaymentDialog({ onCreated }: { onCreated: () => void }) {
  const [open, setOpen] = useState(false)
  const [form, setForm] = useState({ productId: '', amount: '', method: 'VNPAY' as PaymentMethod, orderInfo: '' })
  const set = (k: keyof typeof form) => (e: { target: { value: string } }) => setForm(p => ({ ...p, [k]: e.target.value }))

  const mutation = useMutation({
    mutationFn: () => createPayment({
      productId: Number(form.productId),
      amount: Number(form.amount),
      paymentMethod: form.method,
      orderInfo: form.orderInfo.trim() || undefined,
      returnUrl: `${window.location.origin}/student/payments`,
    }),
    onSuccess: onCreated,
  })

  const handleSubmit = (e: FormEvent) => {
    e.preventDefault()
    mutation.mutate()
  }

  return (
    <Dialog open={open} onOpenChange={next => { setOpen(next); if (next) { mutation.reset(); setForm({ productId: '', amount: '', method: 'VNPAY', orderInfo: '' }) } }}>
      <DialogTrigger asChild>
        <Button className="h-10 rounded-xl px-4"><Plus size={16} aria-hidden="true" /> Tạo thanh toán</Button>
      </DialogTrigger>
      <DialogContent className="sm:max-w-md">
        {mutation.data ? (
          <div className="space-y-4">
            <DialogHeader>
              <DialogTitle>Đã tạo giao dịch #{mutation.data.paymentId}</DialogTitle>
              <DialogDescription>Mở cổng thanh toán để hoàn tất. Sau khi thanh toán, quay lại trang này để xem trạng thái.</DialogDescription>
            </DialogHeader>
            <DialogFooter className="gap-2">
              <Button variant="outline" className="h-10 rounded-xl" onClick={() => setOpen(false)}>Để sau</Button>
              {mutation.data.paymentUrl && (
                <a href={mutation.data.paymentUrl} target="_blank" rel="noopener noreferrer" className="flex h-10 items-center justify-center gap-1.5 rounded-xl bg-primary px-4 text-sm font-medium text-primary-foreground hover:bg-primary/85">
                  <ExternalLink size={15} aria-hidden="true" /> Mở cổng thanh toán
                </a>
              )}
            </DialogFooter>
          </div>
        ) : (
          <form onSubmit={handleSubmit} className="space-y-4">
            <DialogHeader>
              <DialogTitle>Tạo thanh toán</DialogTitle>
              <DialogDescription>Nhập mã sản phẩm/khoá học và số tiền được thông báo.</DialogDescription>
            </DialogHeader>
            <div className="grid gap-4 sm:grid-cols-2">
              <Field label="Mã sản phẩm *"><Input required type="number" min={1} value={form.productId} onChange={set('productId')} className="h-11" /></Field>
              <Field label="Số tiền (VND) *"><Input required type="number" min={1000} step={1000} value={form.amount} onChange={set('amount')} className="h-11" /></Field>
            </div>
            <Field label="Phương thức">
              <Select value={form.method} onChange={set('method')}>
                <option value="VNPAY">VNPay</option>
                <option value="MOMO">MoMo</option>
              </Select>
            </Field>
            <Field label="Nội dung"><Input maxLength={200} value={form.orderInfo} onChange={set('orderInfo')} className="h-11" placeholder="VD: Học phí lớp Lập trình Web" /></Field>
            <FormError message={mutation.isError ? getErrorMessage(mutation.error, 'Không tạo được giao dịch.') : null} />
            <DialogFooter><SubmitButton pending={mutation.isPending}>Tạo giao dịch</SubmitButton></DialogFooter>
          </form>
        )}
      </DialogContent>
    </Dialog>
  )
}

function RefundDialog({ payment, onClose, onDone }: { payment: Payment | null; onClose: () => void; onDone: () => void }) {
  const [reason, setReason] = useState('')
  const mutation = useMutation({
    mutationFn: () => requestRefund(payment!.paymentId, reason.trim()),
    onSuccess: () => { onDone(); setReason(''); onClose() },
  })
  const handleSubmit = (e: FormEvent) => {
    e.preventDefault()
    if (reason.trim()) mutation.mutate()
  }
  return (
    <Dialog open={payment != null} onOpenChange={open => { if (!open) { mutation.reset(); setReason(''); onClose() } }}>
      <DialogContent className="sm:max-w-md">
        <form onSubmit={handleSubmit} className="space-y-4">
          <DialogHeader>
            <DialogTitle>Yêu cầu hoàn tiền</DialogTitle>
            <DialogDescription>
              Giao dịch #{payment?.paymentId} · {payment ? formatMoney(payment.amount) : ''}. Mỗi giao dịch chỉ gửi được một yêu cầu.
            </DialogDescription>
          </DialogHeader>
          <Field label="Lý do *"><TextArea required maxLength={1000} rows={3} value={reason} onChange={e => setReason(e.target.value)} /></Field>
          <FormError message={mutation.isError ? getErrorMessage(mutation.error) : null} />
          <DialogFooter><SubmitButton pending={mutation.isPending} disabled={!reason.trim()}>Gửi yêu cầu</SubmitButton></DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
