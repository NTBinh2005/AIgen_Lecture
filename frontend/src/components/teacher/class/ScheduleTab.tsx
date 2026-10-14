import { useState } from 'react'
import type { FormEvent } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { CalendarDays, Plus, Trash2 } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import {
  Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle, DialogTrigger,
} from '@/components/ui/dialog'
import { Field, FormError, Select, SubmitButton } from '../FormKit'
import { EmptyState } from '@/components/student/StudentUi'
import { ScheduleList } from '@/components/student/ScheduleList'
import { createSchedule, deleteSchedule } from '@/api/teacherLiveApi'
import { teacherKeys } from '@/hooks/useTeacherData'
import { formatTime, formatWeekday, fromDateTimeInput, getErrorMessage } from '@/lib/format'
import type { Schedule, ScheduleType } from '@/types/student'

const WEEKDAYS = [
  { code: 'MO', label: 'T2' }, { code: 'TU', label: 'T3' }, { code: 'WE', label: 'T4' }, { code: 'TH', label: 'T5' },
  { code: 'FR', label: 'T6' }, { code: 'SA', label: 'T7' }, { code: 'SU', label: 'CN' },
]

export function ScheduleTab({ classId, schedules }: { classId: number; schedules: Schedule[] }) {
  const queryClient = useQueryClient()
  const remove = useMutation({
    mutationFn: deleteSchedule,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: teacherKeys.schedules(classId) }),
  })

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between gap-3">
        <p className="text-sm text-muted-foreground">Lịch cố định hiển thị cho học sinh ở mục "Lịch học & Live".</p>
        <ScheduleFormDialog classId={classId} />
      </div>
      {remove.isError && <FormError message={getErrorMessage(remove.error)} />}
      {schedules.length === 0 ? (
        <EmptyState icon={CalendarDays} title="Chưa có lịch học" />
      ) : (
        <div className="space-y-2">
          <ScheduleList schedules={schedules} />
          <div className="flex flex-wrap gap-2">
            {schedules.map(s => (
              <button
                key={s.scheduleId}
                onClick={() => { if (window.confirm(`Xóa lịch ${formatWeekday(s.startsAt)} ${formatTime(s.startsAt)}?`)) remove.mutate(s.scheduleId) }}
                className="flex min-h-9 items-center gap-1.5 rounded-lg border border-border px-3 text-xs text-muted-foreground hover:border-destructive/40 hover:text-destructive"
              >
                <Trash2 size={13} aria-hidden="true" /> Xóa {formatWeekday(s.startsAt)} {formatTime(s.startsAt)}
              </button>
            ))}
          </div>
        </div>
      )}
    </div>
  )
}

function ScheduleFormDialog({ classId }: { classId: number }) {
  const [open, setOpen] = useState(false)
  const [form, setForm] = useState({ startsAt: '', endsAt: '', type: 'LECTURE' as ScheduleType })
  const [days, setDays] = useState<string[]>([])
  const [localError, setLocalError] = useState<string | null>(null)
  const queryClient = useQueryClient()
  const set = (key: keyof typeof form) => (e: { target: { value: string } }) => setForm(p => ({ ...p, [key]: e.target.value }))

  const mutation = useMutation({
    mutationFn: () => createSchedule({
      classId,
      startsAt: fromDateTimeInput(form.startsAt)!,
      endsAt: fromDateTimeInput(form.endsAt)!,
      type: form.type,
      recurrence: days.length ? `FREQ=WEEKLY;BYDAY=${days.join(',')}` : null,
      timezone: 'Asia/Ho_Chi_Minh',
    }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: teacherKeys.schedules(classId) })
      setOpen(false)
    },
  })

  const handleSubmit = (e: FormEvent) => {
    e.preventDefault()
    if (form.endsAt <= form.startsAt) { setLocalError('Giờ kết thúc phải sau giờ bắt đầu.'); return }
    setLocalError(null)
    mutation.mutate()
  }

  return (
    <Dialog open={open} onOpenChange={next => { setOpen(next); if (next) { setDays([]); setLocalError(null); mutation.reset() } }}>
      <DialogTrigger asChild>
        <Button className="h-10 shrink-0 rounded-xl px-4"><Plus size={16} aria-hidden="true" /> Thêm lịch</Button>
      </DialogTrigger>
      <DialogContent className="sm:max-w-md">
        <form onSubmit={handleSubmit} className="space-y-4">
          <DialogHeader>
            <DialogTitle>Thêm lịch học</DialogTitle>
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
          <DialogFooter><SubmitButton pending={mutation.isPending}>Thêm lịch</SubmitButton></DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
