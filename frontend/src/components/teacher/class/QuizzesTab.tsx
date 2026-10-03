import { useState } from 'react'
import type { FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { BarChart3, CalendarClock, ClipboardList, Plus, RotateCcw, Timer } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import {
  Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle, DialogTrigger,
} from '@/components/ui/dialog'
import { Field, FormError, Select, SubmitButton } from '../FormKit'
import { EmptyState, Pill } from '@/components/student/StudentUi'
import { ProgressDialog } from './ProgressDialog'
import { createAssignment } from '@/api/teacherQuizApi'
import { teacherKeys, useQuizVersionIndex } from '@/hooks/useTeacherData'
import type { PublishedQuizVersion } from '@/hooks/useTeacherData'
import { formatDateTime, fromDateTimeInput, getErrorMessage, parseDate } from '@/lib/format'
import { RESULT_POLICY_LABEL } from '@/lib/quiz'
import type { ResultPolicy, TeacherAssignment } from '@/types/teacher'

interface QuizzesTabProps {
  classId: number
  assignments: TeacherAssignment[]
}

export function QuizzesTab({ classId, assignments }: QuizzesTabProps) {
  const index = useQuizVersionIndex()
  const [progressFor, setProgressFor] = useState<TeacherAssignment | null>(null)

  const sorted = [...assignments].sort((a, b) => b.assignmentId - a.assignmentId)

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between gap-3">
        <p className="text-sm text-muted-foreground">Giao quiz đã xuất bản, theo dõi bài nộp và chấm điểm.</p>
        <AssignQuizDialog classId={classId} versions={index.versions} loading={index.isLoading} />
      </div>

      {sorted.length === 0 ? (
        <EmptyState icon={ClipboardList} title="Chưa giao bài kiểm tra nào" />
      ) : (
        <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
          {sorted.map(a => {
            const version = index.byVersionId.get(a.quizVersionId)
            const now = new Date()
            const openAt = parseDate(a.openAt)
            const closeAt = parseDate(a.closeAt)
            const notOpen = openAt != null && openAt > now
            const closed = a.status === 'CLOSED' || (closeAt != null && closeAt < now)
            return (
              <div key={a.assignmentId} className="flex flex-col rounded-2xl border border-border/50 bg-card p-5 dark:bg-card/70">
                <div className="mb-2 flex items-start justify-between gap-3">
                  <h3 className="font-semibold leading-snug text-foreground">
                    {version?.quizTitle ?? (index.isLoading ? 'Đang tải...' : `Quiz phiên bản #${a.quizVersionId}`)}
                    {version && <span className="ml-1 text-xs font-normal text-muted-foreground">v{version.versionNo}</span>}
                  </h3>
                  {closed ? <Pill tone="muted">Đã đóng</Pill> : notOpen ? <Pill tone="amber">Chưa mở</Pill> : <Pill tone="emerald">Đang mở</Pill>}
                </div>
                <dl className="space-y-1.5 text-sm text-muted-foreground">
                  <div className="flex items-center gap-2"><CalendarClock size={14} aria-hidden="true" /><dd>{formatDateTime(a.openAt)} → {formatDateTime(a.closeAt)}</dd></div>
                  <div className="flex flex-wrap gap-x-4 gap-y-1.5">
                    <span className="flex items-center gap-2"><Timer size={14} aria-hidden="true" />{a.durationMinutes ? `${a.durationMinutes} phút` : 'Không giới hạn'}</span>
                    <span className="flex items-center gap-2"><RotateCcw size={14} aria-hidden="true" />{a.maxAttempts ?? '∞'} lượt</span>
                  </div>
                  <dd className="text-xs">Kết quả: {RESULT_POLICY_LABEL[a.resultPolicy]}</dd>
                </dl>
                <Button variant="outline" className="mt-4 h-10 rounded-xl" onClick={() => setProgressFor(a)}>
                  <BarChart3 size={15} aria-hidden="true" /> Bài nộp & chấm điểm
                </Button>
              </div>
            )
          })}
        </div>
      )}

      <ProgressDialog
        assignment={progressFor}
        version={progressFor ? index.byVersionId.get(progressFor.quizVersionId) : undefined}
        onClose={() => setProgressFor(null)}
      />
    </div>
  )
}

interface AssignQuizDialogProps {
  classId: number
  versions: PublishedQuizVersion[]
  loading: boolean
}

