import { useMemo, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { BadgeCheck, Check, CreditCard, Loader2, Search, Undo2, X } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Select } from '@/components/teacher/FormKit'
import { CardSkeleton, EmptyState, ErrorState, PageHeader, Pill } from '@/components/student/StudentUi'
import { getAllPayments, getAllRefunds, updateRefundStatus } from '@/api/adminApi'
import { adminKeys, useAdminUsers } from '@/hooks/useAdminData'
import { PAYMENT_STATUS, REFUND_STATUS } from '@/lib/billing'
import { isBankAccountComplete } from '@/lib/banks'
import { formatMoney } from '@/lib/download'
import { formatDateTime } from '@/lib/format'
import { confirmDialog, notify } from '@/lib/notify'
import { cn } from '@/lib/utils'
import type { PaymentStatus, Refund } from '@/types/billing'
import { usePagination } from '@/hooks/usePagination'
import { Pagination } from '@/components/common/Pagination'

type TabId = 'payments' | 'refunds'
type Decision = 'APPROVED' | 'REJECTED' | 'COMPLETED'

const DECISION: Record<Decision, { title: (id: number) => string; description: string; confirmText: string; done: string; destructive?: boolean }> = {
  APPROVED: {
    title: id => `Duyệt yêu cầu hoàn tiền #${id}?`,
    description: 'Hệ thống tạo mã QR chuyển tiền tới tài khoản người dùng đã khai báo; người dùng nhận thông báo. Sau khi duyệt không sửa được tài khoản nhận.',
    confirmText: 'Duyệt',
    done: 'Đã duyệt yêu cầu hoàn tiền',
  },
  REJECTED: {
    title: id => `Từ chối yêu cầu hoàn tiền #${id}?`,
    description: 'Yêu cầu sẽ đóng lại và không duyệt lại được.',
    confirmText: 'Từ chối',
    done: 'Đã từ chối yêu cầu hoàn tiền',
    destructive: true,
  },
  COMPLETED: {
    title: id => `Đánh dấu yêu cầu #${id} đã hoàn tiền xong?`,
    description: 'Chỉ dùng cho giao dịch cổng cũ (VNPay / MoMo) và chỉ xác nhận sau khi đã tự chuyển tiền lại cho người dùng.',
    confirmText: 'Đã hoàn tiền',
    done: 'Đã đánh dấu hoàn tiền xong',
  },
}

