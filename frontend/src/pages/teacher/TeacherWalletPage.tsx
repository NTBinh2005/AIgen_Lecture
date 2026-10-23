import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import {
  Wallet as WalletIcon, ArrowUpRight, Clock, Lock, Building,
  CheckCircle2, RefreshCw, Crown, ShieldAlert
} from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import {
  Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle,
} from '@/components/ui/dialog'
import { PageHeader, CardSkeleton, EmptyState, Pill } from '@/components/student/StudentUi'
import { getMyWallet, getMyTransactions, paySubscriptionWithWallet } from '@/api/walletApi'
import { requestWithdrawal, getMyWithdrawals, type WithdrawalCreatePayload } from '@/api/withdrawalApi'
import { getPlans } from '@/api/billingApi'
import { formatMoney } from '@/lib/download'
import { formatDateTime, getErrorMessage } from '@/lib/format'
import { notify } from '@/lib/notify'

const COMMON_BANKS = [
  'Vietcombank', 'MBBank', 'Techcombank', 'VPBank', 'ACB',
  'BIDV', 'VietinBank', 'TPBank', 'VIB', 'Sacombank'
]

export default function TeacherWalletPage() {
  const queryClient = useQueryClient()
  const [withdrawOpen, setWithdrawOpen] = useState(false)
  const [paySubOpen, setPaySubOpen] = useState(false)
  const [activeTab, setActiveTab] = useState<'transactions' | 'withdrawals'>('transactions')

  const [withdrawForm, setWithdrawForm] = useState<WithdrawalCreatePayload>({
    amount: 50000,
    bankName: 'Vietcombank',
    bankAccount: '',
    accountHolder: '',
  })

  // Lấy thông tin ví
  const walletQuery = useQuery({
    queryKey: ['myWallet'],
    queryFn: getMyWallet,
  })

  // Lấy lịch sử biến động ví
  const transactionsQuery = useQuery({
    queryKey: ['myTransactions'],
    queryFn: () => getMyTransactions(0, 50),
  })

  // Lấy lịch sử rút tiền
  const withdrawalsQuery = useQuery({
    queryKey: ['myWithdrawals'],
    queryFn: () => getMyWithdrawals(0, 50),
  })

  // Lấy các gói giáo viên
  const plansQuery = useQuery({
    queryKey: ['teacherPlans'],
    queryFn: () => getPlans('TEACHER'),
  })

  // Mutation rút tiền
  const withdrawMutation = useMutation({
    mutationFn: (payload: WithdrawalCreatePayload) => requestWithdrawal(payload),
    onSuccess: () => {
      notify.success('Gửi yêu cầu rút tiền thành công! Admin sẽ duyệt và giải ngân sớm.')
      setWithdrawOpen(false)
      queryClient.invalidateQueries({ queryKey: ['myWallet'] })
      queryClient.invalidateQueries({ queryKey: ['myTransactions'] })
      queryClient.invalidateQueries({ queryKey: ['myWithdrawals'] })
    },
    onError: (err) => {
      notify.error(getErrorMessage(err, 'Không thể gửi yêu cầu rút tiền'))
    },
  })

  // Mutation thanh toán gói bằng ví
  const paySubMutation = useMutation({
    mutationFn: (planId: number) => paySubscriptionWithWallet(planId),
    onSuccess: () => {
      notify.success('Gia hạn gói dịch vụ thành công từ số dư ví!')
      setPaySubOpen(false)
      queryClient.invalidateQueries({ queryKey: ['myWallet'] })
      queryClient.invalidateQueries({ queryKey: ['myTransactions'] })
      queryClient.invalidateQueries({ queryKey: ['billing'] })
    },
    onError: (err) => {
      notify.error(getErrorMessage(err, 'Không thể thanh toán gói dịch vụ'))
    },
  })

  const wallet = walletQuery.data

  return (
    <div className="mx-auto max-w-6xl space-y-6">
      <PageHeader
        title="Ví tiền & Doanh thu giáo viên"
        description="Quản lý học phí các lớp học, thời gian giữ tiền 4 ngày, yêu cầu rút tiền và thanh toán công cụ."
        action={
          <div className="flex items-center gap-2">
            <Button
              variant="outline"
              onClick={() => setPaySubOpen(true)}
              className="gap-2 rounded-xl"
            >
              <Crown size={16} className="text-amber-500" /> Thanh toán gói bằng ví
            </Button>
            <Button
              onClick={() => setWithdrawOpen(true)}
              disabled={!wallet || wallet.balance < 50000}
              className="gap-2 rounded-xl"
            >
              <Building size={16} /> Rút tiền về ngân hàng
            </Button>
          </div>
        }
      />

      {/* Thẻ số dư ví */}
      <div className="grid grid-cols-1 gap-4 md:grid-cols-3">
        {/* Số dư khả dụng */}
        <div className="rounded-2xl border border-emerald-500/20 bg-linear-to-br from-emerald-500/10 via-background to-card p-6 shadow-sm">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2 text-xs font-semibold uppercase tracking-wider text-emerald-600 dark:text-emerald-400">
              <CheckCircle2 size={16} /> Số dư khả dụng
            </div>
            <Button
              variant="ghost"
              size="sm"
              onClick={() => {
                queryClient.invalidateQueries({ queryKey: ['myWallet'] })
                queryClient.invalidateQueries({ queryKey: ['myTransactions'] })
              }}
              className="h-7 w-7 p-0"
            >
              <RefreshCw size={13} className={walletQuery.isFetching ? 'animate-spin' : ''} />
            </Button>
          </div>
          <h2 className="mt-3 text-3xl font-extrabold text-foreground">
            {wallet ? formatMoney(wallet.balance) : '---'}
          </h2>
          <p className="mt-2 text-xs text-muted-foreground">
            Tiền học phí đã qua thời gian giữ 4 ngày, có thể rút về ngân hàng hoặc mua gói công cụ.
          </p>
        </div>

        {/* Học phí tạm giữ 4 ngày */}
        <div className="rounded-2xl border border-amber-500/20 bg-linear-to-br from-amber-500/10 via-background to-card p-6 shadow-sm">
          <div className="flex items-center gap-2 text-xs font-semibold uppercase tracking-wider text-amber-600 dark:text-amber-400">
            <Clock size={16} /> Đang giữ 4 ngày
          </div>
          <h2 className="mt-3 text-3xl font-extrabold text-foreground">
            {wallet ? formatMoney(wallet.pendingBalance) : '---'}
          </h2>
          <p className="mt-2 text-xs text-muted-foreground">
            Học phí sinh viên mới thanh toán (95% sau khi trừ 5% phí nền tảng). Tự động giải ngân sau 4 ngày.
          </p>
        </div>

        {/* Đang chờ rút */}
        <div className="rounded-2xl border border-blue-500/20 bg-linear-to-br from-blue-500/10 via-background to-card p-6 shadow-sm">
          <div className="flex items-center gap-2 text-xs font-semibold uppercase tracking-wider text-blue-600 dark:text-blue-400">
            <Lock size={16} /> Đang chờ rút
          </div>
          <h2 className="mt-3 text-3xl font-extrabold text-foreground">
            {wallet ? formatMoney(wallet.lockedBalance) : '---'}
          </h2>
          <p className="mt-2 text-xs text-muted-foreground">
            Khoản tiền đã tạo yêu cầu rút và đang chờ Admin duyệt chuyển khoản.
          </p>
        </div>
      </div>

      {/* Chính sách và quy định rút tiền */}
      <div className="rounded-2xl border border-border/60 bg-muted/30 p-4 text-xs text-muted-foreground">
        <div className="flex items-center gap-2 font-semibold text-foreground">
          <ShieldAlert size={16} className="text-primary" />
          Chính sách tài chính & Rút tiền giáo viên:
        </div>
        <ul className="mt-2 list-inside list-disc space-y-1">
          <li><strong>Phí nền tảng:</strong> Hệ thống tự động khấu trừ 5% trên mỗi khoản học phí lớp học của học sinh. Giáo viên nhận 95%.</li>
          <li><strong>Thời gian giữ tiền 4 ngày:</strong> Để đảm bảo quyền lợi học thử của học sinh (học không quá 5% bài giảng), học phí sẽ được giữ ở trạng thái chờ trong 4 ngày trước khi tự động giải ngân vào số dư khả dụng.</li>
          <li><strong>Hạn mức & Phí rút tiền:</strong> Rút tối thiểu 50.000 VNĐ. Giáo viên chịu các khoản phí chuyển khoản liên ngân hàng / phí SePay phát sinh (nếu có).</li>
        </ul>
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
          onClick={() => setActiveTab('withdrawals')}
          className={`flex items-center gap-2 border-b-2 px-4 py-2.5 text-sm font-semibold transition-colors ${
            activeTab === 'withdrawals'
              ? 'border-primary text-primary'
              : 'border-transparent text-muted-foreground hover:text-foreground'
          }`}
        >
          <Building size={16} /> Yêu cầu rút tiền ({withdrawalsQuery.data?.totalElements ?? 0})
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
              description="Các giao dịch học phí, giải ngân và rút tiền sẽ xuất hiện tại đây."
            />
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-left text-sm">
                <thead>
                  <tr className="border-b border-border/50 text-xs font-semibold uppercase text-muted-foreground">
                    <th className="pb-3 pl-2">Thời gian</th>
                    <th className="pb-3">Loại giao dịch</th>
                    <th className="pb-3">Nội dung</th>
                    <th className="pb-3 text-right">Biến động</th>
                    <th className="pb-3 pr-2 text-right">Số dư khả dụng</th>
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
                          <Pill tone={
                            tx.type.includes('INCOME') ? 'emerald' :
                            tx.type.includes('REFUND') ? 'red' :
                            tx.type.includes('LOCK') ? 'amber' : 'muted'
                          }>
                            {tx.type === 'CLASS_INCOME_PENDING' && 'Học phí tạm giữ'}
                            {tx.type === 'CLASS_INCOME_AVAILABLE' && 'Giải ngân sau 4 ngày'}
                            {tx.type === 'CLASS_REFUND_TEACHER_DEDUCTION' && 'Thu hồi do hoàn tiền'}
                            {tx.type === 'WITHDRAWAL_LOCK' && 'Khóa tiền rút'}
                            {tx.type === 'WITHDRAWAL_COMPLETED' && 'Rút tiền thành công'}
                            {tx.type === 'WITHDRAWAL_CANCELLED' && 'Hoàn tiền rút bị từ chối'}
                            {tx.type === 'SUBSCRIPTION_PAYMENT' && 'Thanh toán gói công cụ'}
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

      {/* Tab Content: Withdrawals */}
      {activeTab === 'withdrawals' && (
        <div className="rounded-2xl border border-border/60 bg-card p-4 shadow-sm">
          {withdrawalsQuery.isLoading ? (
            <CardSkeleton count={3} />
          ) : !withdrawalsQuery.data?.content || withdrawalsQuery.data.content.length === 0 ? (
            <EmptyState
              icon={Building}
              title="Chưa có yêu cầu rút tiền nào"
              description="Khi số dư khả dụng đạt tối thiểu 50.000 VNĐ, bạn có thể tạo yêu cầu rút tiền về tài khoản ngân hàng."
            />
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-left text-sm">
                <thead>
                  <tr className="border-b border-border/50 text-xs font-semibold uppercase text-muted-foreground">
                    <th className="pb-3 pl-2">Mã YC</th>
                    <th className="pb-3">Số tiền</th>
                    <th className="pb-3">Tài khoản nhận</th>
                    <th className="pb-3">Ngày tạo</th>
                    <th className="pb-3">Trạng thái</th>
                    <th className="pb-3 pr-2">Ghi chú</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-border/40">
                  {withdrawalsQuery.data.content.map((w) => (
                    <tr key={w.withdrawalId} className="hover:bg-muted/30">
                      <td className="py-3.5 pl-2 font-mono text-xs font-bold text-muted-foreground">
                        RT{w.withdrawalId}
                      </td>
                      <td className="py-3.5 font-bold text-foreground">
                        {formatMoney(w.amount)}
                      </td>
                      <td className="py-3.5 text-xs">
                        <span className="font-semibold text-foreground">{w.accountHolder}</span>
                        <div className="font-mono text-muted-foreground">{w.bankAccount} ({w.bankName})</div>
                      </td>
                      <td className="py-3.5 text-xs text-muted-foreground">
                        {formatDateTime(w.createdAt)}
                      </td>
                      <td className="py-3.5">
                        <Pill tone={
                          w.status === 'COMPLETED' ? 'emerald' :
                          w.status === 'APPROVED' ? 'blue' :
                          w.status === 'REJECTED' ? 'red' : 'amber'
                        }>
                          {w.status === 'REQUESTED' && 'Chờ admin duyệt'}
                          {w.status === 'APPROVED' && 'Đã duyệt (đang chi)'}
                          {w.status === 'COMPLETED' && 'Đã nhận tiền'}
                          {w.status === 'REJECTED' && 'Bị từ chối'}
                        </Pill>
                      </td>
                      <td className="py-3.5 pr-2 text-xs text-muted-foreground max-w-xs truncate">
                        {w.adminNote || '---'}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}

      {/* Modal Yêu cầu rút tiền */}
      <Dialog open={withdrawOpen} onOpenChange={setWithdrawOpen}>
        <DialogContent className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle>Yêu cầu rút tiền về ngân hàng</DialogTitle>
            <DialogDescription>
              Số dư khả dụng hiện tại: <strong>{wallet ? formatMoney(wallet.balance) : '0 đ'}</strong>.
              Rút tối thiểu 50.000 VNĐ.
            </DialogDescription>
          </DialogHeader>

          <form
            onSubmit={(e) => {
              e.preventDefault()
              withdrawMutation.mutate(withdrawForm)
            }}
            className="space-y-4 py-2"
          >
            <div>
              <label className="text-xs font-semibold text-muted-foreground">Số tiền rút (VNĐ)</label>
              <Input
                type="number"
                min={50000}
                max={wallet?.balance || 50000}
                step={10000}
                value={withdrawForm.amount}
                onChange={(e) => setWithdrawForm({ ...withdrawForm, amount: Number(e.target.value) })}
                className="mt-1 h-11 text-lg font-bold"
                required
              />
            </div>

            <div>
              <label className="text-xs font-semibold text-muted-foreground">Ngân hàng thụ hưởng</label>
              <select
                value={withdrawForm.bankName}
                onChange={(e) => setWithdrawForm({ ...withdrawForm, bankName: e.target.value })}
                className="mt-1 flex h-11 w-full rounded-md border border-input bg-background px-3 py-2 text-sm font-medium"
              >
                {COMMON_BANKS.map((b) => (
                  <option key={b} value={b}>{b}</option>
                ))}
              </select>
            </div>

            <div>
              <label className="text-xs font-semibold text-muted-foreground">Số tài khoản ngân hàng</label>
              <Input
                type="text"
                placeholder="VD: 0123456789"
                value={withdrawForm.bankAccount}
                onChange={(e) => setWithdrawForm({ ...withdrawForm, bankAccount: e.target.value })}
                className="mt-1 h-11 font-mono text-base font-semibold"
                required
              />
            </div>

            <div>
              <label className="text-xs font-semibold text-muted-foreground">Tên chủ tài khoản (Viết in hoa không dấu)</label>
              <Input
                type="text"
                placeholder="VD: NGUYEN VAN A"
                value={withdrawForm.accountHolder}
                onChange={(e) => setWithdrawForm({ ...withdrawForm, accountHolder: e.target.value.toUpperCase() })}
                className="mt-1 h-11 font-semibold uppercase"
                required
              />
            </div>

            <p className="text-xs text-muted-foreground">
              * Lưu ý: Giáo viên chịu chi phí chuyển khoản liên ngân hàng / phí giao dịch SePay (nếu có).
            </p>

            <DialogFooter className="mt-4">
              <Button
                type="submit"
                disabled={withdrawMutation.isPending || (wallet && wallet.balance < withdrawForm.amount)}
                className="w-full rounded-xl"
              >
                {withdrawMutation.isPending ? 'Đang gửi yêu cầu...' : `Xác nhận rút ${formatMoney(withdrawForm.amount)}`}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>

      {/* Modal Mua gói bằng ví */}
      <Dialog open={paySubOpen} onOpenChange={setPaySubOpen}>
        <DialogContent className="sm:max-w-lg">
          <DialogHeader>
            <DialogTitle>Thanh toán gói dịch vụ bằng ví</DialogTitle>
            <DialogDescription>
              Sử dụng số dư khả dụng (<strong>{wallet ? formatMoney(wallet.balance) : '0 đ'}</strong>) để gia hạn các gói công cụ AI & Video.
            </DialogDescription>
          </DialogHeader>

          <div className="space-y-3 py-2">
            {plansQuery.data?.map((plan) => {
              const canAfford = wallet && wallet.balance >= plan.price
              return (
                <div
                  key={plan.planId}
                  className="flex items-center justify-between rounded-xl border border-border/70 p-4 transition-colors hover:border-primary/40"
                >
                  <div>
                    <h4 className="font-bold text-foreground">{plan.name}</h4>
                    <p className="text-xs text-muted-foreground">{plan.description}</p>
                    <p className="mt-1 text-sm font-bold text-primary">{formatMoney(plan.price)}</p>
                  </div>
                  <Button
                    size="sm"
                    disabled={!canAfford || paySubMutation.isPending}
                    onClick={() => paySubMutation.mutate(plan.planId)}
                    className="rounded-lg"
                  >
                    {canAfford ? 'Thanh toán ngay' : 'Không đủ số dư'}
                  </Button>
                </div>
              )
            })}
          </div>
        </DialogContent>
      </Dialog>
    </div>
  )
}
