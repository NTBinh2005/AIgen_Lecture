import { CheckCircle2, Clock, HelpCircle, XCircle } from 'lucide-react'
import { Pill } from './StudentUi'
import { formatDateTime } from '@/lib/format'
import { cn } from '@/lib/utils'
import type { Attempt, AttemptQuestion, AttemptStatus } from '@/types/student'

const STATUS_LABEL: Record<AttemptStatus, string> = {
  IN_PROGRESS: 'Đang làm',
  SUBMITTED: 'Đã nộp — chờ chấm',
  AUTO_SUBMITTED: 'Tự động nộp khi hết giờ',
  REVIEW_REQUIRED: 'Chờ giáo viên chấm',
  GRADED: 'Đã chấm',
}

interface QuizResultProps {
  attempt: Attempt
  /** Có khi vừa làm xong — dùng để hiện nội dung câu hỏi */
  questions?: AttemptQuestion[]
}

export function QuizResult({ attempt, questions }: QuizResultProps) {
  const score = attempt.finalScore ?? attempt.objectiveScore
  const totalPoints = questions?.reduce((sum, q) => sum + q.points, 0)
  const graded = attempt.status === 'GRADED'
  const percent = score != null && totalPoints ? Math.round((score / totalPoints) * 100) : null
  const questionById = new Map(questions?.map(q => [q.questionId, q]))

  return (
    <div className="space-y-6">
      {/* Điểm */}
      <div className="rounded-3xl border border-border/50 bg-linear-to-br from-primary/10 via-card to-emerald-500/10 p-6 text-center sm:p-8">
        <p className="text-sm font-medium text-muted-foreground">Lượt làm {attempt.attemptNo}</p>
        {score != null ? (
          <p className="mt-2 text-5xl font-bold tracking-tight text-foreground">
            {score}
            {totalPoints ? <span className="text-2xl font-semibold text-muted-foreground"> / {totalPoints}</span> : null}
          </p>
        ) : (
          <p className="mt-2 text-2xl font-bold text-foreground">Chưa có điểm</p>
        )}
        {percent != null && (
          <div className="mx-auto mt-4 h-2 max-w-xs overflow-hidden rounded-full bg-muted">
            <div className="h-full rounded-full bg-linear-to-r from-primary to-emerald-500" style={{ width: `${Math.min(percent, 100)}%` }} />
          </div>
        )}
        <div className="mt-4 flex flex-wrap items-center justify-center gap-2">
          <Pill tone={graded ? 'emerald' : 'amber'}>{STATUS_LABEL[attempt.status] ?? attempt.status}</Pill>
          {attempt.submittedAt && (
            <span className="flex items-center gap-1 text-xs text-muted-foreground">
              <Clock size={12} aria-hidden="true" /> Nộp lúc {formatDateTime(attempt.submittedAt)}
            </span>
          )}
        </div>
      </div>

      {/* Chi tiết từng câu (nếu chính sách cho phép xem) */}
      {attempt.answers.length > 0 && (
        <div className="space-y-3">
          <h2 className="text-lg font-bold text-foreground">Chi tiết bài làm</h2>
          {attempt.answers.map((a, i) => {
            const q = questionById.get(a.questionId)
            const Icon = a.isCorrect == null ? HelpCircle : a.isCorrect ? CheckCircle2 : XCircle
            return (
              <div
                key={a.answerId}
                className={cn(
                  'flex gap-3 rounded-2xl border bg-card p-4 dark:bg-card/70',
                  a.isCorrect === true && 'border-emerald-500/30',
                  a.isCorrect === false && 'border-red-500/30',
                  a.isCorrect == null && 'border-border/50',
                )}
              >
                <Icon
                  size={20}
                  className={cn('mt-0.5 shrink-0', a.isCorrect === true ? 'text-emerald-500' : a.isCorrect === false ? 'text-red-500' : 'text-muted-foreground')}
                  aria-label={a.isCorrect == null ? 'Chưa chấm' : a.isCorrect ? 'Đúng' : 'Sai'}
                />
                <div className="min-w-0 flex-1 text-sm">
                  <p className="font-medium text-foreground">{q ? q.questionText : `Câu ${i + 1}`}</p>
                  <p className="mt-1 text-muted-foreground">Bạn trả lời: <span className="font-medium text-foreground">{a.response || '(bỏ trống)'}</span></p>
                  {a.correctAnswer && <p className="text-muted-foreground">Đáp án: <span className="font-medium text-emerald-600 dark:text-emerald-400">{a.correctAnswer}</span></p>}
                  {a.explanation && <p className="mt-1 rounded-lg bg-muted/60 p-2 text-muted-foreground">{a.explanation}</p>}
                </div>
                {a.pointsAwarded != null && <span className="shrink-0 text-sm font-semibold text-foreground">+{a.pointsAwarded}</span>}
              </div>
            )
          })}
        </div>
      )}
    </div>
  )
}