/** Giao dịch và yêu cầu hoàn tiền của mọi người dùng (GET /payments/admin, GET /refunds, PATCH /refunds/{id}/status). */
export default function AdminPaymentsPage() {
  const queryClient = useQueryClient()
  const [tab, setTab] = useState<TabId>('payments')
  const [search, setSearch] = useState('')
  const [status, setStatus] = useState<PaymentStatus | 'all'>('all')

  const payments = useQuery({ queryKey: adminKeys.payments, queryFn: getAllPayments })
  // Có yêu cầu đã duyệt (SePay) → tự làm mới để thấy "Đã hoàn tiền" khi SePay báo tiền ra
  const refunds = useQuery({
    queryKey: adminKeys.refunds,
    queryFn: getAllRefunds,
    refetchInterval: q => (q.state.data?.some(r => r.status === 'APPROVED') ? 15_000 : false),
  })
  const users = useAdminUsers()
  const methodByPayment = useMemo(() => new Map((payments.data ?? []).map(p => [p.paymentId, p.paymentMethod])), [payments.data])
  /** Hoàn tiền SePay chỉ được xác nhận bằng webhook tiền ra; cổng cũ vẫn đánh dấu tay. */
  const isSepay = (r: Refund) => methodByPayment.get(r.paymentId) === 'SEPAY'

  const userById = useMemo(() => new Map((users.data ?? []).map(u => [u.userId, u])), [users.data])
  const userLabel = (id: number) => {
    const u = userById.get(id)
    return u ? `${u.name} · ${u.email}` : `Người dùng #${id}`
  }

  const decide = useMutation({
    mutationFn: ({ refund, to }: { refund: Refund; to: Decision }) => updateRefundStatus(refund.refundId, to),
    onSuccess: (_d, { to }) => {
      queryClient.invalidateQueries({ queryKey: adminKeys.refunds })
      queryClient.invalidateQueries({ queryKey: adminKeys.payments })
      notify.success(DECISION[to].done)
    },
    onError: e => notify.apiError(e, 'Không cập nhật được yêu cầu hoàn tiền.'),
  })

  const ask = async (refund: Refund, to: Decision) => {
    const d = DECISION[to]
    const ok = await confirmDialog({ title: d.title(refund.refundId), description: d.description, confirmText: d.confirmText, destructive: d.destructive })
    if (ok) decide.mutate({ refund, to })
  }

  const keyword = search.trim().toLowerCase()
  const matches = (userId: number, ...extra: (string | number)[]) =>
    !keyword || userLabel(userId).toLowerCase().includes(keyword) || extra.some(x => String(x).toLowerCase().includes(keyword))

  const paymentList = (payments.data ?? [])
    .filter(p => status === 'all' || p.status === status)
    .filter(p => matches(p.userId, p.paymentId, p.transactionId))
  const refundList = (refunds.data ?? []).filter(r => matches(r.userId, r.refundId, r.paymentId, r.reason))

  const paymentPager = usePagination(paymentList, 15, `${search}|${status}`)
  const refundPager = usePagination(refundList, 15, search)
  const paid = (payments.data ?? []).filter(p => p.status === 'SUCCESS')
  const waiting = (refunds.data ?? []).filter(r => r.status === 'REQUESTED').length

  return (
    <div className="mx-auto max-w-6xl space-y-6">
      <PageHeader title="Thanh toán" description="Giao dịch của mọi người dùng và các yêu cầu hoàn tiền cần xử lý." />

      <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
        <Stat label="Giao dịch" value={String(payments.data?.length ?? 0)} />
        <Stat label="Thành công" value={String(paid.length)} />
        <Stat label="Doanh thu" value={formatMoney(paid.reduce((s, p) => s + Number(p.amount), 0))} />
        <Stat label="Hoàn tiền chờ duyệt" value={String(waiting)} highlight={waiting > 0} />
      </div>

      <div className="flex flex-col gap-3 sm:flex-row sm:items-center">
        <div role="tablist" aria-label="Loại dữ liệu" className="inline-flex shrink-0 rounded-xl bg-muted p-1">
          {([['payments', 'Giao dịch', CreditCard], ['refunds', 'Hoàn tiền', Undo2]] as const).map(([id, label, Icon]) => (
            <button
              key={id}
              role="tab"
              aria-selected={tab === id}
              onClick={() => setTab(id)}
              className={cn(
                'flex h-9 items-center gap-1.5 rounded-lg px-3 text-sm font-medium transition-colors',
                tab === id ? 'bg-card text-foreground shadow-sm' : 'text-muted-foreground hover:text-foreground',
              )}
            >
              <Icon size={15} aria-hidden="true" /> {label}
              {id === 'refunds' && waiting > 0 && <span className="rounded-full bg-amber-500/15 px-1.5 text-xs font-semibold text-amber-700 dark:text-amber-400">{waiting}</span>}
            </button>
          ))}
        </div>
        <div className="relative flex-1">
          <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" aria-hidden="true" />
          <Input type="search" value={search} onChange={e => setSearch(e.target.value)} placeholder="Tìm theo tên, email, mã giao dịch..." aria-label="Tìm kiếm" className="h-10 bg-card pl-9" />
        </div>
        {tab === 'payments' && (
          <Select aria-label="Lọc trạng thái" value={status} onChange={e => setStatus(e.target.value as PaymentStatus | 'all')} className="h-10 sm:w-48">
            <option value="all">Mọi trạng thái</option>
            {(Object.keys(PAYMENT_STATUS) as PaymentStatus[]).map(s => <option key={s} value={s}>{PAYMENT_STATUS[s].label}</option>)}
          </Select>
        )}
      </div>

      {tab === 'payments' ? (
        payments.isError ? <ErrorState /> : payments.isLoading ? <CardSkeleton count={3} /> : paymentList.length === 0 ? (
          <EmptyState icon={CreditCard} title="Không có giao dịch phù hợp" />
        ) : (
        <>
          <ul className="divide-y divide-border/60 overflow-hidden rounded-2xl border border-border/50 bg-card dark:bg-card/70">
            {paymentPager.pageItems.map(p => {
              const s = PAYMENT_STATUS[p.status] ?? { label: p.status, tone: 'muted' as const }
              return (
                <li key={p.paymentId} className="flex flex-col gap-2 p-4 sm:flex-row sm:items-center sm:justify-between">
                  <div className="min-w-0">
                    <p className="truncate font-medium text-foreground">{userLabel(p.userId)}</p>
                    <p className="mt-0.5 truncate text-xs text-muted-foreground">
                      #{p.paymentId} · {p.paymentMethod} · Sản phẩm #{p.productId} · {formatDateTime(p.createdAt)}
                    </p>
                    <p className="truncate font-mono text-[11px] text-muted-foreground/80">{p.transactionId}</p>
                  </div>
                  <div className="flex shrink-0 items-center gap-3">
                    <span className="font-semibold tabular-nums text-foreground">{formatMoney(Number(p.amount))}</span>
                    <Pill tone={s.tone}>{s.label}</Pill>
                  </div>
                </li>
              )
            })}
          </ul>
          <Pagination page={paymentPager.page} totalPages={paymentPager.totalPages} total={paymentPager.total} pageSize={paymentPager.pageSize} onChange={paymentPager.setPage} label="Phân trang giao dịch" />
        </>
        )
      ) : (
        refunds.isError ? <ErrorState /> : refunds.isLoading ? <CardSkeleton count={3} /> : refundList.length === 0 ? (
          <EmptyState icon={BadgeCheck} title="Không có yêu cầu hoàn tiền" description="Yêu cầu của học sinh sẽ hiện ở đây để bạn duyệt hoặc từ chối." />
        ) : (
        <>
          <ul className="space-y-3">
            {refundPager.pageItems.map(r => {
              const s = REFUND_STATUS[r.status] ?? { label: r.status, tone: 'blue' as const }
              const busy = decide.isPending && decide.variables?.refund.refundId === r.refundId
              return (
                <li key={r.refundId} className="rounded-2xl border border-border/50 bg-card p-4 dark:bg-card/70">
                  <div className="flex flex-col gap-2 sm:flex-row sm:items-start sm:justify-between">
                    <div className="min-w-0">
                      <p className="truncate font-medium text-foreground">{userLabel(r.userId)}</p>
                      <p className="mt-0.5 text-xs text-muted-foreground">
                        Yêu cầu #{r.refundId} · Giao dịch #{r.paymentId} · {formatDateTime(r.createdAt)}
                      </p>
                    </div>
                    <div className="flex shrink-0 items-center gap-3">
                      <span className="font-semibold tabular-nums text-foreground">{formatMoney(Number(r.amount))}</span>
                      <Pill tone={s.tone}>{s.label}</Pill>
                    </div>
                  </div>
                  <p className="mt-3 rounded-xl bg-muted/50 px-3 py-2 text-sm text-foreground">
                    <span className="text-muted-foreground">Lý do: </span>{r.reason}
                  </p>
                  <p className="mt-2 text-sm">
                    <span className="text-muted-foreground">Tài khoản nhận: </span>
                    {isBankAccountComplete(r)
                      ? <span className="font-medium text-foreground">{r.bankName} · {r.bankAccount} · {r.accountHolder}</span>
                      : <span className="text-amber-700 dark:text-amber-400">chưa có (yêu cầu cũ) — người dùng cần bổ sung trước khi chuyển tiền</span>}
                  </p>
                  {r.status === 'COMPLETED' && r.sepayReferenceCode && (
                    <p className="mt-1 text-xs text-muted-foreground">Đã đối soát qua SePay · mã giao dịch ngân hàng {r.sepayReferenceCode}</p>
                  )}
                  {r.status === 'APPROVED' && isSepay(r) && <RefundTransfer refund={r} />}
                  {(r.status === 'REQUESTED' || (r.status === 'APPROVED' && !isSepay(r))) && (
                    <div className="mt-3 flex flex-col gap-2 sm:flex-row sm:justify-end">
                      {r.status === 'REQUESTED' ? (
                        <>
                          <Button variant="outline" className="h-10 rounded-xl text-destructive hover:bg-destructive/10" disabled={busy} onClick={() => ask(r, 'REJECTED')}>
                            <X size={15} aria-hidden="true" /> Từ chối
                          </Button>
                          <Button className="h-10 rounded-xl" disabled={busy || (isSepay(r) && !isBankAccountComplete(r))} onClick={() => ask(r, 'APPROVED')}>
                            <Check size={15} aria-hidden="true" /> Duyệt
                          </Button>
                        </>
                      ) : (
                        <Button className="h-10 rounded-xl" disabled={busy} onClick={() => ask(r, 'COMPLETED')}>
                          <BadgeCheck size={15} aria-hidden="true" /> Đã hoàn tiền xong
                        </Button>
                      )}
                    </div>
                  )}
                </li>
              )
            })}
          </ul>
          <Pagination page={refundPager.page} totalPages={refundPager.totalPages} total={refundPager.total} pageSize={refundPager.pageSize} onChange={refundPager.setPage} label="Phân trang hoàn tiền" />
        </>
        )
      )}
    </div>
  )
}

