import { useState } from 'react'
import type { FormEvent, ReactNode } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import {
  Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle, DialogTrigger,
} from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Field, FormError, SubmitButton, TextArea } from './FormKit'
import { createClass, updateClass } from '@/api/teacherClassApi'
import { teacherKeys } from '@/hooks/useTeacherData'
import { useAuthStore } from '@/store/authStore'
import { fromDateTimeInput, getErrorMessage, toDateTimeInput } from '@/lib/format'
import type { ClassDetail } from '@/types/student'

interface ClassFormDialogProps {
  /** Có giá trị → sửa lớp; không có → tạo lớp mới */
  existing?: ClassDetail
  trigger: ReactNode
  onSaved?: (c: ClassDetail) => void
}

function initialState(c?: ClassDetail) {
  return {
    className: c?.className ?? '',
    classCode: c?.classCode ?? '',
    semester: c?.semester ?? '',
    startsAt: toDateTimeInput(c?.startsAt),
    endsAt: toDateTimeInput(c?.endsAt),
    maxStudents: c?.maxStudents?.toString() ?? '',
    description: c?.description ?? '',
  }
}

export function ClassFormDialog({ existing, trigger, onSaved }: ClassFormDialogProps) {
  const [open, setOpen] = useState(false)
  const [form, setForm] = useState(() => initialState(existing))
  const [localError, setLocalError] = useState<string | null>(null)
  const queryClient = useQueryClient()
  const user = useAuthStore(s => s.user)

  const mutation = useMutation({
    mutationFn: () => {
      const payload = {
        className: form.className.trim(),
        classCode: form.classCode.trim().toUpperCase(),
        semester: form.semester.trim() || null,
        startsAt: fromDateTimeInput(form.startsAt),
        endsAt: fromDateTimeInput(form.endsAt),
        maxStudents: form.maxStudents ? Number(form.maxStudents) : null,
        description: form.description.trim() || null,
      }
      return existing ? updateClass(existing.classId, payload) : createClass(Number(user?.id), payload)
    },
    onSuccess: saved => {
      queryClient.invalidateQueries({ queryKey: teacherKeys.all })
      setOpen(false)
      onSaved?.(saved)
    },
  })

  const set = (key: keyof typeof form) => (e: { target: { value: string } }) =>
    setForm(prev => ({ ...prev, [key]: e.target.value }))

  const handleSubmit = (e: FormEvent) => {
    e.preventDefault()
    if (form.startsAt && form.endsAt && form.endsAt <= form.startsAt) {
      setLocalError('Ngày kết thúc phải sau ngày bắt đầu.')
      return
    }
    setLocalError(null)
    mutation.mutate()
  }

  return (
    <Dialog
      open={open}
      onOpenChange={next => {
        setOpen(next)
        if (next) { setForm(initialState(existing)); setLocalError(null); mutation.reset() }
      }}
    >
      <DialogTrigger asChild>{trigger}</DialogTrigger>
      <DialogContent className="max-h-[90vh] overflow-y-auto sm:max-w-lg">
        <form onSubmit={handleSubmit} className="space-y-4">
          <DialogHeader>
            <DialogTitle>{existing ? 'Sửa thông tin lớp' : 'Tạo lớp học mới'}</DialogTitle>
            <DialogDescription>
              {existing ? 'Cập nhật thông tin hiển thị cho học sinh.' : 'Lớp mới ở trạng thái nháp. Kích hoạt lớp để học sinh có thể tham gia bằng mã lớp.'}
            </DialogDescription>
          </DialogHeader>

          <Field label="Tên lớp *">
            <Input required maxLength={150} value={form.className} onChange={set('className')} className="h-11" placeholder="VD: Lập trình Web - Nhóm 1" />
          </Field>
          <div className="grid gap-4 sm:grid-cols-2">
            <Field label="Mã lớp *" hint="Học sinh dùng mã này để tham gia">
              <Input required maxLength={20} value={form.classCode} onChange={set('classCode')} className="h-11 font-mono uppercase" placeholder="VD: WEB101" />
            </Field>
            <Field label="Học kỳ">
              <Input maxLength={30} value={form.semester} onChange={set('semester')} className="h-11" placeholder="VD: HK1-2026" />
            </Field>
          </div>
          <div className="grid gap-4 sm:grid-cols-2">
            <Field label="Bắt đầu" hint="Bắt buộc khi kích hoạt lớp">
              <Input type="datetime-local" value={form.startsAt} onChange={set('startsAt')} className="h-11" />
            </Field>
            <Field label="Kết thúc">
              <Input type="datetime-local" value={form.endsAt} onChange={set('endsAt')} className="h-11" />
            </Field>
          </div>
          <Field label="Sĩ số tối đa">
            <Input type="number" min={1} value={form.maxStudents} onChange={set('maxStudents')} className="h-11" placeholder="Không giới hạn" />
          </Field>
          <Field label="Mô tả">
            <TextArea maxLength={500} rows={3} value={form.description} onChange={set('description')} />
          </Field>

          <FormError message={localError ?? (mutation.isError ? getErrorMessage(mutation.error) : null)} />
          <DialogFooter>
            <SubmitButton pending={mutation.isPending}>{existing ? 'Lưu thay đổi' : 'Tạo lớp'}</SubmitButton>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
