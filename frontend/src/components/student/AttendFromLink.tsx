import { useSearchParams } from 'react-router-dom'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { CheckCircle2, Loader2, XCircle } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogTitle } from '@/components/ui/dialog'
import { scanAttendanceQr } from '@/api/liveApi'
import { normalizeAttendanceCode } from '@/lib/attendance'
import { getErrorMessage } from '@/lib/format'

/**
 * Học sinh quét mã QR giáo viên chiếu → mở /student/schedule?attend=<id>&code=<mã> → tự điểm danh.
 * Dùng useQuery (khoá theo buổi + mã, không thử lại, không tự gọi lại) để mỗi link chỉ gửi đúng một request,
 * kể cả khi component được gắn lại (StrictMode, đổi trang qua lại).
 */
export function AttendFromLink() {
  const [params, setParams] = useSearchParams()
  const queryClient = useQueryClient()
  const sessionId = Number(params.get('attend'))
  const code = normalizeAttendanceCode(params.get('code') ?? '')
  const active = Number.isInteger(sessionId) && sessionId > 0 && code.length > 0

  const scan = useQuery({
    queryKey: ['attend-link', sessionId, code],
    queryFn: async () => { await scanAttendanceQr(sessionId, code); return true },
    enabled: active,
    retry: false,
    staleTime: Infinity,
    gcTime: 10 * 60_000,
    refetchOnMount: false,
    refetchOnWindowFocus: false,
    refetchOnReconnect: false,
  })

  const close = () => {
    if (scan.isSuccess) queryClient.invalidateQueries({ queryKey: ['student'] })
    const next = new URLSearchParams(params)
    next.delete('attend')
    next.delete('code')
    setParams(next, { replace: true })
  }

  return (
    <Dialog open={active} onOpenChange={open => { if (!open) close() }}>
      <DialogContent className="sm:max-w-sm">
        <div className="space-y-3 py-2 text-center">
          {scan.isSuccess ? (
            <>
              <CheckCircle2 className="mx-auto h-12 w-12 text-emerald-500" aria-hidden="true" />
              <DialogTitle>Điểm danh thành công</DialogTitle>
              <DialogDescription>Bạn đã được ghi nhận có mặt trong buổi học.</DialogDescription>
            </>
          ) : scan.isError ? (
            <>
              <XCircle className="mx-auto h-12 w-12 text-destructive" aria-hidden="true" />
              <DialogTitle>Chưa điểm danh được</DialogTitle>
              <DialogDescription>{getErrorMessage(scan.error, 'Mã không hợp lệ hoặc đã hết hạn — nhờ giáo viên tạo mã mới.')}</DialogDescription>
            </>
          ) : (
            <>
              <Loader2 className="mx-auto h-10 w-10 animate-spin text-primary" aria-hidden="true" />
              <DialogTitle>Đang điểm danh…</DialogTitle>
              <DialogDescription>Đang gửi mã điểm danh từ mã QR.</DialogDescription>
            </>
          )}
          <Button className="h-10 w-full rounded-xl" disabled={scan.isFetching} onClick={close}>Đóng</Button>
        </div>
      </DialogContent>
    </Dialog>
  )
}
