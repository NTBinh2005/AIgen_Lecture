import { useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { AlertTriangle, ArrowLeft, Loader2, RotateCcw } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { FormError } from '../FormKit'
import { Pill } from '@/components/student/StudentUi'
import { finalizeAttempt, gradeAttempt, reopenAttempt } from '@/api/teacherQuizApi'
import { teacherKeys, useAssignmentProgress } from '@/hooks/useTeacherData'
import type { PublishedQuizVersion } from '@/hooks/useTeacherData'
import { formatDateTime, getErrorMessage } from '@/lib/format'
import type { AttemptStatus } from '@/types/student'
import type { ProgressAttempt, QuizQuestion, TeacherAssignment } from '@/types/teacher'

const STATUS: Record<AttemptStatus, { label: string; tone: 'amber' | 'blue' | 'emerald' | 'violet' }> = {
  IN_PROGRESS: { label: 'Đang làm', tone: 'blue' },
  SUBMITTED: { label: 'Đã nộp', tone: 'violet' },
  AUTO_SUBMITTED: { label: 'Tự nộp (hết giờ)', tone: 'violet' },
  REVIEW_REQUIRED: { label: 'Cần chấm', tone: 'amber' },
  GRADED: { label: 'Đã chấm', tone: 'emerald' },
}

function parseQuestions(snapshot: string | undefined): QuizQuestion[] {
  try {
    const parsed = JSON.parse(snapshot ?? '[]')
    return Array.isArray(parsed) ? parsed : []
  } catch {
    return []
  }
}

interface ProgressDialogProps {
  assignment: TeacherAssignment | null
  version?: PublishedQuizVersion
  onClose: () => void
}

export function ProgressDialog({ assignment, version, onClose }: ProgressDialogProps) {
  const [grading, setGrading] = useState<ProgressAttempt | null>(null)
  const progress = useAssignmentProgress(assignment?.assignmentId ?? null)
  const questions = parseQuestions(version?.questionsSnapshot)
  const attempts = [...(progress.data ?? [])].sort((a, b) => b.attemptId - a.attemptId)
  const needReview = attempts.filter(a => a.status === 'REVIEW_REQUIRED').length

  return (
    <Dialog open={assignment != null} onOpenChange={open => { if (!open) { setGrading(null); onClose() } }}>
      <DialogContent className="max-h-[90vh] overflow-y-auto sm:max-w-2xl">
        {grading ? (
          <GradePanel attempt={grading} questions={questions} assignmentId={assignment!.assignmentId} onBack={() => setGrading(null)} />
        ) : (
          <>
            <DialogHeader>
              <DialogTitle>{version?.quizTitle ?? 'Bài nộp'}</DialogTitle>
              <DialogDescription>
                {attempts.length} lượt làm{needReview ? ` · ${needReview} lượt cần chấm` : ''}
              </DialogDescription>
            </DialogHeader>

            <p className="flex gap-2 rounded-xl bg-amber-500/10 p-3 text-sm text-amber-800 dark:text-amber-300">
              <AlertTriangle size={16} className="mt-0.5 shrink-0" aria-hidden="true" />
              Backend chưa trả tên học sinh và nội dung câu trả lời cho giáo viên (FIX.md #17), nên danh sách chỉ hiện mã lượt làm.
            </p>

            {progress.isLoading ? (
              <div className="flex justify-center py-10"><Loader2 className="h-6 w-6 animate-spin text-muted-foreground" /></div>
            ) : attempts.length === 0 ? (
              <p className="py-10 text-center text-sm text-muted-foreground">Chưa có học sinh nào làm bài.</p>
            ) : (
              <ul className="divide-y divide-border/60 rounded-xl border border-border/50">
                {attempts.map(a => {
                  const s = STATUS[a.status] ?? { label: a.status, tone: 'blue' as const }
                  const score = a.finalScore ?? a.objectiveScore
                  return (
                    <li key={a.attemptId} className="flex flex-col gap-2 p-3 sm:flex-row sm:items-center">
                      <div className="min-w-0 flex-1">
                        <p className="font-medium text-foreground">
                          {a.studentName ?? `Lượt #${a.attemptId}`} <span className="text-sm font-normal text-muted-foreground">· lần {a.attemptNo}</span>
                        </p>
                        <p className="text-xs text-muted-foreground">
                          {a.submittedAt ? `Nộp ${formatDateTime(a.submittedAt)}` : `Bắt đầu ${formatDateTime(a.startedAt)}`}
                        </p>
                      </div>
                      <div className="flex items-center gap-2">
                        <Pill tone={s.tone}>{s.label}</Pill>
                        {score != null && <span className="w-12 text-right text-sm font-semibold">{score}đ</span>}
                        {a.status !== 'IN_PROGRESS' && (
                          <Button size="sm" variant={a.status === 'REVIEW_REQUIRED' ? 'default' : 'outline'} className="h-9 rounded-lg" onClick={() => setGrading(a)}>
                            {a.status === 'GRADED' ? 'Chấm lại' : 'Chấm điểm'}
                          </Button>
                        )}
                      </div>
                    </li>
                  )
                })}
              </ul>
            )}
          </>
        )}
      </DialogContent>
    </Dialog>
  )
}

interface GradePanelProps {
  attempt: ProgressAttempt
  questions: QuizQuestion[]
  assignmentId: number
  onBack: () => void
}

function GradePanel({ attempt, questions, assignmentId, onBack }: GradePanelProps) {
  const queryClient = useQueryClient()
  const [scores, setScores] = useState<Record<number, string>>({})

  const refresh = () => queryClient.invalidateQueries({ queryKey: teacherKeys.progress(assignmentId) })

  const grade = useMutation({
    mutationFn: async () => {
      const questionScores: Record<number, number> = {}
      questions.forEach(q => {
        if (q.questionId != null && scores[q.questionId] !== undefined && scores[q.questionId] !== '') {
          questionScores[q.questionId] = Number(scores[q.questionId])
        }
      })
      await gradeAttempt(attempt.attemptId, questionScores)
      await finalizeAttempt(attempt.attemptId)
    },
    onSuccess: () => { refresh(); onBack() },
  })

  const reopen = useMutation({
    mutationFn: () => reopenAttempt(attempt.attemptId),
    onSuccess: () => { refresh(); onBack() },
  })

  const total = questions.reduce((sum, q) => sum + (Number(scores[q.questionId ?? -1]) || 0), 0)
  const maxTotal = questions.reduce((sum, q) => sum + q.points, 0)

  return (
    <div className="space-y-4">
      <button onClick={onBack} className="flex min-h-9 items-center gap-1.5 text-sm text-muted-foreground hover:text-foreground">
        <ArrowLeft size={15} aria-hidden="true" /> Danh sách bài nộp
      </button>
      <DialogHeader>
        <DialogTitle>Chấm lượt #{attempt.attemptId}</DialogTitle>
        <DialogDescription>Nhập điểm cho từng câu. Câu để trống sẽ giữ điểm tự chấm của hệ thống.</DialogDescription>
      </DialogHeader>

      {questions.length === 0 ? (
        <p className="text-sm text-muted-foreground">Không đọc được danh sách câu hỏi của phiên bản quiz này.</p>
      ) : (
        <ol className="space-y-3">
          {questions.map((q, i) => (
            <li key={q.questionId ?? i} className="flex gap-3 rounded-xl border border-border/50 p-3">
              <span className="mt-1 text-sm font-semibold text-primary">{i + 1}.</span>
              <div className="min-w-0 flex-1">
                <p className="text-sm font-medium text-foreground">{q.questionText}</p>
                <p className="text-xs text-muted-foreground">{q.questionType} · tối đa {q.points} điểm</p>
              </div>
              <input
                type="number"
                min={0}
                max={q.points}
                step={0.25}
                value={scores[q.questionId ?? -1] ?? ''}
                onChange={e => setScores(p => ({ ...p, [q.questionId ?? -1]: e.target.value }))}
                aria-label={`Điểm câu ${i + 1}`}
                className="h-10 w-20 rounded-lg border border-input bg-background px-2 text-right text-sm dark:bg-input/30"
              />
            </li>
          ))}
        </ol>
      )}

      <FormError message={grade.isError ? getErrorMessage(grade.error) : reopen.isError ? getErrorMessage(reopen.error) : null} />

      <div className="flex flex-col-reverse gap-2 border-t border-border/50 pt-4 sm:flex-row sm:items-center">
        <Button variant="outline" className="h-10 rounded-xl" disabled={reopen.isPending} onClick={() => { if (window.confirm('Mở lại lượt làm này cho học sinh?')) reopen.mutate() }}>
          <RotateCcw size={15} aria-hidden="true" /> Mở lại bài
        </Button>
        <p className="text-sm text-muted-foreground sm:ml-auto">Tổng nhập: <b className="text-foreground">{total}</b> / {maxTotal}</p>
        <Button className="h-10 rounded-xl" disabled={grade.isPending || questions.length === 0} onClick={() => grade.mutate()}>
          {grade.isPending && <Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" />}
          Lưu & chốt điểm
        </Button>
      </div>
    </div>
  )
}
