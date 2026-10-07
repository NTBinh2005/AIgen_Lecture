import { useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { ArrowLeft, CheckCircle2, HelpCircle, Loader2, RotateCcw, XCircle } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { FormError } from '../FormKit'
import { Pill } from '@/components/student/StudentUi'
import { finalizeAttempt, gradeAttempt, reopenAttempt } from '@/api/teacherQuizApi'
import { teacherKeys, useAssignmentProgress } from '@/hooks/useTeacherData'
import type { PublishedQuizVersion } from '@/hooks/useTeacherData'
import { formatDateTime, getErrorMessage } from '@/lib/format'
import { QUESTION_TYPE_LABEL } from '@/lib/quiz'
import { cn } from '@/lib/utils'
import type { AttemptAnswer, AttemptStatus } from '@/types/student'
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
    return Array.isArray(parsed) ? [...parsed].sort((a, b) => (a.orderIndex ?? 0) - (b.orderIndex ?? 0)) : []
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
  const title = assignment?.quizTitle ?? version?.quizTitle ?? 'Bài nộp'

  return (
    <Dialog open={assignment != null} onOpenChange={open => { if (!open) { setGrading(null); onClose() } }}>
      <DialogContent className="max-h-[90vh] overflow-y-auto sm:max-w-2xl">
        {grading ? (
          <GradePanel
            key={grading.attemptId}
            attempt={grading}
            questions={questions}
            assignmentId={assignment!.assignmentId}
            onBack={() => setGrading(null)}
          />
        ) : (
          <>
            <DialogHeader>
              <DialogTitle>{title}</DialogTitle>
              <DialogDescription>
                {attempts.length} lượt làm{needReview ? ` · ${needReview} lượt cần chấm` : ''}
              </DialogDescription>
            </DialogHeader>

            {progress.isLoading ? (
              <div className="flex justify-center py-10"><Loader2 className="h-6 w-6 animate-spin text-muted-foreground" /></div>
            ) : progress.isError ? (
              <FormError message={getErrorMessage(progress.error, 'Không tải được danh sách bài nộp.')} />
            ) : attempts.length === 0 ? (
              <p className="py-10 text-center text-sm text-muted-foreground">Chưa có học sinh nào làm bài.</p>
            ) : (
              <ul className="divide-y divide-border/60 rounded-xl border border-border/50">
                {attempts.map(a => {
                  const s = STATUS[a.status] ?? { label: a.status, tone: 'blue' as const }
                  const score = a.finalScore ?? a.objectiveScore
                  return (
                    <li key={a.attemptId} className="flex flex-col gap-2 p-3 sm:flex-row sm:items-center">
                      <div className="flex min-w-0 flex-1 items-center gap-3">
                        <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-linear-to-tr from-blue-600 to-indigo-600 text-sm font-bold text-white">
                          {(a.studentName ?? '?').charAt(0).toUpperCase()}
                        </div>
                        <div className="min-w-0">
                          <p className="truncate font-medium text-foreground">
                            {a.studentName ?? `Lượt #${a.attemptId}`}
                            <span className="text-sm font-normal text-muted-foreground"> · lần {a.attemptNo}</span>
                          </p>
                          <p className="text-xs text-muted-foreground">
                            {a.submittedAt ? `Nộp ${formatDateTime(a.submittedAt)}` : `Bắt đầu ${formatDateTime(a.startedAt)}`}
                          </p>
                        </div>
                      </div>
                      <div className="flex items-center gap-2">
                        <Pill tone={s.tone}>{s.label}</Pill>
                        {score != null && <span className="w-12 text-right text-sm font-semibold">{score}đ</span>}
                        {a.status !== 'IN_PROGRESS' && (
                          <Button
                            size="sm"
                            variant={a.status === 'REVIEW_REQUIRED' ? 'default' : 'outline'}
                            className="h-9 rounded-lg"
                            onClick={() => setGrading(a)}
                          >
                            {a.status === 'GRADED' ? 'Xem / chấm lại' : 'Chấm điểm'}
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
  const answerByQuestion = new Map<number, AttemptAnswer>((attempt.answers ?? []).map(a => [a.questionId, a]))

  // Điền sẵn điểm hiện có: điểm giáo viên đã chấm, nếu chưa thì điểm tự chấm
  const [scores, setScores] = useState<Record<number, string>>(() => {
    const init: Record<number, string> = {}
    questions.forEach(q => {
      const ans = q.questionId != null ? answerByQuestion.get(q.questionId) : undefined
      const value = ans?.teacherFinalScore ?? ans?.pointsAwarded
      if (q.questionId != null && value != null) init[q.questionId] = String(value)
    })
    return init
  })

  const refresh = () => queryClient.invalidateQueries({ queryKey: teacherKeys.progress(assignmentId) })

  const grade = useMutation({
    mutationFn: async () => {
      const questionScores: Record<number, number> = {}
      questions.forEach(q => {
        const raw = q.questionId != null ? scores[q.questionId] : undefined
        if (q.questionId != null && raw !== undefined && raw !== '') questionScores[q.questionId] = Number(raw)
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
  const invalid = questions.some(q => {
    const v = Number(scores[q.questionId ?? -1])
    return scores[q.questionId ?? -1] !== undefined && scores[q.questionId ?? -1] !== '' && (v < 0 || v > q.points)
  })

  return (
    <div className="space-y-4">
      <button onClick={onBack} className="flex min-h-9 items-center gap-1.5 text-sm text-muted-foreground hover:text-foreground">
        <ArrowLeft size={15} aria-hidden="true" /> Danh sách bài nộp
      </button>
      <DialogHeader>
        <DialogTitle>{attempt.studentName ?? `Lượt #${attempt.attemptId}`} — lần {attempt.attemptNo}</DialogTitle>
        <DialogDescription>
          Câu trắc nghiệm và trả lời ngắn đã được tự chấm; bạn có thể sửa điểm. Câu tự luận cần nhập điểm.
        </DialogDescription>
      </DialogHeader>

      {questions.length === 0 ? (
        <p className="text-sm text-muted-foreground">Không đọc được danh sách câu hỏi của phiên bản quiz này.</p>
      ) : (
        <ol className="space-y-3">
          {questions.map((q, i) => {
            const ans = q.questionId != null ? answerByQuestion.get(q.questionId) : undefined
            const Icon = ans?.isCorrect == null ? HelpCircle : ans.isCorrect ? CheckCircle2 : XCircle
            const key = q.questionId ?? -1
            return (
              <li key={q.questionId ?? i} className="space-y-2 rounded-xl border border-border/50 p-3">
                <div className="flex gap-3">
                  <span className="mt-0.5 text-sm font-semibold text-primary">{i + 1}.</span>
                  <div className="min-w-0 flex-1">
                    <p className="text-sm font-medium text-foreground">{q.questionText}</p>
                    <p className="text-xs text-muted-foreground">{QUESTION_TYPE_LABEL[q.questionType] ?? q.questionType} · tối đa {q.points} điểm</p>
                  </div>
                  <input
                    type="number"
                    min={0}
                    max={q.points}
                    step={0.25}
                    value={scores[key] ?? ''}
                    onChange={e => setScores(p => ({ ...p, [key]: e.target.value }))}
                    aria-label={`Điểm câu ${i + 1}`}
                    className="h-10 w-20 shrink-0 rounded-lg border border-input bg-background px-2 text-right text-sm dark:bg-input/30"
                  />
                </div>
                <div className="ml-6 rounded-lg bg-muted/50 p-2.5 text-sm">
                  <p className="flex items-start gap-1.5">
                    <Icon
                      size={15}
                      className={cn('mt-0.5 shrink-0', ans?.isCorrect === true ? 'text-emerald-500' : ans?.isCorrect === false ? 'text-red-500' : 'text-muted-foreground')}
                      aria-label={ans?.isCorrect == null ? 'Chưa chấm tự động' : ans.isCorrect ? 'Đúng' : 'Sai'}
                    />
                    <span className="whitespace-pre-wrap break-words text-foreground">{ans?.response || <i className="text-muted-foreground">(bỏ trống)</i>}</span>
                  </p>
                  {(ans?.correctAnswer ?? q.correctAnswer) && (
                    <p className="mt-1 text-xs text-muted-foreground">
                      Đáp án: <b className="text-emerald-600 dark:text-emerald-400">{ans?.correctAnswer ?? q.correctAnswer}</b>
                    </p>
                  )}
                </div>
              </li>
            )
          })}
        </ol>
      )}

      <FormError
        message={invalid ? 'Có câu nhập điểm vượt quá điểm tối đa hoặc nhỏ hơn 0.'
          : grade.isError ? getErrorMessage(grade.error)
            : reopen.isError ? getErrorMessage(reopen.error) : null}
      />

      <div className="flex flex-col-reverse gap-2 border-t border-border/50 pt-4 sm:flex-row sm:items-center">
        <Button
          variant="outline"
          className="h-10 rounded-xl"
          disabled={reopen.isPending}
          onClick={() => { if (window.confirm('Mở lại lượt làm này cho học sinh?')) reopen.mutate() }}
        >
          <RotateCcw size={15} aria-hidden="true" /> Mở lại bài
        </Button>
        <p className="text-sm text-muted-foreground sm:ml-auto">Tổng: <b className="text-foreground">{total}</b> / {maxTotal}</p>
        <Button className="h-10 rounded-xl" disabled={grade.isPending || invalid || questions.length === 0} onClick={() => grade.mutate()}>
          {grade.isPending && <Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" />}
          Lưu & chốt điểm
        </Button>
      </div>
    </div>
  )
}
