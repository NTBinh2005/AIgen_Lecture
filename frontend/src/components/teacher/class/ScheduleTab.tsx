import { useState } from 'react'
import type { FormEvent } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { CalendarDays, Pencil, Plus, Trash2 } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import {
  Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle,
} from '@/components/ui/dialog'
import { Field, FormError, Select, SubmitButton } from '../FormKit'
import { EmptyState } from '@/components/student/StudentUi'
import { ScheduleViews } from '@/components/common/ScheduleViews'
import { createSchedule, deleteSchedule, updateSchedule } from '@/api/teacherLiveApi'
import { teacherKeys } from '@/hooks/useTeacherData'
import { formatTime, formatWeekday, fromDateTimeInput, getErrorMessage, toDateTimeInput } from '@/lib/format'
import type { LiveSession, Schedule, ScheduleType } from '@/types/student'
import { confirmDialog, notify } from '@/lib/notify'
import { describeRecurrence } from '@/lib/schedule'

const WEEKDAYS = [
  { code: 'MO', label: 'T2' }, { code: 'TU', label: 'T3' }, { code: 'WE', label: 'T4' }, { code: 'TH', label: 'T5' },
  { code: 'FR', label: 'T6' }, { code: 'SA', label: 'T7' }, { code: 'SU', label: 'CN' },
]

/** "FREQ=WEEKLY;BYDAY=MO,WE" → ['MO', 'WE'] */
function daysFromRecurrence(recurrence: string | null): string[] {
  const m = recurrence?.match(/BYDAY=([A-Z,]+)/)
  return m ? m[1].split(',').filter(Boolean) : []
}

export function ScheduleTab({ classId, schedules, sessions = [] }: { classId: number; schedules: Schedule[]; sessions?: LiveSession[] }) {
  const queryClient = useQueryClient()
  const [editing, setEditing] = useState<{ open: boolean; schedule: Schedule | null }>({ open: false, schedule: null })
  const remove = useMutation({
    mutationFn: deleteSchedule,
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: teacherKeys.schedules(classId) }); notify.success('Đã xoá lịch học') },
    onError: e => notify.apiError(e, 'Không xoá được lịch học.'),
  })

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between gap-3">
        <p className="text-sm text-muted-foreground">Lịch cố định hiển thị cho học sinh ở mục "Lịch học & Live".</p>
        <Button className="h-10 shrink-0 rounded-xl px-4" onClick={() => setEditing({ open: true, schedule: null })}>
          <Plus size={16} aria-hidden="true" /> Thêm lịch
        </Button>
      </div>
      {schedules.length === 0 && sessions.length === 0 ? (
        <EmptyState icon={CalendarDays} title="Chưa có lịch học" />
      ) : (
        <div className="space-y-2">
          <ScheduleViews schedules={schedules} sessions={sessions} />
          <ul className="flex flex-wrap gap-2">
            {schedules.map(s => {
              // Lịch lặp: gọi theo quy tắc (VD "Hằng tuần: Thứ 2, Thứ 4 · 21:00"), lịch một lần: theo ngày
              const label = s.recurrence
                ? `${describeRecurrence(s.recurrence)} · ${formatTime(s.startsAt)}`
                : `${formatWeekday(s.startsAt)} ${formatTime(s.startsAt)}`
              return (
                <li key={s.scheduleId} className="flex overflow-hidden rounded-lg border border-border text-xs text-muted-foreground">
                  <button
                    onClick={() => setEditing({ open: true, schedule: s })}
                    className="flex min-h-9 items-center gap-1.5 px-3 hover:bg-muted hover:text-foreground"
                    aria-label={`Sửa lịch ${label}`}
                  >
                    <Pencil size={13} aria-hidden="true" /> {label}
                  </button>
                  <button
                    onClick={async () => { if (await confirmDialog({ title: `Xoá lịch ${label}?`, confirmText: 'Xoá', destructive: true })) remove.mutate(s.scheduleId) }}
                    className="flex min-h-9 items-center border-l border-border px-2.5 hover:bg-destructive/10 hover:text-destructive"
                    aria-label={`Xoá lịch ${label}`}
                    title="Xoá"
                  >
                    <Trash2 size={13} aria-hidden="true" />
                  </button>
                </li>
              )
            })}
          </ul>
        </div>
      )}

      <Dialog open={editing.open} onOpenChange={open => { if (!open) setEditing({ open: false, schedule: null }) }}>
        <DialogContent className="sm:max-w-md">
          {editing.open && (
            <ScheduleForm
              key={editing.schedule?.scheduleId ?? 'new'}
              classId={classId}
              schedule={editing.schedule}
              onDone={() => setEditing({ open: false, schedule: null })}
            />
          )}
        </DialogContent>
      </Dialog>
    </div>
  )
}

