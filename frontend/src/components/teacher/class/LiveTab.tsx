import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  Ban, CheckCircle2, ClipboardCheck, DoorOpen, ExternalLink, Loader2, MapPin, Plus, Power, QrCode, Radio, Video,
} from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import {
  Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle, DialogTrigger,
} from '@/components/ui/dialog'
import { Field, FormError, Select, SubmitButton } from '../FormKit'
import { EmptyState, LiveStatusPill } from '@/components/student/StudentUi'
import {
  cancelLiveSession, createLiveSession, generateQr, getAttendance, markAttendance, transitionLiveSession,
} from '@/api/teacherLiveApi'
import type { LiveTransition } from '@/api/teacherLiveApi'
import { teacherKeys } from '@/hooks/useTeacherData'
import { formatTime, formatWeekday, fromDateTimeInput, getErrorMessage, parseDate } from '@/lib/format'
import { cn } from '@/lib/utils'
import type { Enrollment, LiveSession, LiveSessionType } from '@/types/student'
import type { QrToken } from '@/types/teacher'

const NEXT_ACTION: Partial<Record<LiveSession['status'], { action: LiveTransition; label: string; icon: typeof Power }>> = {
  SCHEDULED: { action: 'open', label: 'Mở phòng', icon: DoorOpen },
  OPEN: { action: 'live', label: 'Bắt đầu', icon: Radio },
  LIVE: { action: 'end', label: 'Kết thúc', icon: Power },
}

interface LiveTabProps {
  classId: number
  sessions: LiveSession[]
  students: Enrollment[]
}

export function LiveTab({ classId, sessions, students }: LiveTabProps) {
  const queryClient = useQueryClient()
  const [qrSession, setQrSession] = useState<LiveSession | null>(null)
  const [attendanceSession, setAttendanceSession] = useState<LiveSession | null>(null)
  const refresh = () => queryClient.invalidateQueries({ queryKey: teacherKeys.live(classId) })

  const transition = useMutation({
    mutationFn: ({ id, action }: { id: number; action: LiveTransition }) => transitionLiveSession(id, action),
    onSuccess: refresh,
  })
  const cancel = useMutation({
    mutationFn: ({ id, reason }: { id: number; reason: string }) => cancelLiveSession(id, reason),
    onSuccess: refresh,
  })

  const handleCancel = (s: LiveSession) => {
    const reason = window.prompt(`Lý do hủy "${s.title}"?`)
    if (reason !== null) cancel.mutate({ id: s.sessionId, reason: reason.trim() || 'Giáo viên hủy' })
  }

  const sorted = [...sessions].sort((a, b) => a.startsAt.localeCompare(b.startsAt))
  const error = transition.error ?? cancel.error

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between gap-3">
        <p className="text-sm text-muted-foreground">Mở phòng trước giờ học để học sinh vào lớp và điểm danh bằng mã QR.</p>
        <LiveFormDialog classId={classId} />
      </div>

      {error && <FormError message={getErrorMessage(error)} />}

      {sorted.length === 0 ? (
        <EmptyState icon={Video} title="Chưa có buổi học nào" />
      ) : (
        <ul className="space-y-3">
          {sorted.map(s => {
            const next = NEXT_ACTION[s.status]
            const active = s.status === 'OPEN' || s.status === 'LIVE'
            const busy = (transition.isPending && transition.variables?.id === s.sessionId) || (cancel.isPending && cancel.variables?.id === s.sessionId)
            return (
              <li
                key={s.sessionId}
                className={cn(
                  'flex flex-col gap-4 rounded-2xl border bg-card p-4 dark:bg-card/70 lg:flex-row lg:items-center',
                  s.status === 'LIVE' ? 'border-red-500/40' : 'border-border/50',
                  (s.status === 'ENDED' || s.status === 'CANCELLED') && 'opacity-70',
                )}
              >
                <div className="shrink-0 lg:w-40">
                  <p className="text-sm font-semibold capitalize text-foreground">{formatWeekday(s.startsAt)}</p>
                  <p className="text-sm text-muted-foreground">{formatTime(s.startsAt)} – {formatTime(s.endsAt)}</p>
                </div>
                <div className="min-w-0 flex-1">
                  <div className="flex flex-wrap items-center gap-2">
                    <h3 className="font-semibold text-foreground">{s.title}</h3>
                    <LiveStatusPill status={s.status} />
                  </div>
                  <p className="mt-1 flex items-center gap-1 text-sm text-muted-foreground">
                    {s.type === 'ONLINE' ? <Video size={14} aria-hidden="true" /> : <MapPin size={14} aria-hidden="true" />}
                    {s.type === 'ONLINE' ? (s.meetingUrl ? 'Trực tuyến' : 'Trực tuyến (chưa có link)') : s.location || 'Tại lớp'}
                    {s.cancelReason && <span className="text-destructive"> · Hủy: {s.cancelReason}</span>}
                  </p>
                </div>
                <div className="flex flex-wrap gap-2">
                  {s.meetingUrl && active && (
                    <a href={s.meetingUrl} target="_blank" rel="noopener noreferrer" className="flex h-9 items-center gap-1.5 rounded-lg border border-border px-3 text-sm font-medium hover:bg-muted">
                      <ExternalLink size={14} aria-hidden="true" /> Link
                    </a>
                  )}
                  {active && (
                    <Button variant="outline" className="h-9 rounded-lg" onClick={() => setQrSession(s)}>
                      <QrCode size={14} aria-hidden="true" /> Mã QR
                    </Button>
                  )}
                  {s.status !== 'SCHEDULED' && s.status !== 'CANCELLED' && (
                    <Button variant="outline" className="h-9 rounded-lg" onClick={() => setAttendanceSession(s)}>
                      <ClipboardCheck size={14} aria-hidden="true" /> Điểm danh
                    </Button>
                  )}
                  {next && (
                    <Button className="h-9 rounded-lg" disabled={busy} onClick={() => transition.mutate({ id: s.sessionId, action: next.action })}>
                      {busy ? <Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" /> : <next.icon size={14} aria-hidden="true" />}
                      {next.label}
                    </Button>
                  )}
                  {(s.status === 'SCHEDULED' || s.status === 'OPEN') && (
                    <Button variant="ghost" className="h-9 rounded-lg text-destructive hover:bg-destructive/10" disabled={busy} onClick={() => handleCancel(s)}>
                      <Ban size={14} aria-hidden="true" /> Hủy
                    </Button>
                  )}
                </div>
              </li>
            )
          })}
        </ul>
      )}

      <QrDialog session={qrSession} onClose={() => setQrSession(null)} />
      <AttendanceDialog session={attendanceSession} students={students} onClose={() => setAttendanceSession(null)} />
    </div>
  )
}