function Stat({ label, value, highlight = false }: { label: string; value: string; highlight?: boolean }) {
  return (
    <div className={cn('rounded-2xl border bg-card p-4 dark:bg-card/70', highlight ? 'border-amber-500/40' : 'border-border/50')}>
      <p className="text-sm text-muted-foreground">{label}</p>
      <p className="mt-1 truncate text-2xl font-bold tabular-nums tracking-tight text-foreground">{value}</p>
    </div>
  )
}

/** Yêu cầu SePay đã duyệt: QR chuyển tiền tới tài khoản người dùng; hoàn tất tự động khi SePay báo giao dịch tiền ra. */
function RefundTransfer({ refund }: { refund: Refund }) {
  if (!refund.qrUrl) {
    return (
      <p className="mt-3 rounded-xl bg-amber-500/10 px-3 py-2 text-sm text-amber-800 dark:text-amber-300">
        Chưa tạo được mã QR vì người dùng chưa khai báo tài khoản nhận. Người dùng bổ sung tài khoản ở trang Gói &amp; thanh toán, yêu cầu sẽ quay lại chờ duyệt.
      </p>
    )
  }
  return (
    <div className="mt-3 flex flex-col gap-4 rounded-xl border border-primary/30 bg-primary/5 p-3 sm:flex-row sm:items-center">
      <a href={refund.qrUrl} target="_blank" rel="noopener noreferrer" className="mx-auto shrink-0 sm:mx-0">
        <img src={refund.qrUrl} alt={`Mã QR hoàn tiền ${refund.refundCode ?? ''}`} className="w-44 rounded-lg border border-border bg-white" />
      </a>
      <div className="space-y-1.5 text-sm text-foreground">
        <p className="font-semibold">Chuyển tiền hoàn bằng mã QR</p>
        <ol className="list-decimal space-y-1 pl-5 text-muted-foreground">
          <li>Mở app ngân hàng của <span className="text-foreground">tài khoản nhận tiền SePay</span> và quét mã.</li>
          <li>Kiểm tra người nhận <span className="font-medium text-foreground">{refund.accountHolder}</span> và số tiền <span className="font-medium text-foreground">{formatMoney(Number(refund.amount))}</span>.</li>
          <li>Giữ nguyên nội dung chuyển khoản <span className="font-mono font-semibold text-foreground">{refund.refundCode}</span>.</li>
        </ol>
        <p className="flex items-center gap-1.5 text-xs text-muted-foreground">
          <Loader2 size={13} className="animate-spin" aria-hidden="true" />
          Đang chờ SePay báo giao dịch tiền ra — trạng thái tự chuyển "Đã hoàn tiền". Chỉ hoạt động với ngân hàng SePay hỗ trợ báo tiền ra (Sacombank, TPBank, VietinBank).
        </p>
      </div>
    </div>
  )
}
