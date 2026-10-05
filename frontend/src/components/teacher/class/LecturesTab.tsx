import { useState } from 'react'
import type { FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { Eye, PlaySquare, Plus, Trash2 } from 'lucide-react'
import { Button } from '@/components/ui/button'
import {
  Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle, DialogTrigger,
} from '@/components/ui/dialog'
import { Field, FormError, Select, SubmitButton } from '../FormKit'
import { EmptyState } from '@/components/student/StudentUi'
import { assignLectureToClass, unassignLecture } from '@/api/teacherClassApi'
import { teacherKeys, useTeacherLectures } from '@/hooks/useTeacherData'
import { formatDate, getErrorMessage } from '@/lib/format'
import type { ClassLecture } from '@/types/student'

interface LecturesTabProps {
  classId: number
  lectures: ClassLecture[]
}

export function LecturesTab({ classId, lectures }: LecturesTabProps) {
  const queryClient = useQueryClient()
  const remove = useMutation({
    mutationFn: (lectureId: number) => unassignLecture(classId, lectureId),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: teacherKeys.classLectures(classId) }),
  })

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between gap-3">
        <p className="text-sm text-muted-foreground">Học sinh của lớp sẽ thấy các bài giảng được giao ở đây.</p>
        <AssignLectureDialog classId={classId} assignedIds={lectures.map(l => l.lectureId)} />
      </div>

      {remove.isError && <FormError message={getErrorMessage(remove.error)} />}

      {lectures.length === 0 ? (
        <EmptyState icon={PlaySquare} title="Chưa giao bài giảng nào" description="Chỉ giao được bài giảng đã xuất bản." />
      ) : (
        <ul className="divide-y divide-border/60 overflow-hidden rounded-2xl border border-border/50 bg-card dark:bg-card/70">
          {lectures.map(l => (
            <li key={l.lectureId} className="flex items-center gap-3 p-4">
              <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-primary/10 text-primary">
                <PlaySquare size={18} aria-hidden="true" />
              </div>
              <div className="min-w-0 flex-1">
                <p className="truncate font-medium text-foreground">{l.title}</p>
                <p className="text-xs text-muted-foreground">Giao ngày {formatDate(l.assignedAt)}</p>
              </div>
              <Link
                to={`/teacher/lectures/${l.lectureId}`}
                className="flex h-9 w-9 items-center justify-center rounded-lg text-muted-foreground hover:bg-muted hover:text-foreground"
                aria-label={`Xem ${l.title}`}
              >
                <Eye size={16} />
              </Link>
              <button
                onClick={() => { if (window.confirm(`Bỏ giao "${l.title}" khỏi lớp?`)) remove.mutate(l.lectureId) }}
                disabled={remove.isPending}
                className="flex h-9 w-9 items-center justify-center rounded-lg text-muted-foreground hover:bg-destructive/10 hover:text-destructive"
                aria-label={`Bỏ giao ${l.title}`}
              >
                <Trash2 size={16} />
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}

function AssignLectureDialog({ classId, assignedIds }: { classId: number; assignedIds: number[] }) {
  const [open, setOpen] = useState(false)
  const [lectureId, setLectureId] = useState('')
  const queryClient = useQueryClient()
  const lectures = useTeacherLectures()

  const available = (lectures.data?.content ?? []).filter(l => l.status === 'PUBLISHED' && !assignedIds.includes(l.lectureId))

  const mutation = useMutation({
    mutationFn: () => assignLectureToClass(classId, Number(lectureId)),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: teacherKeys.classLectures(classId) })
      setOpen(false)
    },
  })

  const handleSubmit = (e: FormEvent) => {
    e.preventDefault()
    if (lectureId) mutation.mutate()
  }

  return (
    <Dialog open={open} onOpenChange={next => { setOpen(next); if (next) { setLectureId(''); mutation.reset() } }}>
      <DialogTrigger asChild>
        <Button className="h-10 shrink-0 rounded-xl px-4"><Plus size={16} aria-hidden="true" /> Giao bài giảng</Button>
      </DialogTrigger>
      <DialogContent className="sm:max-w-md">
        <form onSubmit={handleSubmit} className="space-y-4">
          <DialogHeader>
            <DialogTitle>Giao bài giảng cho lớp</DialogTitle>
            <DialogDescription>Chỉ hiện bài giảng đã xuất bản và chưa được giao cho lớp này.</DialogDescription>
          </DialogHeader>
          <Field label="Bài giảng">
            <Select value={lectureId} onChange={e => setLectureId(e.target.value)} disabled={lectures.isLoading}>
              <option value="">{lectures.isLoading ? 'Đang tải...' : available.length ? '— Chọn bài giảng —' : 'Không có bài giảng phù hợp'}</option>
              {available.map(l => (
                <option key={l.lectureId} value={l.lectureId}>
                  {l.title}{l.accessScope === 'PRIVATE' ? ' (Riêng tư)' : ''}
                </option>
              ))}
            </Select>
          </Field>
          {available.find(l => String(l.lectureId) === lectureId)?.accessScope === 'PRIVATE' && (
            <p className="rounded-xl bg-amber-500/10 p-3 text-sm text-amber-800 dark:text-amber-300">
              Bài này đang ở chế độ Riêng tư nên học sinh sẽ không xem được. Đổi sang "Lớp học" ở trang
              {' '}<Link to={`/teacher/lectures/${lectureId}/edit`} className="font-medium underline">Sửa nội dung</Link> rồi xuất bản lại.
            </p>
          )}
          {!lectures.isLoading && available.length === 0 && (
            <p className="text-sm text-muted-foreground">
              Hãy <Link to="/teacher/lectures" className="font-medium text-primary hover:underline">xuất bản bài giảng</Link> trước khi giao.
            </p>
          )}
          <FormError message={mutation.isError ? getErrorMessage(mutation.error) : null} />
          <DialogFooter>
            <SubmitButton pending={mutation.isPending} disabled={!lectureId}>Giao bài</SubmitButton>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