// ─── Tạo buổi học ─────────────────────────────────────────────────────────────

function LiveFormDialog({ classId }: { classId: number }) {
  const [open, setOpen] = useState(false)
  const [form, setForm] = useState({ title: '', type: 'ONLINE' as LiveSessionType, startsAt: '', endsAt: '', meetingUrl: '', location: '' })
  const queryClient = useQueryClient()
  const set = (key: keyof typeof form) => (e: { target: { value: string } }) => setForm(p => ({ ...p, [key]: e.target.value }))

  const mutation = useMutation({
    mutationFn: () => createLiveSession({
      classId,
      title: form.title.trim(),
      type: form.type,
      startsAt: fromDateTimeInput(form.startsAt)!,
      endsAt: fromDateTimeInput(form.endsAt)!,
      meetingUrl: form.type === 'ONLINE' ? form.meetingUrl.trim() || null : null,
      location: form.type === 'OFFLINE' ? form.location.trim() || null : null,
    }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: teacherKeys.live(classId) })
      setOpen(false)
    },
  })

  const handleSubmit = (e: FormEvent) => {
    e.preventDefault()
    mutation.mutate()
  }

  return (
    <Dialog open={open} onOpenChange={next => { setOpen(next); if (next) mutation.reset() }}>
      <DialogTrigger asChild>
        <Button className="h-10 shrink-0 rounded-xl px-4"><Plus size={16} aria-hidden="true" /> Tạo buổi học</Button>
      </DialogTrigger>
      <DialogContent className="max-h-[90vh] overflow-y-auto sm:max-w-lg">
        <form onSubmit={handleSubmit} className="space-y-4">
          <DialogHeader>
            <DialogTitle>Tạo buổi học</DialogTitle>
            <DialogDescription>Hệ thống sẽ chặn nếu trùng giờ với buổi học khác của lớp.</DialogDescription>
          </DialogHeader>
          <Field label="Tiêu đề *"><Input required value={form.title} onChange={set('title')} className="h-11" placeholder="VD: Buổi 3 — Ôn tập giữa kỳ" /></Field>
          <Field label="Hình thức">
            <Select value={form.type} onChange={set('type')}>
              <option value="ONLINE">Trực tuyến</option>
              <option value="OFFLINE">Tại lớp</option>
            </Select>
          </Field>
          <div className="grid gap-4 sm:grid-cols-2">
            <Field label="Bắt đầu *"><Input required type="datetime-local" value={form.startsAt} onChange={set('startsAt')} className="h-11" /></Field>
            <Field label="Kết thúc *"><Input required type="datetime-local" value={form.endsAt} onChange={set('endsAt')} className="h-11" /></Field>
          </div>
          {form.type === 'ONLINE' ? (
            <Field label="Link phòng học" hint="Google Meet, Zoom..."><Input type="url" value={form.meetingUrl} onChange={set('meetingUrl')} className="h-11" placeholder="https://meet.google.com/..." /></Field>
          ) : (
            <Field label="Địa điểm"><Input value={form.location} onChange={set('location')} className="h-11" placeholder="VD: Phòng A101" /></Field>
          )}
          <FormError message={mutation.isError ? getErrorMessage(mutation.error) : null} />
          <DialogFooter><SubmitButton pending={mutation.isPending}>Tạo buổi học</SubmitButton></DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}