function AssignQuizDialog({ classId, versions, loading }: AssignQuizDialogProps) {
  const [open, setOpen] = useState(false)
  const [form, setForm] = useState({ versionId: '', openAt: '', closeAt: '', duration: '30', maxAttempts: '1', policy: 'AFTER_SUBMISSION' as ResultPolicy })
  const [localError, setLocalError] = useState<string | null>(null)
  const queryClient = useQueryClient()

  // Mỗi quiz chỉ lấy phiên bản mới nhất
  const latest = [...versions.reduce((m, v) => {
    const cur = m.get(v.quizId)
    if (!cur || v.versionNo > cur.versionNo) m.set(v.quizId, v)
    return m
  }, new Map<number, PublishedQuizVersion>()).values()]

  const mutation = useMutation({
    mutationFn: () => createAssignment({
      quizVersionId: Number(form.versionId),
      classId,
      openAt: fromDateTimeInput(form.openAt)!,
      closeAt: fromDateTimeInput(form.closeAt)!,
      durationMinutes: form.duration ? Number(form.duration) : null,
      maxAttempts: form.maxAttempts ? Number(form.maxAttempts) : null,
      resultPolicy: form.policy,
    }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: teacherKeys.assignments(classId) })
      setOpen(false)
    },
  })

  const set = (key: keyof typeof form) => (e: { target: { value: string } }) => setForm(p => ({ ...p, [key]: e.target.value }))

  const handleSubmit = (e: FormEvent) => {
    e.preventDefault()
    // Backend chưa kiểm tra thời gian (FIX.md #21) → chặn ở FE
    if (form.closeAt <= form.openAt) { setLocalError('Hạn nộp phải sau thời điểm mở.'); return }
    setLocalError(null)
    mutation.mutate()
  }

  return (
    <Dialog open={open} onOpenChange={next => { setOpen(next); if (next) { setLocalError(null); mutation.reset() } }}>
      <DialogTrigger asChild>
        <Button className="h-10 shrink-0 rounded-xl px-4"><Plus size={16} aria-hidden="true" /> Giao bài kiểm tra</Button>
      </DialogTrigger>
      <DialogContent className="max-h-[90vh] overflow-y-auto sm:max-w-lg">
        <form onSubmit={handleSubmit} className="space-y-4">
          <DialogHeader>
            <DialogTitle>Giao bài kiểm tra</DialogTitle>
            <DialogDescription>Chọn quiz đã xuất bản. Học sinh sẽ làm đúng phiên bản bạn chọn.</DialogDescription>
          </DialogHeader>
          <Field label="Quiz *">
            <Select required value={form.versionId} onChange={set('versionId')} disabled={loading}>
              <option value="">{loading ? 'Đang tải...' : latest.length ? '— Chọn quiz —' : 'Chưa có quiz đã xuất bản'}</option>
              {latest.map(v => <option key={v.versionId} value={v.versionId}>{v.quizTitle} (v{v.versionNo})</option>)}
            </Select>
          </Field>
          {!loading && latest.length === 0 && (
            <p className="text-sm text-muted-foreground">
              <Link to="/teacher/quizzes/new" className="font-medium text-primary hover:underline">Tạo quiz</Link> và xuất bản trước khi giao.
            </p>
          )}
          <div className="grid gap-4 sm:grid-cols-2">
            <Field label="Mở lúc *"><Input required type="datetime-local" value={form.openAt} onChange={set('openAt')} className="h-11" /></Field>
            <Field label="Hạn nộp *"><Input required type="datetime-local" value={form.closeAt} onChange={set('closeAt')} className="h-11" /></Field>
          </div>
          <div className="grid gap-4 sm:grid-cols-2">
            <Field label="Thời gian làm (phút)" hint="Để trống = không giới hạn"><Input type="number" min={1} value={form.duration} onChange={set('duration')} className="h-11" /></Field>
            <Field label="Số lượt làm" hint="Để trống = không giới hạn"><Input type="number" min={1} value={form.maxAttempts} onChange={set('maxAttempts')} className="h-11" /></Field>
          </div>
          <Field label="Học sinh xem kết quả">
            <Select value={form.policy} onChange={set('policy')}>
              {(Object.keys(RESULT_POLICY_LABEL) as ResultPolicy[]).map(p => <option key={p} value={p}>{RESULT_POLICY_LABEL[p]}</option>)}
            </Select>
          </Field>
          <FormError message={localError ?? (mutation.isError ? getErrorMessage(mutation.error) : null)} />
          <DialogFooter>
            <SubmitButton pending={mutation.isPending} disabled={!form.versionId}>Giao bài</SubmitButton>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
