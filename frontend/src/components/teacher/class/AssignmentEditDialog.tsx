import { useState } from 'react'
import type { FormEvent } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Field, FormError, Select, SubmitButton } from '../FormKit'
import { updateAssignment } from '@/api/teacherQuizApi'
import { teacherKeys } from '@/hooks/useTeacherData'
import { fromDateTimeInput, getErrorMessage, toDateTimeInput } from '@/lib/format'
import { notify } from '@/lib/notify'
import { RESULT_POLICY_LABEL } from '@/lib/quiz'
import type { ResultPolicy, TeacherAssignment } from '@/types/teacher'

interface AssignmentEditDialogProps {
  assignment: TeacherAssignment | null
  title: string
  onClose: () => void
}

/** Sửa giờ mở / hạn nộp / thời lượng / số lượt / chính sách kết quả / trạng thái của bài đã giao. */
export function AssignmentEditDialog({ assignment, title, onClose }: AssignmentEditDialogProps) {
  return (
    <Dialog open={assignment != null} onOpenChange={open => { if (!open) onClose() }}>
      <DialogContent className="max-h-[90vh] overflow-y-auto sm:max-w-lg">
        {assignment && <EditForm key={assignment.assignmentId} assignment={assignment} title={title} onDone={onClose} />}
      </DialogContent>
    </Dialog>
  )
}

function EditForm({ assignment, title, onDone }: { assignment: TeacherAssignment; title: string; onDone: () => void }) {
  const queryClient = useQueryClient()
  const [form, setForm] = useState(() => ({
    openAt: toDateTimeInput(assignment.openAt),
    closeAt: toDateTimeInput(assignment.closeAt),
    duration: assignment.durationMinutes != null ? String(assignment.durationMinutes) : '',
    maxAttempts: assignment.maxAttempts != null ? String(assignment.maxAttempts) : '',
    policy: assignment.resultPolicy,
    status: assignment.status,
  }))
  const [localError, setLocalError] = useState<string | null>(null)
  const set = (key: keyof typeof form) => (e: { target: { value: string } }) => setForm(p => ({ ...p, [key]: e.target.value }))

  const mutation = useMutation({
    mutationFn: () => {
      // Chỉ gửi trường thay đổi: backend chặn đổi thời lượng / số lượt khi đã có bài làm
      const payload: Parameters<typeof updateAssignment>[1] = {}
      const openAt = form.openAt ? fromDateTimeInput(form.openAt) : null
      const closeAt = form.closeAt ? fromDateTimeInput(form.closeAt) : null
      if (form.openAt !== toDateTimeInput(assignment.openAt)) payload.openAt = openAt
      if (form.closeAt !== toDateTimeInput(assignment.closeAt)) payload.closeAt = closeAt
      const duration = form.duration ? Number(form.duration) : null
      if (duration !== assignment.durationMinutes) payload.durationMinutes = duration
      const attempts = form.maxAttempts ? Number(form.maxAttempts) : null
      if (attempts !== assignment.maxAttempts) payload.maxAttempts = attempts
      if (form.policy !== assignment.resultPolicy) payload.resultPolicy = form.policy
      if (form.status !== assignment.status) payload.status = form.status
      return updateAssignment(assignment.assignmentId, payload)
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: teacherKeys.assignments(assignment.classId) })
      notify.success('Đã lưu bài kiểm tra')
      onDone()
    },
  })

  const submit = (e: FormEvent) => {
    e.preventDefault()
    if (form.openAt && form.closeAt && form.closeAt <= form.openAt) { setLocalError('Hạn nộp phải sau thời điểm mở.'); return }
    setLocalError(null)
    mutation.mutate()
  }

  return (
    <form onSubmit={submit} className="space-y-4">
      <DialogHeader>
        <DialogTitle>Sửa bài kiểm tra đã giao</DialogTitle>
        <DialogDescription>{title}. Để trống giờ mở / hạn nộp nếu không muốn giới hạn.</DialogDescription>
      </DialogHeader>
      <div className="grid gap-4 sm:grid-cols-2">
        <Field label="Mở lúc"><Input type="datetime-local" value={form.openAt} onChange={set('openAt')} className="h-11" /></Field>
        <Field label="Hạn nộp"><Input type="datetime-local" value={form.closeAt} onChange={set('closeAt')} className="h-11" /></Field>
      </div>
      <div className="grid gap-4 sm:grid-cols-2">
        <Field label="Thời lượng (phút)" hint="Trống = không giới hạn"><Input type="number" min={1} value={form.duration} onChange={set('duration')} className="h-11" /></Field>
        <Field label="Số lượt / học sinh" hint="Trống = không giới hạn"><Input type="number" min={1} value={form.maxAttempts} onChange={set('maxAttempts')} className="h-11" /></Field>
      </div>
      <Field label="Học sinh xem kết quả">
        <Select value={form.policy} onChange={set('policy')}>
          {(Object.keys(RESULT_POLICY_LABEL) as ResultPolicy[]).map(p => <option key={p} value={p}>{RESULT_POLICY_LABEL[p]}</option>)}
        </Select>
      </Field>
      <Field label="Trạng thái">
        <Select value={form.status} onChange={set('status')}>
          <option value="OPEN">Đang mở</option>
          <option value="CLOSED">Đã đóng (không nhận bài nữa)</option>
        </Select>
      </Field>
      <FormError message={localError ?? (mutation.isError ? getErrorMessage(mutation.error, 'Không lưu được bài kiểm tra.') : null)} />
      <DialogFooter><SubmitButton pending={mutation.isPending}>Lưu thay đổi</SubmitButton></DialogFooter>
    </form>
  )
}
