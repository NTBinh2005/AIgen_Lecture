import { useState } from 'react'
import type { FormEvent } from 'react'
import { useMutation } from '@tanstack/react-query'
import { CheckCircle2, ExternalLink, Loader2, MapPin, QrCode, Video } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import {
  Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle, DialogTrigger,
} from '@/components/ui/dialog'
import { LiveStatusPill } from './StudentUi'
import { joinLiveSession, scanAttendanceQr } from '@/api/liveApi'
import { formatTime, formatWeekday, getErrorMessage } from '@/lib/format'
import { cn } from '@/lib/utils'
import type { LiveSession } from '@/types/student'

interface LiveSessionCardProps {
  session: LiveSession
  showClass?: boolean
  compact?: boolean
}

export function LiveSessionCard({ session: s, showClass = true, compact = false }: LiveSessionCardProps) {
  const joinable = s.status === 'OPEN' || s.status === 'LIVE'
  const inactive = s.status === 'ENDED' || s.status === 'CANCELLED'

  const join = useMutation({
    mutationFn: () => joinLiveSession(s.sessionId),
    onSuccess: data => {
      const url = data.meetingUrl ?? s.meetingUrl
      if (url) window.open(url, '_blank', 'noopener,noreferrer')
    },
  })

  return (
    <div className={cn(
      'flex flex-col gap-4 rounded-2xl border bg-card p-4 dark:bg-card/70 sm:flex-row sm:items-center',
      s.status === 'LIVE' ? 'border-red-500/40 shadow-lg shadow-red-500/5' : 'border-border/50',
      inactive && 'opacity-70',
    )}>
      {/* Ngày giờ */}
      <div className="flex shrink-0 items-center gap-3 sm:w-40 sm:flex-col sm:items-start sm:gap-0">
        <p className="text-sm font-semibold capitalize text-foreground">{formatWeekday(s.startsAt)}</p>
        <p className="text-sm text-muted-foreground">{formatTime(s.startsAt)} – {formatTime(s.endsAt)}</p>
      </div>

      {/* Nội dung */}
      <div className="min-w-0 flex-1">
        <div className="flex flex-wrap items-center gap-2">
          <h3 className="truncate font-semibold text-foreground">{s.title}</h3>
          <LiveStatusPill status={s.status} />
        </div>
        <p className="mt-1 flex flex-wrap items-center gap-x-3 gap-y-1 text-sm text-muted-foreground">
          {showClass && <span>{s.className}</span>}
          <span className="flex items-center gap-1">
            {s.type === 'ONLINE' ? <Video size={14} aria-hidden="true" /> : <MapPin size={14} aria-hidden="true" />}
            {s.type === 'ONLINE' ? 'Trực tuyến' : s.location || 'Tại lớp'}
          </span>
          {!compact && <span>GV: {s.createdByName}</span>}
        </p>
        {s.status === 'CANCELLED' && s.cancelReason && (
          <p className="mt-1 text-sm text-destructive">Lý do hủy: {s.cancelReason}</p>
        )}
        {join.isError && (
          <p className="mt-1 text-sm text-destructive" role="alert">
            {getErrorMessage(join.error, 'Không vào được phòng học.')}
          </p>
        )}
      </div>

      {/* Hành động */}
      {!inactive && (
        <div className="flex shrink-0 gap-2">
          {s.type === 'ONLINE' && (
            <Button
              className="h-10 flex-1 rounded-xl sm:flex-none"
              disabled={!joinable || join.isPending}
              onClick={() => join.mutate()}
              title={joinable ? undefined : 'Phòng học chưa mở'}
            >
              {join.isPending ? <Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" /> : <ExternalLink size={15} aria-hidden="true" />}
              Vào lớp
            </Button>
          )}
          {joinable && <AttendanceDialog sessionId={s.sessionId} />}
        </div>
      )}
    </div>
  )
}

/** LIVE-06: Điểm danh bằng mã QR giáo viên chiếu trên màn hình. */
function AttendanceDialog({ sessionId }: { sessionId: number }) {
  const [open, setOpen] = useState(false)
  const [code, setCode] = useState('')
  const scan = useMutation({ mutationFn: (c: string) => scanAttendanceQr(sessionId, c) })

  const handleSubmit = (e: FormEvent) => {
    e.preventDefault()
    if (code.trim()) scan.mutate(code.trim())
  }

  return (
    <Dialog open={open} onOpenChange={next => { setOpen(next); if (!next) { setCode(''); scan.reset() } }}>
      <DialogTrigger asChild>
        <Button variant="outline" className="h-10 rounded-xl">
          <QrCode size={15} aria-hidden="true" />
          Điểm danh
        </Button>
      </DialogTrigger>
      <DialogContent className="sm:max-w-md">
        {scan.isSuccess ? (
          <div className="space-y-3 py-2 text-center">
            <CheckCircle2 className="mx-auto h-12 w-12 text-emerald-500" aria-hidden="true" />
            <DialogTitle>Điểm danh thành công</DialogTitle>
            <Button className="h-10 w-full rounded-xl" onClick={() => setOpen(false)}>Đóng</Button>
          </div>
        ) : (
          <form onSubmit={handleSubmit} className="space-y-4">
            <DialogHeader>
              <DialogTitle>Điểm danh</DialogTitle>
              <DialogDescription>Nhập mã dưới mã QR giáo viên đang chiếu. Mã chỉ có hiệu lực trong vài phút.</DialogDescription>
            </DialogHeader>
            <Input
              autoFocus
              value={code}
              onChange={e => setCode(e.target.value)}
              placeholder="Mã điểm danh"
              aria-label="Mã điểm danh"
              className="h-11 font-mono"
            />
            {scan.isError && (
              <p className="text-sm text-destructive" role="alert">
                {getErrorMessage(scan.error, 'Mã không hợp lệ hoặc đã hết hạn.')}
              </p>
            )}
            <DialogFooter>
              <Button type="submit" className="h-10 w-full rounded-xl sm:w-auto" disabled={!code.trim() || scan.isPending}>
                {scan.isPending && <Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" />}
                Xác nhận
              </Button>
            </DialogFooter>
          </form>
        )}
      </DialogContent>
    </Dialog>
  )
}