function ScheduleForm({ classId, schedule, onDone }: { classId: number; schedule: Schedule | null; onDone: () => void }) {
  const [form, setForm] = useState(() => ({
    startsAt: schedule ? toDateTimeInput(schedule.startsAt) : '',
    endsAt: schedule ? toDateTimeInput(schedule.endsAt) : '',
    type: schedule?.type ?? ('LECTURE' as ScheduleType),
  }))
  const [days, setDays] = useState<string[]>(() => daysFromRecurrence(schedule?.recurrence ?? null))
  const [localError, setLocalError] = useState<string | null>(null)
  const queryClient = useQueryClient()
  const set = (key: keyof typeof form) => (e: { target: { value: string } }) => setForm(p => ({ ...p, [key]: e.target.value }))

  const mutation = useMutation({
    mutationFn: () => {
      const payload = {
        startsAt: fromDateTimeInput(form.startsAt)!,
        endsAt: fromDateTimeInput(form.endsAt)!,
        type: form.type,
        recurrence: days.length ? `FREQ=WEEKLY;BYDAY=${days.join(',')}` : null,
        timezone: 'Asia/Ho_Chi_Minh',
      }
      return schedule ? updateSchedule(schedule.scheduleId, payload) : createSchedule({ classId, ...payload })
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: teacherKeys.schedules(classId) })
      notify.success(schedule ? 'Đã lưu lịch học' : 'Đã thêm lịch học')
      onDone()
    },
  })

  const handleSubmit = (e: FormEvent) => {
    e.preventDefault()
    if (form.endsAt <= form.startsAt) { setLocalError('Giờ kết thúc phải sau giờ bắt đầu.'); return }
    setLocalError(null)
    mutation.mutate()
  }

  return (
    <form onSubmit={handleSubmit} className="space-y-4">
      <DialogHeader>
        <DialogTitle>{schedule ? 'Sửa lịch học' : 'Thêm lịch học'}</DialogTitle>
        <DialogDescription>Chọn buổi đầu tiên và các ngày lặp lại hằng tuần (nếu có).</DialogDescription>
      </DialogHeader>
      <div className="grid gap-4 sm:grid-cols-2">
        <Field label="Bắt đầu *"><Input required type="datetime-local" value={form.startsAt} onChange={set('startsAt')} className="h-11" /></Field>
        <Field label="Kết thúc *"><Input required type="datetime-local" value={form.endsAt} onChange={set('endsAt')} className="h-11" /></Field>
      </div>
      <Field label="Loại">
        <Select value={form.type} onChange={set('type')}>
          <option value="LECTURE">Lý thuyết</option>
          <option value="LAB">Thực hành</option>
          <option value="EXAM">Kiểm tra</option>
          <option value="MEETING">Họp</option>
          <option value="OTHER">Khác</option>
        </Select>
      </Field>
      <fieldset>
        <legend className="mb-1.5 text-sm font-medium text-foreground">Lặp lại hằng tuần</legend>
        <div className="flex flex-wrap gap-2">
          {WEEKDAYS.map(d => {
            const on = days.includes(d.code)
            return (
              <button
                key={d.code}
                type="button"
                aria-pressed={on}
                onClick={() => setDays(p => on ? p.filter(x => x !== d.code) : [...p, d.code])}
                className={`h-10 w-11 rounded-xl border text-sm font-medium transition-colors ${on ? 'border-primary bg-primary text-primary-foreground' : 'border-border hover:bg-muted'}`}
              >
                {d.label}
              </button>
            )
          })}
        </div>
      </fieldset>
      <FormError message={localError ?? (mutation.isError ? getErrorMessage(mutation.error) : null)} />
      <DialogFooter><SubmitButton pending={mutation.isPending}>{schedule ? 'Lưu thay đổi' : 'Thêm lịch'}</SubmitButton></DialogFooter>
    </form>
  )
}
