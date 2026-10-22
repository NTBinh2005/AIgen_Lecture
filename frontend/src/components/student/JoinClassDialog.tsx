import { useState } from 'react'
import type { FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  CheckCircle2, KeyRound, Loader2, Plus, Wallet as WalletIcon, QrCode,
  Check, Copy
} from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import {
  Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle, DialogTrigger,
} from '@/components/ui/dialog'
import { lookupClassByCode, selfEnroll } from '@/api/classApi'
import { purchaseClass, type ClassOrder } from '@/api/classOrderApi'
import { getMyWallet } from '@/api/walletApi'
import { getErrorMessage } from '@/lib/format'
import { formatMoney } from '@/lib/download'
import type { ClassDetail } from '@/types/student'

interface JoinClassDialogProps {
  size?: 'default' | 'lg'
}

export function JoinClassDialog({ size = 'default' }: JoinClassDialogProps) {
  const [open, setOpen] = useState(false)
  const [code, setCode] = useState('')
  const [classInfo, setClassInfo] = useState<ClassDetail | null>(null)
  const [payResult, setPayResult] = useState<ClassOrder | null>(null)
  const [copied, setCopied] = useState<string | null>(null)
  const queryClient = useQueryClient()

  // Số dư ví của học sinh
  const walletQuery = useQuery({
    queryKey: ['myWallet'],
    queryFn: getMyWallet,
    enabled: open,
  })

  // Tra cứu lớp
  const lookupMutation = useMutation({
    mutationFn: (classCode: string) => lookupClassByCode(classCode),
    onSuccess: (data) => {
      setClassInfo(data)
      // Nếu miễn phí, tự động enroll luôn
      if (!data.price || data.price <= 0) {
        enrollMutation.mutate(data.classCode)
      }
    },
  })

  // Enroll miễn phí
  const enrollMutation = useMutation({
    mutationFn: (classCode: string) => selfEnroll(classCode),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['student'] })
    },
  })

  // Mua lớp bằng Ví hoặc SePay QR
  const purchaseMutation = useMutation({
    mutationFn: ({ classId, method }: { classId: number; method: 'WALLET' | 'SEPAY_QR' }) =>
      purchaseClass(classId, method),
    onSuccess: (data) => {
      setPayResult(data)
      queryClient.invalidateQueries({ queryKey: ['student'] })
      queryClient.invalidateQueries({ queryKey: ['myWallet'] })
      queryClient.invalidateQueries({ queryKey: ['myClassOrders'] })
    },
  })

  const handleCopy = (text: string, field: string) => {
    navigator.clipboard.writeText(text)
    setCopied(field)
    setTimeout(() => setCopied(null), 2000)
  }

  const handleSubmit = (e: FormEvent) => {
    e.preventDefault()
    const trimmed = code.trim().toUpperCase()
    if (trimmed) lookupMutation.mutate(trimmed)
  }

  const handleOpenChange = (next: boolean) => {
    setOpen(next)
    if (!next) {
      setCode('')
      setClassInfo(null)
      setPayResult(null)
      lookupMutation.reset()
      enrollMutation.reset()
      purchaseMutation.reset()
    }
  }

  const wallet = walletQuery.data
  const isEnrolledSuccess = enrollMutation.isSuccess || (purchaseMutation.isSuccess && payResult?.paymentMethod === 'WALLET')

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogTrigger asChild>
        <Button className={size === 'lg' ? 'h-11 rounded-xl px-5' : 'h-10 rounded-xl px-4'}>
          <Plus size={16} aria-hidden="true" />
          Tham gia lớp
        </Button>
      </DialogTrigger>
      <DialogContent className="sm:max-w-md">
        {/* 1. Đã tham gia thành công */}
        {isEnrolledSuccess ? (
          <div className="space-y-4 py-2 text-center">
            <CheckCircle2 className="mx-auto h-12 w-12 text-emerald-500" aria-hidden="true" />
            <div>
              <DialogTitle className="text-lg">Tham gia lớp học thành công!</DialogTitle>
              <DialogDescription className="mt-1">
                Bạn đã được ghi danh vào lớp{' '}
                <span className="font-semibold text-foreground">
                  {classInfo?.className || enrollMutation.data?.className}
                </span>.
              </DialogDescription>
            </div>
            <Button className="h-10 w-full rounded-xl" onClick={() => handleOpenChange(false)}>
              Bắt đầu học ngay
            </Button>
          </div>
        ) : payResult?.paymentMethod === 'SEPAY_QR' ? (
          /* 2. Hiển thị mã QR SePay khi chọn thanh toán SePay QR */
          <div className="space-y-4 text-center">
            <DialogHeader>
              <DialogTitle>Thanh toán học phí qua SePay QR</DialogTitle>
              <DialogDescription>
                Quét mã VietQR bằng ứng dụng ngân hàng bất kỳ để tham gia lớp ngay lập tức.
              </DialogDescription>
            </DialogHeader>

            {payResult.qrUrl && (
              <div className="mx-auto flex w-full max-w-[240px] flex-col items-center justify-center rounded-2xl border border-border/80 bg-white p-3 shadow-inner">
                <img src={payResult.qrUrl} alt="VietQR SePay" className="h-auto w-full object-contain" />
              </div>
            )}

            <div className="space-y-2 rounded-xl bg-muted/50 p-3 text-left text-xs">
              <div className="flex items-center justify-between">
                <span className="text-muted-foreground">Khóa học:</span>
                <span className="font-bold text-foreground">{payResult.className}</span>
              </div>
              <div className="flex items-center justify-between">
                <span className="text-muted-foreground">Học phí:</span>
                <span className="font-bold text-emerald-600 dark:text-emerald-400">{formatMoney(payResult.price)}</span>
              </div>
              <div className="flex items-center justify-between border-t border-border/60 pt-2">
                <span className="text-muted-foreground">Nội dung chuyển khoản:</span>
                <button
                  onClick={() => handleCopy(payResult.orderCode, 'code')}
                  className="flex items-center gap-1 rounded bg-amber-500/10 px-1.5 py-0.5 font-mono font-bold text-amber-600 dark:text-amber-400"
                >
                  {payResult.orderCode}
                  {copied === 'code' ? <Check size={13} className="text-emerald-500" /> : <Copy size={13} />}
                </button>
              </div>
            </div>

            <p className="text-xs text-muted-foreground">
              Hệ thống sẽ tự động thêm bạn vào lớp ngay khi nhận được thanh toán.
            </p>

            <Button
              variant="outline"
              onClick={() => handleOpenChange(false)}
              className="w-full rounded-xl"
            >
              Đóng cửa sổ
            </Button>
          </div>
        ) : classInfo && classInfo.price && classInfo.price > 0 ? (
          /* 3. Lớp có học phí -> Lựa chọn thanh toán bằng Ví hoặc SePay QR */
          <div className="space-y-4 py-1">
            <DialogHeader>
              <DialogTitle>Thanh toán học phí tham gia lớp</DialogTitle>
              <DialogDescription>
                Lớp <strong className="text-foreground">{classInfo.className}</strong> yêu cầu thanh toán học phí.
              </DialogDescription>
            </DialogHeader>

            <div className="rounded-xl border border-primary/20 bg-primary/5 p-4 text-center">
              <span className="text-xs font-semibold uppercase tracking-wider text-muted-foreground">Học phí</span>
              <div className="text-2xl font-black text-primary">{formatMoney(classInfo.price)}</div>
              <div className="mt-1 text-xs text-muted-foreground">Giảng viên: {classInfo.teacherName}</div>
            </div>

            <div className="space-y-3">
              {/* Nút thanh toán bằng Ví */}
              <div className="rounded-xl border border-border/70 p-3.5 transition-colors hover:border-primary/50">
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-2.5">
                    <div className="flex h-9 w-9 items-center justify-center rounded-lg bg-emerald-500/10 text-emerald-600">
                      <WalletIcon size={18} />
                    </div>
                    <div>
                      <h4 className="text-sm font-bold text-foreground">Thanh toán bằng Ví cá nhân</h4>
                      <p className="text-xs text-muted-foreground">
                        Số dư hiện có: <strong>{wallet ? formatMoney(wallet.balance) : '0 đ'}</strong>
                      </p>
                    </div>
                  </div>
                </div>

                <Button
                  className="mt-3 w-full rounded-xl font-semibold"
                  disabled={!wallet || wallet.balance < classInfo.price || purchaseMutation.isPending}
                  onClick={() => purchaseMutation.mutate({ classId: classInfo.classId, method: 'WALLET' })}
                >
                  {purchaseMutation.isPending ? (
                    <Loader2 size={15} className="animate-spin" />
                  ) : wallet && wallet.balance >= classInfo.price ? (
                    'Thanh toán ngay bằng Ví'
                  ) : (
                    'Số dư ví không đủ (Vui lòng nạp thêm)'
                  )}
                </Button>
              </div>

              {/* Nút thanh toán bằng VietQR SePay */}
              <div className="rounded-xl border border-border/70 p-3.5 transition-colors hover:border-primary/50">
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-2.5">
                    <div className="flex h-9 w-9 items-center justify-center rounded-lg bg-blue-500/10 text-blue-600">
                      <QrCode size={18} />
                    </div>
                    <div>
                      <h4 className="text-sm font-bold text-foreground">Quét mã VietQR SePay</h4>
                      <p className="text-xs text-muted-foreground">Chuyển khoản trực tiếp qua App ngân hàng</p>
                    </div>
                  </div>
                </div>

                <Button
                  variant="outline"
                  className="mt-3 w-full rounded-xl font-semibold"
                  disabled={purchaseMutation.isPending}
                  onClick={() => purchaseMutation.mutate({ classId: classInfo.classId, method: 'SEPAY_QR' })}
                >
                  {purchaseMutation.isPending ? <Loader2 size={15} className="animate-spin" /> : 'Tạo mã VietQR chuyển khoản'}
                </Button>
              </div>
            </div>

            <p className="text-[11px] text-muted-foreground text-center">
              * Chính sách: Hoàn 100% học phí về ví trong 4 ngày nếu học không quá 5% bài giảng.
            </p>
          </div>
        ) : (
          /* 4. Form nhập mã lớp ban đầu */
          <form onSubmit={handleSubmit} className="space-y-4">
            <DialogHeader>
              <DialogTitle>Tham gia lớp học</DialogTitle>
              <DialogDescription>Nhập mã lớp giáo viên đã gửi cho bạn. Lớp phải đang hoạt động.</DialogDescription>
            </DialogHeader>
            <div className="relative">
              <KeyRound className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" aria-hidden="true" />
              <Input
                autoFocus
                value={code}
                onChange={e => setCode(e.target.value.toUpperCase())}
                placeholder="VD: FE10476"
                maxLength={20}
                aria-label="Mã lớp"
                className="h-11 pl-9 font-mono tracking-wider"
              />
            </div>
            {(lookupMutation.isError || enrollMutation.isError) && (
              <p className="text-sm text-destructive" role="alert">
                {getErrorMessage(lookupMutation.error || enrollMutation.error, 'Không tìm thấy lớp học với mã này.')}
              </p>
            )}
            <DialogFooter>
              <Button
                type="submit"
                className="h-10 w-full rounded-xl sm:w-auto"
                disabled={!code.trim() || lookupMutation.isPending || enrollMutation.isPending}
              >
                {(lookupMutation.isPending || enrollMutation.isPending) && (
                  <Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" />
                )}
                Tiếp tục
              </Button>
            </DialogFooter>
          </form>
        )}
      </DialogContent>
    </Dialog>
  )
}
