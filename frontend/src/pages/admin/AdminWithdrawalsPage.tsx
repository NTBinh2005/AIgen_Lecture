import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import {
  Building, XCircle, QrCode, RefreshCw, Check, Copy
} from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import {
  Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle,
} from '@/components/ui/dialog'
import { PageHeader, CardSkeleton, EmptyState, Pill } from '@/components/student/StudentUi'
import { getAllWithdrawals, reviewWithdrawal, type WithdrawalRequestItem } from '@/api/withdrawalApi'
import { formatMoney } from '@/lib/download'
import { formatDateTime, getErrorMessage } from '@/lib/format'
import { notify, confirmDialog } from '@/lib/notify'

export default function AdminWithdrawalsPage() {
  const queryClient = useQueryClient()
  const [statusFilter, setStatusFilter] = useState<string>('')
  const [selectedItem, setSelectedItem] = useState<WithdrawalRequestItem | null>(null)
  const [actionType, setActionType] = useState<'APPROVE_PAY' | 'REJECT' | null>(null)
  const [rejectReason, setRejectReason] = useState('')
  const [copied, setCopied] = useState<string | null>(null)

  const withdrawalsQuery = useQuery({
    queryKey: ['adminWithdrawals', statusFilter],
    queryFn: () => getAllWithdrawals(statusFilter || undefined, 0, 50),
    refetchInterval: 10000,
  })

  const reviewMutation = useMutation({
    mutationFn: ({ id, status, adminNote }: { id: number; status: 'APPROVED' | 'REJECTED' | 'COMPLETED'; adminNote?: string }) =>
      reviewWithdrawal(id, { status, adminNote }),
    onSuccess: (data) => {
      notify.success('Cập nhật trạng thái yêu cầu rút tiền thành công!')
      setSelectedItem(data)
      queryClient.invalidateQueries({ queryKey: ['adminWithdrawals'] })
    },
    onError: (err) => {
      notify.error(getErrorMessage(err, 'Lỗi khi cập nhật yêu cầu rút tiền'))
    },
  })

  const handleCopy = (text: string, field: string) => {
    navigator.clipboard.writeText(text)
    setCopied(field)
    setTimeout(() => setCopied(null), 2000)
  }

  const handleOpenPay = (item: WithdrawalRequestItem) => {
    setSelectedItem(item)
    setActionType('APPROVE_PAY')
    if (item.status === 'REQUESTED') {
      reviewMutation.mutate({ id: item.withdrawalId, status: 'APPROVED' })
    }
  }

  const handleOpenReject = (item: WithdrawalRequestItem) => {
    setSelectedItem(item)
    setActionType('REJECT')
    setRejectReason('')
  }

  const handleConfirmReject = () => {
    if (!selectedItem) return
    reviewMutation.mutate({
      id: selectedItem.withdrawalId,
      status: 'REJECTED',
      adminNote: rejectReason || 'Không hợp lệ theo quy định',
    })
    setActionType(null)
  }

  const handleConfirmManualComplete = async (item: WithdrawalRequestItem) => {
    const ok = await confirmDialog({
      title: 'Xác nhận đã chuyển tiền thủ công',
      description: `Bạn xác nhận đã chuyển khoản ${formatMoney(item.amount)} tới giáo viên ${item.accountHolder} (${item.bankAccount}) ngoài hệ thống SePay?`,
      confirmText: 'Đã chuyển tiền',
    })
    if (ok) {
      reviewMutation.mutate({
        id: item.withdrawalId,
        status: 'COMPLETED',
        adminNote: 'Admin xác nhận đã chuyển tiền thành công thủ công',
      })
      setActionType(null)
    }
  }

  return (
    <div className="mx-auto max-w-6xl space-y-6">
      <PageHeader
        title="Duyệt yêu cầu rút tiền giáo viên"
        description="Quản lý và giải ngân học phí cho giáo viên. Quét mã VietQR SePay để chi tiền trực tiếp."
        action={
          <div className="flex items-center gap-2">
            <select
              value={statusFilter}
              onChange={(e) => setStatusFilter(e.target.value)}
              className="h-10 rounded-xl border border-input bg-background px-3 text-sm font-medium"
            >
              <option value="">Tất cả trạng thái</option>
              <option value="REQUESTED">Chờ duyệt</option>
              <option value="APPROVED">Đã duyệt (chờ chi)</option>
              <option value="COMPLETED">Đã chi tiền</option>
              <option value="REJECTED">Đã từ chối</option>
            </select>
            <Button
              variant="outline"
              size="sm"
              onClick={() => queryClient.invalidateQueries({ queryKey: ['adminWithdrawals'] })}
              className="h-10 w-10 p-0 rounded-xl"
            >
              <RefreshCw size={14} className={withdrawalsQuery.isFetching ? 'animate-spin' : ''} />
            </Button>
          </div>
        }
      />

      {/* Danh sách yêu cầu */}
      <div className="rounded-2xl border border-border/60 bg-card p-4 shadow-sm">
        {withdrawalsQuery.isLoading ? (
          <CardSkeleton count={4} />
        ) : !withdrawalsQuery.data?.content || withdrawalsQuery.data.content.length === 0 ? (
          <EmptyState
            icon={Building}
            title="Không có yêu cầu rút tiền nào"
            description="Các yêu cầu rút tiền từ giáo viên sẽ hiển thị tại đây để bạn xem xét và chi trả."
          />
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm">
              <thead>
                <tr className="border-b border-border/50 text-xs font-semibold uppercase text-muted-foreground">
                  <th className="pb-3 pl-2">Mã GD</th>
                  <th className="pb-3">Giáo viên</th>
                  <th className="pb-3">Số tiền</th>
                  <th className="pb-3">Tài khoản nhận</th>
                  <th className="pb-3">Thời gian</th>
                  <th className="pb-3">Trạng thái</th>
                  <th className="pb-3 pr-2 text-right">Thao tác</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-border/40">
                {withdrawalsQuery.data.content.map((item) => (
                  <tr key={item.withdrawalId} className="hover:bg-muted/30">
                    <td className="py-3.5 pl-2 font-mono text-xs font-bold text-primary">
                      RT{item.withdrawalId}
                    </td>
                    <td className="py-3.5">
                      <div className="font-semibold text-foreground">{item.teacherName}</div>
                      <div className="text-xs text-muted-foreground">{item.teacherEmail}</div>
                    </td>
                    <td className="py-3.5 font-bold text-foreground">
                      {formatMoney(item.amount)}
                    </td>
                    <td className="py-3.5 text-xs">
                      <div className="font-bold text-foreground">{item.accountHolder}</div>
                      <div className="font-mono text-muted-foreground">{item.bankAccount} ({item.bankName})</div>
                    </td>
                    <td className="py-3.5 text-xs text-muted-foreground">
                      {formatDateTime(item.createdAt)}
                    </td>
                    <td className="py-3.5">
                      <Pill tone={
                        item.status === 'COMPLETED' ? 'emerald' :
                        item.status === 'APPROVED' ? 'blue' :
                        item.status === 'REJECTED' ? 'red' : 'amber'
                      }>
                        {item.status === 'REQUESTED' && 'Chờ duyệt'}
                        {item.status === 'APPROVED' && 'Đang chi tiền'}
                        {item.status === 'COMPLETED' && 'Đã hoàn tất'}
                        {item.status === 'REJECTED' && 'Đã từ chối'}
                      </Pill>
                    </td>
                    <td className="py-3.5 pr-2 text-right">
                      {item.status === 'REQUESTED' && (
                        <div className="flex items-center justify-end gap-1.5">
                          <Button
                            size="sm"
                            onClick={() => handleOpenPay(item)}
                            className="gap-1 rounded-lg text-xs"
                          >
                            <QrCode size={13} /> Duyệt & Chi
                          </Button>
                          <Button
                            variant="ghost"
                            size="sm"
                            onClick={() => handleOpenReject(item)}
                            className="rounded-lg text-xs text-destructive hover:bg-destructive/10"
                          >
                            <XCircle size={13} /> Từ chối
                          </Button>
                        </div>
                      )}
                      {item.status === 'APPROVED' && (
                        <div className="flex items-center justify-end gap-1.5">
                          <Button
                            size="sm"
                            variant="outline"
                            onClick={() => handleOpenPay(item)}
                            className="gap-1 rounded-lg text-xs"
                          >
                            <QrCode size={13} /> Mã QR Chi tiền
                          </Button>
                          <Button
                            size="sm"
                            onClick={() => handleConfirmManualComplete(item)}
                            className="rounded-lg text-xs"
                          >
                            Xác nhận đã chi
                          </Button>
                        </div>
                      )}
                      {item.status === 'COMPLETED' && (
                        <span className="text-xs text-emerald-600 dark:text-emerald-400 font-medium">
                          ✓ Đã thanh toán
                        </span>
                      )}
                      {item.status === 'REJECTED' && (
                        <span className="text-xs text-muted-foreground italic">
                          {item.adminNote || 'Đã từ chối'}
                        </span>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* Modal Chi tiền qua VietQR SePay */}
      <Dialog
        open={actionType === 'APPROVE_PAY' && !!selectedItem}
        onOpenChange={(open) => {
          if (!open) {
            setActionType(null)
            setSelectedItem(null)
          }
        }}
      >
        <DialogContent className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle>Chi tiền cho giáo viên qua VietQR</DialogTitle>
            <DialogDescription>
              Quét mã QR bằng App ngân hàng của hệ thống để chuyển tiền. Webhook SePay sẽ tự động đánh dấu hoàn tất ngay khi tiền ra.
            </DialogDescription>
          </DialogHeader>

          {selectedItem && (
            <div className="space-y-4 text-center">
              {selectedItem.qrUrl ? (
                <div className="mx-auto flex w-full max-w-[260px] flex-col items-center justify-center rounded-2xl border border-border/80 bg-white p-3 shadow-inner">
                  <img
                    src={selectedItem.qrUrl}
                    alt="VietQR Payout"
                    className="h-auto w-full object-contain"
                  />
                </div>
              ) : (
                <div className="rounded-xl border border-amber-500/20 bg-amber-500/5 p-4 text-xs text-amber-600">
                  Chưa thể tạo mã VietQR tự động. Vui lòng chuyển khoản thủ công theo thông tin bên dưới.
                </div>
              )}

              <div className="space-y-2 rounded-xl bg-muted/50 p-3 text-left text-xs">
                <div className="flex items-center justify-between">
                  <span className="text-muted-foreground">Người nhận:</span>
                  <span className="font-bold text-foreground">{selectedItem.accountHolder}</span>
                </div>
                <div className="flex items-center justify-between">
                  <span className="text-muted-foreground">Ngân hàng:</span>
                  <span className="font-bold text-foreground">{selectedItem.bankName}</span>
                </div>
                <div className="flex items-center justify-between">
                  <span className="text-muted-foreground">Số tài khoản:</span>
                  <button
                    onClick={() => handleCopy(selectedItem.bankAccount, 'acc')}
                    className="flex items-center gap-1 font-mono font-bold text-foreground hover:text-primary"
                  >
                    {selectedItem.bankAccount}
                    {copied === 'acc' ? <Check size={13} className="text-emerald-500" /> : <Copy size={13} />}
                  </button>
                </div>
                <div className="flex items-center justify-between">
                  <span className="text-muted-foreground">Số tiền chi:</span>
                  <span className="font-bold text-emerald-600 dark:text-emerald-400">{formatMoney(selectedItem.amount)}</span>
                </div>
                <div className="flex items-center justify-between border-t border-border/60 pt-2">
                  <span className="text-muted-foreground">Nội dung chuyển khoản:</span>
                  <button
                    onClick={() => handleCopy(`RT${selectedItem.withdrawalId}`, 'des')}
                    className="flex items-center gap-1 rounded bg-amber-500/10 px-1.5 py-0.5 font-mono font-bold text-amber-600 dark:text-amber-400"
                  >
                    RT{selectedItem.withdrawalId}
                    {copied === 'des' ? <Check size={13} className="text-emerald-500" /> : <Copy size={13} />}
                  </button>
                </div>
              </div>

              <DialogFooter className="mt-4 flex flex-col gap-2 sm:flex-row">
                <Button
                  variant="outline"
                  onClick={() => {
                    setActionType(null)
                    setSelectedItem(null)
                  }}
                  className="w-full rounded-xl sm:w-auto"
                >
                  Đóng
                </Button>
                <Button
                  onClick={() => handleConfirmManualComplete(selectedItem)}
                  className="w-full rounded-xl sm:w-auto"
                >
                  Xác nhận đã chi tiền thủ công
                </Button>
              </DialogFooter>
            </div>
          )}
        </DialogContent>
      </Dialog>

      {/* Modal Từ chối yêu cầu */}
      <Dialog
        open={actionType === 'REJECT' && !!selectedItem}
        onOpenChange={(open) => {
          if (!open) {
            setActionType(null)
            setSelectedItem(null)
          }
        }}
      >
        <DialogContent className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle>Từ chối yêu cầu rút tiền RT{selectedItem?.withdrawalId}</DialogTitle>
            <DialogDescription>
              Số tiền {selectedItem ? formatMoney(selectedItem.amount) : ''} đang bị khóa sẽ được mở khóa và hoàn trả lại số dư ví của giáo viên {selectedItem?.teacherName}.
            </DialogDescription>
          </DialogHeader>

          <div className="space-y-3 py-2">
            <label className="text-xs font-semibold text-muted-foreground">Lý do từ chối</label>
            <Input
              type="text"
              placeholder="VD: Sai thông tin số tài khoản / Tên chủ tài khoản không khớp"
              value={rejectReason}
              onChange={(e) => setRejectReason(e.target.value)}
              className="h-11"
              autoFocus
            />
          </div>

          <DialogFooter className="mt-4">
            <Button
              variant="outline"
              onClick={() => {
                setActionType(null)
                setSelectedItem(null)
              }}
              className="rounded-xl"
            >
              Hủy
            </Button>
            <Button
              variant="destructive"
              onClick={handleConfirmReject}
              disabled={reviewMutation.isPending}
              className="rounded-xl"
            >
              {reviewMutation.isPending ? 'Đang xử lý...' : 'Xác nhận từ chối'}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}
