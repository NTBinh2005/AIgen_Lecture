import { useState } from 'react'
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  AlertTriangle, ArrowLeft, ArrowRight, CalendarClock, ChevronLeft, ChevronRight, ClipboardList,
  CloudCheck, Loader2, RotateCcw, Send, Timer,
} from 'lucide-react'
import { Button } from '@/components/ui/button'
import {
  Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle,
} from '@/components/ui/dialog'
import { getMyResult, startAttempt } from '@/api/quizApi'
import { useAllAssignments } from '@/hooks/useStudentData'
import { useQuizAttempt } from '@/hooks/useQuizAttempt'
import type { SaveState } from '@/hooks/useQuizAttempt'
import { QuizQuestion } from '@/components/student/QuizQuestion'
import { QuizResult } from '@/components/student/QuizResult'
import { EmptyState, ErrorState, Panel } from '@/components/student/StudentUi'
import { formatDateTime, getErrorMessage, getErrorStatus } from '@/lib/format'
import { cn } from '@/lib/utils'
import type { AttemptStart } from '@/types/student'

export default function QuizAttemptPage() {
  const { assignmentId: idParam } = useParams<{ assignmentId: string }>()
  const assignmentId = Number(idParam)
  const [searchParams] = useSearchParams()
  const [attempt, setAttempt] = useState<AttemptStart | null>(null)

  const assignments = useAllAssignments()
  const assignment = assignments.data.find(a => a.assignmentId === assignmentId)

  const start = useMutation({ mutationFn: () => startAttempt(assignmentId), onSuccess: setAttempt })

  if (attempt) return <AttemptView start={attempt} title={assignment?.quizTitle} />

  if (searchParams.get('view') === 'result') {
    return <ResultOnlyView assignmentId={assignmentId} title={assignment?.quizTitle} />
  }

  // ── Màn giới thiệu trước khi bắt đầu ──
  return (
    <div className="mx-auto max-w-2xl space-y-6">
      <BackLink />
      <Panel className="p-6 sm:p-8">
        <div className="mb-5 flex h-14 w-14 items-center justify-center rounded-2xl bg-violet-500/10 text-violet-600 dark:text-violet-400">
          <ClipboardList size={28} aria-hidden="true" />
        </div>
        <h1 className="text-2xl font-bold tracking-tight text-foreground">{assignment?.quizTitle ?? 'Bài kiểm tra'}</h1>
        {assignment && <p className="mt-1 text-sm text-muted-foreground">{assignment.className}</p>}

        {assignment && (
          <dl className="mt-6 grid grid-cols-1 gap-3 sm:grid-cols-3">
            <InfoItem icon={Timer} label="Thời gian" value={assignment.durationMinutes ? `${assignment.durationMinutes} phút` : 'Không giới hạn'} />
            <InfoItem icon={RotateCcw} label="Lượt làm" value={`${assignment.usedAttempts}/${assignment.maxAttempts ?? '∞'}`} />
            <InfoItem icon={CalendarClock} label="Hạn nộp" value={formatDateTime(assignment.closeAt)} />
          </dl>
        )}

        <ul className="mt-6 space-y-2 rounded-xl bg-muted/50 p-4 text-sm text-muted-foreground">
          <li>• Đồng hồ bắt đầu chạy ngay khi bạn bấm <b className="text-foreground">Bắt đầu</b>.</li>
          <li>• Đáp án được tự động lưu. Nếu mất mạng, bạn có thể vào lại để làm tiếp.</li>
          <li>• Hết giờ, bài sẽ tự động được nộp. Việc rời khỏi tab sẽ được ghi nhận.</li>
        </ul>

        {start.isError && (
          <div className="mt-4"><ErrorState message={getErrorMessage(start.error, 'Không bắt đầu được bài làm.')} /></div>
        )}

        <div className="mt-6 flex flex-col-reverse gap-3 sm:flex-row sm:justify-end">
          {assignment && assignment.usedAttempts > 0 && (
            <Link to={`?view=result`} className="flex h-11 items-center justify-center rounded-xl border border-border px-5 text-sm font-medium hover:bg-muted">
              Xem kết quả gần nhất
            </Link>
          )}
          <Button className="h-11 rounded-xl px-6 text-base" onClick={() => start.mutate()} disabled={start.isPending}>
            {start.isPending ? <Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" /> : <ArrowRight size={18} aria-hidden="true" />}
            {assignment?.studentStatus === 'IN_PROGRESS' ? 'Làm tiếp' : 'Bắt đầu'}
          </Button>
        </div>
      </Panel>
    </div>
  )
}

// ─── Làm bài ──────────────────────────────────────────────────────────────────

function AttemptView({ start, title }: { start: AttemptStart; title?: string }) {
  const [current, setCurrent] = useState(0)
  const [confirmOpen, setConfirmOpen] = useState(false)
  const queryClient = useQueryClient()
  const navigate = useNavigate()
  const { answers, setAnswer, saveState, secondsLeft, submit, submitting, submitError, result } = useQuizAttempt(start)

  const questions = [...start.questions].sort((a, b) => a.orderIndex - b.orderIndex)
  const answeredCount = questions.filter(q => (answers[q.questionId] ?? '').trim()).length
  const unanswered = questions.length - answeredCount

  const handleSubmit = async () => {
    await submit()
    setConfirmOpen(false)
    queryClient.invalidateQueries({ queryKey: ['student'] })
  }

  if (result) {
    return (
      <div className="mx-auto max-w-3xl space-y-6">
        <BackLink />
        <h1 className="text-2xl font-bold text-foreground">{title ?? 'Kết quả bài làm'}</h1>
        <QuizResult attempt={result} questions={questions} />
        <Button variant="outline" className="h-11 w-full rounded-xl sm:w-auto" onClick={() => navigate(-1)}>Quay lại</Button>
      </div>
    )
  }

  if (questions.length === 0) {
    return <EmptyState icon={ClipboardList} title="Bài kiểm tra chưa có câu hỏi" />
  }

  const q = questions[current]
  const lowTime = secondsLeft != null && secondsLeft <= 60

  return (
    <div className="mx-auto max-w-5xl">
      {/* ── Thanh trên: tiêu đề, trạng thái lưu, đồng hồ ── */}
      <div className="sticky top-16 z-10 -mx-4 mb-6 border-b border-border/50 bg-background/80 px-4 py-3 backdrop-blur-xl lg:-mx-8 lg:px-8">
        <div className="mx-auto flex max-w-5xl items-center gap-3">
          <div className="min-w-0 flex-1">
            <p className="truncate font-semibold text-foreground">{title ?? 'Bài kiểm tra'}</p>
            <SaveIndicator state={saveState} answered={answeredCount} total={questions.length} />
          </div>
          {secondsLeft != null && (
            <div
              className={cn(
                'flex items-center gap-1.5 rounded-xl px-3 py-2 font-mono text-sm font-bold tabular-nums',
                lowTime ? 'animate-pulse bg-red-500/10 text-red-600 dark:text-red-400' : 'bg-muted text-foreground',
              )}
              aria-live={lowTime ? 'assertive' : 'off'}
              aria-label={`Còn lại ${Math.floor(secondsLeft / 60)} phút ${secondsLeft % 60} giây`}
            >
              <Timer size={16} aria-hidden="true" />
              {String(Math.floor(secondsLeft / 60)).padStart(2, '0')}:{String(secondsLeft % 60).padStart(2, '0')}
            </div>
          )}
          <Button className="hidden h-10 rounded-xl sm:inline-flex" onClick={() => setConfirmOpen(true)}>
            <Send size={15} aria-hidden="true" /> Nộp bài
          </Button>
        </div>
      </div>

      <div className="grid gap-6 lg:grid-cols-[1fr_15rem]">
        {/* ── Câu hỏi ── */}
        <Panel className="p-5 sm:p-8">
          <QuizQuestion
            key={q.questionId}
            question={q}
            index={current}
            value={answers[q.questionId] ?? ''}
            onChange={v => setAnswer(q.questionId, v)}
            disabled={submitting}
          />
          <div className="mt-8 flex items-center justify-between gap-3">
            <Button variant="outline" className="h-11 rounded-xl px-4" disabled={current === 0} onClick={() => setCurrent(c => c - 1)}>
              <ChevronLeft size={18} aria-hidden="true" /> Câu trước
            </Button>
            {current < questions.length - 1 ? (
              <Button className="h-11 rounded-xl px-4" onClick={() => setCurrent(c => c + 1)}>
                Câu sau <ChevronRight size={18} aria-hidden="true" />
              </Button>
            ) : (
              <Button className="h-11 rounded-xl px-4" onClick={() => setConfirmOpen(true)}>
                <Send size={15} aria-hidden="true" /> Nộp bài
              </Button>
            )}
          </div>
        </Panel>

        {/* ── Bảng điều hướng câu hỏi ── */}
        <Panel className="h-fit lg:sticky lg:top-36">
          <p className="mb-3 text-sm font-semibold text-foreground">Danh sách câu hỏi</p>
          <div className="grid grid-cols-8 gap-2 sm:grid-cols-10 lg:grid-cols-5">
            {questions.map((question, i) => {
              const done = (answers[question.questionId] ?? '').trim() !== ''
              return (
                <button
                  key={question.questionId}
                  onClick={() => setCurrent(i)}
                  aria-label={`Câu ${i + 1}${done ? ', đã trả lời' : ''}`}
                  aria-current={i === current ? 'step' : undefined}
                  className={cn(
                    'flex aspect-square min-h-9 items-center justify-center rounded-lg text-sm font-semibold transition-all',
                    i === current && 'ring-2 ring-primary ring-offset-2 ring-offset-background',
                    done ? 'bg-primary text-primary-foreground' : 'bg-muted text-muted-foreground hover:bg-muted/70',
                  )}
                >
                  {i + 1}
                </button>
              )
            })}
          </div>
          <div className="mt-4 flex gap-4 text-xs text-muted-foreground">
            <span className="flex items-center gap-1.5"><span className="h-3 w-3 rounded bg-primary" /> Đã làm</span>
            <span className="flex items-center gap-1.5"><span className="h-3 w-3 rounded bg-muted" /> Chưa làm</span>
          </div>
        </Panel>
      </div>

      {/* Lỗi nộp bài khi hết giờ (không có dialog đang mở) */}
      {submitError != null && !confirmOpen && (
        <div className="mt-4"><ErrorState message={submitErrorMessage(submitError)} /></div>
      )}

      {/* ── Xác nhận nộp ── */}
      <Dialog open={confirmOpen} onOpenChange={setConfirmOpen}>
        <DialogContent className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle>Nộp bài?</DialogTitle>
            <DialogDescription>
              Bạn đã trả lời {answeredCount}/{questions.length} câu. Sau khi nộp sẽ không sửa được nữa.
            </DialogDescription>
          </DialogHeader>
          {unanswered > 0 && (
            <p className="flex items-center gap-2 rounded-xl bg-amber-500/10 p-3 text-sm text-amber-700 dark:text-amber-400">
              <AlertTriangle size={16} className="shrink-0" aria-hidden="true" />
              Còn {unanswered} câu chưa trả lời.
            </p>
          )}
          {submitError != null && <ErrorState message={submitErrorMessage(submitError)} />}
          <DialogFooter className="gap-2">
            <Button variant="outline" className="h-10 rounded-xl" onClick={() => setConfirmOpen(false)}>Làm tiếp</Button>
            <Button className="h-10 rounded-xl" onClick={handleSubmit} disabled={submitting}>
              {submitting && <Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" />}
              Nộp bài
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Nút nộp cố định cho mobile */}
      <div className="fixed inset-x-0 bottom-0 z-20 border-t border-border/50 bg-background/90 p-3 backdrop-blur-xl sm:hidden">
        <Button className="h-11 w-full rounded-xl" onClick={() => setConfirmOpen(true)}>
          <Send size={15} aria-hidden="true" /> Nộp bài ({answeredCount}/{questions.length})
        </Button>
      </div>
      <div className="h-16 sm:hidden" aria-hidden="true" />
    </div>
  )
}

function submitErrorMessage(error: unknown) {
  return getErrorStatus(error) === 500
    ? 'Hệ thống chấm bài đang lỗi nên chưa nộp được. Đáp án của bạn đã được lưu, hãy thử nộp lại sau hoặc báo giáo viên.'
    : getErrorMessage(error, 'Không nộp được bài. Vui lòng thử lại.')
}

function SaveIndicator({ state, answered, total }: { state: SaveState; answered: number; total: number }) {
  const text = {
    idle: `${answered}/${total} câu đã trả lời`,
    saving: 'Đang lưu...',
    saved: `Đã lưu · ${answered}/${total} câu`,
    error: 'Lưu thất bại — kiểm tra kết nối',
  }[state]
  return (
    <p className={cn('flex items-center gap-1 text-xs', state === 'error' ? 'text-destructive' : 'text-muted-foreground')}>
      {state === 'saving' && <Loader2 size={12} className="animate-spin" aria-hidden="true" />}
      {state === 'saved' && <CloudCheck size={12} aria-hidden="true" />}
      {text}
    </p>
  )
}

// ─── Chỉ xem kết quả ──────────────────────────────────────────────────────────

function ResultOnlyView({ assignmentId, title }: { assignmentId: number; title?: string }) {
  const result = useQuery({ queryKey: ['student', 'result', assignmentId], queryFn: () => getMyResult(assignmentId), retry: false })

  return (
    <div className="mx-auto max-w-3xl space-y-6">
      <BackLink />
      <h1 className="text-2xl font-bold text-foreground">{title ?? 'Kết quả bài làm'}</h1>
      {result.isLoading ? (
        <div className="h-48 animate-pulse rounded-3xl bg-muted" />
      ) : result.isError ? (
        <ErrorState message={getErrorMessage(result.error, 'Chưa có kết quả cho bài kiểm tra này.')} />
      ) : result.data ? (
        <QuizResult attempt={result.data} />
      ) : null}
    </div>
  )
}

// ─── Phụ ──────────────────────────────────────────────────────────────────────

function InfoItem({ icon: Icon, label, value }: { icon: typeof Timer; label: string; value: string }) {
  return (
    <div className="rounded-xl border border-border/50 p-3">
      <dt className="flex items-center gap-1.5 text-xs text-muted-foreground"><Icon size={13} aria-hidden="true" />{label}</dt>
      <dd className="mt-1 text-sm font-semibold text-foreground">{value}</dd>
    </div>
  )
}

function BackLink() {
  const navigate = useNavigate()
  return (
    <button onClick={() => navigate(-1)} className="inline-flex min-h-10 items-center gap-1.5 text-sm font-medium text-muted-foreground hover:text-foreground">
      <ArrowLeft size={16} aria-hidden="true" />
      Quay lại
    </button>
  )
}
