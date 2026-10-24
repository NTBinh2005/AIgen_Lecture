import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { Wallet as WalletIcon, ArrowUpRight, Plus, Copy, Check, Info, RefreshCw, BookOpen, Undo2 } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import {
  Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle,
} from '@/components/ui/dialog'
import { PageHeader, CardSkeleton, EmptyState, Pill } from '@/components/student/StudentUi'
import { getMyWallet, getMyTransactions, createDeposit, type DepositResponse } from '@/api/walletApi'
import { getMyClassOrders, refundClass } from '@/api/classOrderApi'
import { formatMoney } from '@/lib/download'
import { formatDateTime, getErrorMessage } from '@/lib/format'
import { notify, confirmDialog } from '@/lib/notify'

export default function StudentWalletPage() {
  const queryClient = useQueryClient()
  const [depositOpen, setDepositOpen] = useState(false)
  const [depositAmount, setDepositAmount] = useState<number>(100000)
  const [depositResult, setDepositResult] = useState<DepositResponse | null>(null)
  const [copied, setCopied] = useState<string | null>(null)
  const [activeTab, setActiveTab] = useState<'transactions' | 'orders'>('transactions')

  // Lấy thông tin ví
  const walletQuery = useQuery({
    queryKey: ['myWallet'],
    queryFn: getMyWallet,
    refetchInterval: depositResult ? 4000 : false, // Poll nhanh khi đang chờ nạp tiền
  })

  // Lấy lịch sử biến động ví
  const transactionsQuery = useQuery({
    queryKey: ['myTransactions'],
    queryFn: () => getMyTransactions(0, 50),
  })

  // Lấy lịch sử mua lớp
  const ordersQuery = useQuery({
    queryKey: ['myClassOrders'],
    queryFn: () => getMyClassOrders(0, 50),
  })

  const depositMutation = useMutation({
    mutationFn: (amount: number) => createDeposit(amount),
    onSuccess: (data) => {
      setDepositResult(data)
    },
    onError: (err) => {
      notify.error(getErrorMessage(err, 'Không thể tạo mã nạp tiền'))
    },
  })

  const refundMutation = useMutation({
    mutationFn: (classId: number) => refundClass(classId),
    onSuccess: () => {
      notify.success('Hoàn tiền học phí về ví thành công 100%!')
      queryClient.invalidateQueries({ queryKey: ['myWallet'] })
      queryClient.invalidateQueries({ queryKey: ['myTransactions'] })
      queryClient.invalidateQueries({ queryKey: ['myClassOrders'] })
      queryClient.invalidateQueries({ queryKey: ['student'] })
    },
    onError: (err) => {
      notify.error(getErrorMessage(err, 'Không thể hoàn học phí'))
    },
  })

  const handleCopy = (text: string, field: string) => {
    navigator.clipboard.writeText(text)
    setCopied(field)
    setTimeout(() => setCopied(null), 2000)
  }

  const handleRefundClick = async (classId: number, className: string) => {
    const ok = await confirmDialog({
      title: 'Xác nhận hoàn học phí',
      description: `Bạn có chắc muốn hoàn tiền lớp "${className}"? Toàn bộ 100% học phí sẽ được cộng lại vào ví cá nhân và bạn sẽ rời khỏi lớp học.`,
      confirmText: 'Hoàn tiền ngay',
    })
    if (ok) {
      refundMutation.mutate(classId)
    }
  }

  const wallet = walletQuery.data

  return (
    <div className="mx-auto max-w-5xl space-y-6">
      <PageHeader
        title="Ví tiền học tập"
        description="Quản lý số dư, nạp tiền học phí và thanh toán các lớp học của bạn."
        action={
          <Button onClick={() => { setDepositResult(null); setDepositOpen(true) }} className="gap-2 rounded-xl">
            <Plus size={16} /> Nạp tiền vào ví
          </Button>
        }
      />

      {/* Thông tin số dư ví */}
      <div className="grid grid-cols-1 gap-4 md:grid-cols-3">
        <div className="rounded-2xl border border-primary/20 bg-linear-to-br from-primary/10 via-background to-card p-6 shadow-sm md:col-span-2">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-3">
              <div className="flex h-12 w-12 items-center justify-center rounded-xl bg-primary text-primary-foreground shadow-md">
                <WalletIcon size={24} />
              </div>
              <div>
                <p className="text-sm font-medium text-muted-foreground">Số dư khả dụng</p>
                <h2 className="text-3xl font-extrabold tracking-tight text-foreground">
                  {wallet ? formatMoney(wallet.balance) : '---'}
                </h2>
              </div>
            </div>
            <Button
              variant="outline"
              size="sm"
              onClick={() => {
                queryClient.invalidateQueries({ queryKey: ['myWallet'] })
                queryClient.invalidateQueries({ queryKey: ['myTransactions'] })
              }}
              className="gap-1.5 rounded-lg text-xs"
            >
              <RefreshCw size={13} className={walletQuery.isFetching ? 'animate-spin' : ''} />
              Làm mới
            </Button>
          </div>

          <div className="mt-4 flex items-start gap-2.5 rounded-xl border border-amber-500/20 bg-amber-500/5 p-3 text-xs text-amber-700 dark:text-amber-400">
            <Info size={16} className="mt-0.5 shrink-0" />
            <p>
              <strong>Lưu ý:</strong> Số dư ví chỉ dùng để thanh toán các khóa học trên nền tảng Edumind và không hỗ trợ rút về ngân hàng. Trong vòng 4 ngày kể từ khi mua lớp, nếu bạn đã học không quá 5% bài giảng, bạn có thể yêu cầu hoàn 100% học phí về lại ví.
            </p>
          </div>
        </div>

        <div className="flex flex-col justify-between rounded-2xl border border-border/50 bg-card p-6 shadow-sm">
          <div>
            <p className="text-sm font-medium text-muted-foreground">Nạp tiền nhanh</p>
            <p className="mt-1 text-xs text-muted-foreground">Chuyển khoản VietQR SePay tự động cộng tiền trong vài giây.</p>
          </div>
          <div className="mt-4 flex flex-wrap gap-2">
            {[50000, 100000, 200000, 500000].map((amt) => (
              <Button
                key={amt}
                variant="outline"
                size="sm"
                onClick={() => {
                  setDepositAmount(amt)
                  setDepositResult(null)
                  setDepositOpen(true)
                }}
                className="rounded-lg text-xs"
              >
                +{formatMoney(amt)}
              </Button>
            ))}
          </div>
        </div>
      </div>

      {/* Tabs */}
      <div className="flex border-b border-border">
        <button
          onClick={() => setActiveTab('transactions')}
          className={`flex items-center gap-2 border-b-2 px-4 py-2.5 text-sm font-semibold transition-colors ${
            activeTab === 'transactions'
              ? 'border-primary text-primary'
              : 'border-transparent text-muted-foreground hover:text-foreground'
          }`}
        >
          <ArrowUpRight size={16} /> Biến động số dư ví
        </button>
        <button
          onClick={() => setActiveTab('orders')}
          className={`flex items-center gap-2 border-b-2 px-4 py-2.5 text-sm font-semibold transition-colors ${
            activeTab === 'orders'
              ? 'border-primary text-primary'
              : 'border-transparent text-muted-foreground hover:text-foreground'
          }`}
        >
          <BookOpen size={16} /> Lịch sử mua khóa học ({ordersQuery.data?.totalElements ?? 0})
        </button>
      </div>

      {/* Tab Content: Transactions */}
      {activeTab === 'transactions' && (
        <div className="rounded-2xl border border-border/60 bg-card p-4 shadow-sm">
          {transactionsQuery.isLoading ? (
            <CardSkeleton count={4} />
          ) : !transactionsQuery.data?.content || transactionsQuery.data.content.length === 0 ? (
            <EmptyState
              icon={WalletIcon}
              title="Chưa có giao dịch ví nào"
              description="Các giao dịch nạp tiền, mua lớp học và hoàn tiền sẽ hiển thị tại đây."
            />
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-left text-sm">
                <thead>
                  <tr className="border-b border-border/50 text-xs font-semibold uppercase text-muted-foreground">
                    <th className="pb-3 pl-2">Thời gian</th>
                    <th className="pb-3">Loại giao dịch</th>
                    <th className="pb-3">Nội dung</th>
                    <th className="pb-3 text-right">Số tiền</th>
                    <th className="pb-3 pr-2 text-right">Số dư sau GD</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-border/40">
                  {transactionsQuery.data.content.map((tx) => {
                    const isPositive = Number(tx.amount) > 0
                    return (
                      <tr key={tx.transactionId} className="hover:bg-muted/30">
                        <td className="py-3.5 pl-2 text-xs text-muted-foreground">
                          {formatDateTime(tx.createdAt)}
                        </td>
                        <td className="py-3.5">
                          <Pill tone={isPositive ? 'emerald' : 'muted'}>
                            {tx.type === 'DEPOSIT' && 'Nạp tiền ví'}
                            {tx.type === 'CLASS_PURCHASE' && 'Mua khóa học'}
                            {tx.type === 'CLASS_REFUND_STUDENT' && 'Hoàn học phí'}
                            {tx.type === 'SUBSCRIPTION_PAYMENT' && 'Thanh toán gói'}
                          </Pill>
                        </td>
                        <td className="max-w-xs truncate py-3.5 text-foreground font-medium">
                          {tx.description}
                        </td>
                        <td className={`py-3.5 text-right font-bold ${isPositive ? 'text-emerald-600 dark:text-emerald-400' : 'text-rose-600 dark:text-rose-400'}`}>
                          {isPositive ? '+' : ''}{formatMoney(tx.amount)}
                        </td>
                        <td className="py-3.5 pr-2 text-right font-medium text-muted-foreground">
                          {formatMoney(tx.balanceAfter)}
                        </td>
                      </tr>
                    )
                  })}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}

      {/* Tab Content: Orders */}
      {activeTab === 'orders' && (
        <div className="rounded-2xl border border-border/60 bg-card p-4 shadow-sm">
          {ordersQuery.isLoading ? (
            <CardSkeleton count={3} />
          ) : !ordersQuery.data?.content || ordersQuery.data.content.length === 0 ? (
            <EmptyState
              icon={BookOpen}
              title="Chưa có khóa học nào đã mua"
              description="Các khóa học bạn mua hoặc được hoàn tiền sẽ xuất hiện ở đây."
            />
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-left text-sm">
                <thead>
                  <tr className="border-b border-border/50 text-xs font-semibold uppercase text-muted-foreground">
                    <th className="pb-3 pl-2">Mã đơn</th>
                    <th className="pb-3">Khóa học</th>
                    <th className="pb-3">Học phí</th>
                    <th className="pb-3">Thời gian mua</th>
                    <th className="pb-3">Trạng thái</th>
                    <th className="pb-3 pr-2 text-right">Thao tác</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-border/40">
                  {ordersQuery.data.content.map((order) => {
                    const isRefunded = order.status === 'REFUNDED'
                    const isPending = order.status === 'PENDING'
                    const isCancelled = order.status === 'CANCELLED'
                    const holdDate = new Date(order.holdUntil)
                    const canStillRefundTime = holdDate > new Date() && order.status === 'ACTIVE'

                    return (
                      <tr key={order.orderId} className="hover:bg-muted/30">
                        <td className="py-3.5 pl-2 font-mono text-xs font-bold text-muted-foreground">
                          {order.orderCode}
                        </td>
                        <td className="py-3.5 font-semibold text-foreground">
                          {order.className}
                        </td>
                        <td className="py-3.5 font-bold text-primary">
                          {order.price > 0 ? formatMoney(order.price) : 'Miễn phí'}
                        </td>
                        <td className="py-3.5 text-xs text-muted-foreground">
                          {formatDateTime(order.createdAt)}
                        </td>
                        <td className="py-3.5">
                          <Pill tone={isRefunded || isCancelled ? 'red' : isPending ? 'amber' : 'emerald'}>
                            {isRefunded ? 'Đã hoàn tiền' : isCancelled ? 'Đã hủy' : isPending ? 'Chờ thanh toán' : 'Đang học'}
                          </Pill>
                        </td>
                        <td className="py-3.5 pr-2 text-right">
                          {order.price > 0 && order.status === 'ACTIVE' && (
                            <Button
                              variant="ghost"
                              size="sm"
                              disabled={!canStillRefundTime || refundMutation.isPending}
                              onClick={() => handleRefundClick(order.classId, order.className)}
                              className="gap-1 text-xs text-rose-600 hover:bg-rose-50 hover:text-rose-700 dark:hover:bg-rose-950/30"
                              title={canStillRefundTime ? 'Hoàn tiền trong 4 ngày nếu học <= 5%' : 'Đã quá thời hạn 4 ngày để hoàn tiền'}
                            >
                              <Undo2 size={14} /> Hoàn học phí
                            </Button>
                          )}
                        </td>
                      </tr>
                    )
                  })}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}

      {/* Modal Nạp tiền */}
      <Dialog open={depositOpen} onOpenChange={setDepositOpen}>
        <DialogContent className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle>Nạp tiền vào ví cá nhân</DialogTitle>
            <DialogDescription>
              {depositResult
                ? 'Quét mã VietQR bằng app ngân hàng bất kỳ để nạp tiền tự động.'
                : 'Nhập số tiền bạn muốn nạp (tối thiểu 10.000 VNĐ).'}
            </DialogDescription>
          </DialogHeader>

          {!depositResult ? (
            <div className="space-y-4 py-2">
              <div>
                <label className="text-xs font-semibold text-muted-foreground">Số tiền muốn nạp (VNĐ)</label>
                <Input
                  type="number"
                  min={10000}
                  step={10000}
                  value={depositAmount}
                  onChange={(e) => setDepositAmount(Number(e.target.value))}
                  className="mt-1.5 h-11 text-lg font-bold"
                />
              </div>

              <div className="flex flex-wrap gap-2">
                {[50000, 100000, 200000, 500000, 1000000].map((amt) => (
                  <button
                    key={amt}
                    type="button"
                    onClick={() => setDepositAmount(amt)}
                    className={`rounded-lg border px-3 py-1.5 text-xs font-medium transition-colors ${
                      depositAmount === amt
                        ? 'border-primary bg-primary/10 text-primary'
                        : 'border-border text-muted-foreground hover:bg-muted'
                    }`}
                  >
                    {formatMoney(amt)}
                  </button>
                ))}
              </div>

              <DialogFooter className="mt-4">
                <Button
                  onClick={() => depositMutation.mutate(depositAmount)}
                  disabled={depositAmount < 10000 || depositMutation.isPending}
                  className="w-full rounded-xl"
                >
                  {depositMutation.isPending ? 'Đang tạo mã QR...' : `Nạp ${formatMoney(depositAmount)}`}
                </Button>
              </DialogFooter>
            </div>
          ) : (
            <div className="space-y-4 text-center">
              <div className="mx-auto flex w-full max-w-[260px] flex-col items-center justify-center rounded-2xl border border-border/80 bg-white p-3 shadow-inner">
                <img
                  src={depositResult.qrUrl}
                  alt="VietQR SePay"
                  className="h-auto w-full object-contain"
                />
              </div>

              <div className="space-y-2 rounded-xl bg-muted/50 p-3 text-left text-xs">
                <div className="flex items-center justify-between">
                  <span className="text-muted-foreground">Ngân hàng:</span>
                  <span className="font-bold text-foreground">{depositResult.bankName}</span>
                </div>
                <div className="flex items-center justify-between">
                  <span className="text-muted-foreground">Số tài khoản:</span>
                  <button
                    onClick={() => handleCopy(depositResult.accountNumber, 'acc')}
                    className="flex items-center gap-1 font-mono font-bold text-foreground hover:text-primary"
                  >
                    {depositResult.accountNumber}
                    {copied === 'acc' ? <Check size={13} className="text-emerald-500" /> : <Copy size={13} />}
                  </button>
                </div>
                <div className="flex items-center justify-between">
                  <span className="text-muted-foreground">Chủ tài khoản:</span>
                  <span className="font-bold text-foreground">{depositResult.accountName}</span>
                </div>
                <div className="flex items-center justify-between">
                  <span className="text-muted-foreground">Số tiền:</span>
                  <span className="font-bold text-emerald-600 dark:text-emerald-400">{formatMoney(depositResult.amount)}</span>
                </div>
                <div className="flex items-center justify-between border-t border-border/60 pt-2">
                  <span className="text-muted-foreground">Nội dung chuyển khoản:</span>
                  <button
                    onClick={() => handleCopy(depositResult.description, 'des')}
                    className="flex items-center gap-1 rounded bg-amber-500/10 px-1.5 py-0.5 font-mono font-bold text-amber-600 dark:text-amber-400"
                  >
                    {depositResult.description}
                    {copied === 'des' ? <Check size={13} className="text-emerald-500" /> : <Copy size={13} />}
                  </button>
                </div>
              </div>

              <p className="text-xs text-muted-foreground">
                Hệ thống tự động cộng tiền ngay khi tiền vào tài khoản. Bạn có thể đóng cửa sổ này.
              </p>

              <Button
                variant="outline"
                onClick={() => {
                  setDepositOpen(false)
                  queryClient.invalidateQueries({ queryKey: ['myWallet'] })
                  queryClient.invalidateQueries({ queryKey: ['myTransactions'] })
                }}
                className="w-full rounded-xl"
              >
                Đóng
              </Button>
            </div>
          )}
        </DialogContent>
      </Dialog>
    </div>
  )
}