// ─── Mã QR điểm danh ──────────────────────────────────────────────────────────

function QrDialog({ session, onClose }: { session: LiveSession | null; onClose: () => void }) {
  const [qr, setQr] = useState<QrToken | null>(null)
  const [secondsLeft, setSecondsLeft] = useState(0)
  const generate = useMutation({ mutationFn: (id: number) => generateQr(id), onSuccess: setQr })

  useEffect(() => {
    const expires = parseDate(qr?.expiresAt)?.getTime()
    if (!expires) return
    const tick = () => setSecondsLeft(Math.max(0, Math.floor((expires - Date.now()) / 1000)))
    tick()
    const id = setInterval(tick, 1000)
    return () => clearInterval(id)
  }, [qr?.expiresAt])

  return (
    <Dialog open={session != null} onOpenChange={open => { if (!open) { setQr(null); generate.reset(); onClose() } }}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>Mã điểm danh</DialogTitle>
          <DialogDescription>Chiếu mã này lên màn hình. Học sinh nhập mã ở mục "Lịch học & Live" → Điểm danh.</DialogDescription>
        </DialogHeader>
        {qr && secondsLeft > 0 ? (
          <div className="space-y-3 text-center">
            <p className="break-all rounded-2xl bg-muted p-5 font-mono text-2xl font-bold tracking-widest text-foreground select-all">{qr.code}</p>
            <p className="text-sm text-muted-foreground">
              Hết hạn sau <b className="tabular-nums text-foreground">{Math.floor(secondsLeft / 60)}:{String(secondsLeft % 60).padStart(2, '0')}</b>
            </p>
          </div>
        ) : (
          <div className="py-6 text-center">
            <QrCode className="mx-auto mb-3 h-12 w-12 text-muted-foreground/50" aria-hidden="true" />
            <p className="text-sm text-muted-foreground">{qr ? 'Mã đã hết hạn.' : 'Tạo mã mới cho buổi học này.'}</p>
          </div>
        )}
        <FormError message={generate.isError ? getErrorMessage(generate.error) : null} />
        <DialogFooter>
          <Button className="h-10 rounded-xl" disabled={generate.isPending} onClick={() => session && generate.mutate(session.sessionId)}>
            {generate.isPending && <Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" />}
            {qr ? 'Tạo mã mới' : 'Tạo mã'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

// ─── Điểm danh ────────────────────────────────────────────────────────────────

function AttendanceDialog({ session, students, onClose }: { session: LiveSession | null; students: Enrollment[]; onClose: () => void }) {
  const queryClient = useQueryClient()
  const attendance = useQuery({
    queryKey: ['teacher', 'attendance', session?.sessionId],
    queryFn: () => getAttendance(session!.sessionId),
    enabled: session != null,
  })
  const mark = useMutation({
    mutationFn: (studentId: number) => markAttendance(session!.sessionId, studentId),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['teacher', 'attendance', session?.sessionId] }),
  })

  const present = new Map((attendance.data ?? []).filter(a => a.present).map(a => [a.studentId, a]))
  const active = students.filter(s => s.status === 'ACTIVE')

  return (
    <Dialog open={session != null} onOpenChange={open => { if (!open) onClose() }}>
      <DialogContent className="max-h-[90vh] overflow-y-auto sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>Điểm danh — {session?.title}</DialogTitle>
          <DialogDescription>Có mặt {present.size}/{active.length} học sinh.</DialogDescription>
        </DialogHeader>
        <FormError message={mark.isError ? getErrorMessage(mark.error) : null} />
        {attendance.isLoading ? (
          <div className="flex justify-center py-8"><Loader2 className="h-6 w-6 animate-spin text-muted-foreground" /></div>
        ) : (
          <ul className="divide-y divide-border/60 rounded-xl border border-border/50">
            {active.map(s => {
              const record = present.get(s.studentId)
              return (
                <li key={s.studentId} className="flex items-center gap-3 p-3">
                  <span className="min-w-0 flex-1 truncate text-sm font-medium text-foreground">{s.studentName}</span>
                  {record ? (
                    <span className="flex items-center gap-1 text-sm text-emerald-600 dark:text-emerald-400">
                      <CheckCircle2 size={15} aria-hidden="true" /> Có mặt <span className="text-xs text-muted-foreground">({record.source === 'QR' ? 'QR' : 'thủ công'})</span>
                    </span>
                  ) : (
                    <Button size="sm" variant="outline" className="h-8 rounded-lg" disabled={mark.isPending} onClick={() => mark.mutate(s.studentId)}>
                      Đánh dấu có mặt
                    </Button>
                  )}
                </li>
              )
            })}
            {active.length === 0 && <li className="p-6 text-center text-sm text-muted-foreground">Lớp chưa có học sinh.</li>}
          </ul>
        )}
      </DialogContent>
    </Dialog>
  )
}
