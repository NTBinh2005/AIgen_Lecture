import { useMemo } from 'react'
import { Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { ArrowRight, GraduationCap } from 'lucide-react'
import { getQuizHistory } from '@/api/quizApi'
import { useAllAssignments } from '@/hooks/useStudentData'
import { CardSkeleton, EmptyState, ErrorState, PageHeader, Pill } from '@/components/student/StudentUi'
import { formatDateTime } from '@/lib/format'
import type { AttemptStatus } from '@/types/student'

const STATUS: Record<AttemptStatus, { label: string; tone: 'amber' | 'blue' | 'emerald' | 'violet' }> = {
  IN_PROGRESS: { label: 'Đang làm', tone: 'blue' },
  SUBMITTED: { label: 'Đã nộp', tone: 'violet' },
  AUTO_SUBMITTED: { label: 'Tự nộp khi hết giờ', tone: 'violet' },
  REVIEW_REQUIRED: { label: 'Chờ giáo viên chấm', tone: 'amber' },
  GRADED: { label: 'Đã chấm', tone: 'emerald' },
}

/** Lịch sử làm bài kiểm tra (GET /students/me/quiz-history). */
export default function StudentQuizHistoryPage() {
  const history = useQuery({ queryKey: ['student', 'quiz-history'], queryFn: getQuizHistory })
  const assignments = useAllAssignments()

  // API lịch sử chỉ trả assignmentId → ghép tên quiz và lớp từ danh sách bài kiểm tra của các lớp
  const info = useMemo(
    () => new Map(assignments.data.map(a => [a.assignmentId, { title: a.quizTitle, className: a.className }])),
    [assignments.data],
  )

  const attempts = history.data ?? []
  const graded = attempts.filter(a => a.status === 'GRADED' && a.finalScore != null)
  const average = graded.length ? graded.reduce((s, a) => s + (a.finalScore ?? 0), 0) / graded.length : null

  return (
    <div className="mx-auto max-w-4xl space-y-6">
      <PageHeader title="Kết quả học tập" description="Các bài kiểm tra bạn đã nộp." />

      <div className="grid grid-cols-3 gap-3">
        {[
          { label: 'Bài đã nộp', value: history.isLoading ? '—' : attempts.length },
          { label: 'Đã có điểm', value: history.isLoading ? '—' : graded.length },
          { label: 'Điểm trung bình', value: average == null ? '—' : average.toFixed(1) },
        ].map(s => (
          <div key={s.label} className="rounded-2xl border border-border/50 bg-card p-4 dark:bg-card/70">
            <p className="text-sm text-muted-foreground">{s.label}</p>
            <p className="mt-1 text-2xl font-bold tabular-nums text-foreground">{s.value}</p>
          </div>
        ))}
      </div>

      {history.isError && <ErrorState />}

      {history.isLoading ? (
        <CardSkeleton count={3} className="md:grid-cols-1 xl:grid-cols-1" />
      ) : attempts.length === 0 ? (
        <EmptyState icon={GraduationCap} title="Bạn chưa nộp bài kiểm tra nào" description="Bài kiểm tra được giao nằm trong từng lớp học." />
      ) : (
        <ul className="divide-y divide-border/60 overflow-hidden rounded-2xl border border-border/50 bg-card dark:bg-card/70">
          {attempts.map(a => {
            const s = STATUS[a.status] ?? { label: a.status, tone: 'blue' as const }
            const meta = info.get(a.assignmentId)
            const score = a.finalScore ?? a.objectiveScore
            return (
              <li key={a.attemptId}>
                <Link to={`/student/quizzes/${a.assignmentId}?view=result`} className="group flex flex-col gap-2 p-4 hover:bg-muted/40 sm:flex-row sm:items-center">
                  <div className="min-w-0 flex-1">
                    <p className="truncate font-semibold text-foreground group-hover:text-primary">
                      {meta?.title ?? `Bài kiểm tra #${a.assignmentId}`}
                      <span className="text-sm font-normal text-muted-foreground"> · lần {a.attemptNo}</span>
                    </p>
                    <p className="text-xs text-muted-foreground">
                      {meta?.className ? `${meta.className} · ` : ''}Nộp {formatDateTime(a.submittedAt)}
                    </p>
                  </div>
                  <div className="flex items-center gap-3">
                    <Pill tone={s.tone}>{s.label}</Pill>
                    <span className="w-14 text-right text-lg font-bold tabular-nums text-foreground">{score != null ? score : '—'}</span>
                    <ArrowRight size={16} className="hidden text-muted-foreground transition-transform group-hover:translate-x-0.5 sm:block" aria-hidden="true" />
                  </div>
                </Link>
              </li>
            )
          })}
        </ul>
      )}
    </div>
  )
}
