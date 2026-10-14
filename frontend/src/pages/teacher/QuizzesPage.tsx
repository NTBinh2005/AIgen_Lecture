import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { Archive, ClipboardList, Lock, Pencil, Plus, Rocket, Search } from 'lucide-react'
import { Button, buttonVariants } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { archiveQuiz, closeQuiz, publishQuiz } from '@/api/teacherQuizApi'
import { useTeacherQuizzes } from '@/hooks/useTeacherData'
import { FormError } from '@/components/teacher/FormKit'
import { AiQuizDialog } from '@/components/teacher/AiQuizDialog'
import { CardSkeleton, EmptyState, ErrorState, PageHeader, Pill } from '@/components/student/StudentUi'
import { formatDate, getErrorMessage } from '@/lib/format'
import { cn } from '@/lib/utils'
import type { QuizStatus } from '@/types/teacher'

const STATUS: Record<QuizStatus, { label: string; tone: 'amber' | 'emerald' | 'muted' | 'blue' }> = {
  DRAFT: { label: 'Nháp', tone: 'amber' },
  REVIEWED: { label: 'Nháp', tone: 'amber' },
  PUBLISHED: { label: 'Đã xuất bản', tone: 'emerald' },
  CLOSED: { label: 'Đã đóng', tone: 'muted' },
  ARCHIVED: { label: 'Lưu trữ', tone: 'muted' },
}

const FILTERS: Array<{ id: 'all' | 'draft' | 'PUBLISHED' | 'CLOSED' | 'ARCHIVED'; label: string }> = [
  { id: 'all', label: 'Tất cả' },
  { id: 'draft', label: 'Nháp' },
  { id: 'PUBLISHED', label: 'Đã xuất bản' },
  { id: 'CLOSED', label: 'Đã đóng' },
  { id: 'ARCHIVED', label: 'Lưu trữ' },
]

export default function TeacherQuizzesPage() {
  const { data, isLoading, isError } = useTeacherQuizzes()
  const [filter, setFilter] = useState<(typeof FILTERS)[number]['id']>('all')
  const [search, setSearch] = useState('')
  const queryClient = useQueryClient()

  const action = useMutation({
    mutationFn: async ({ id, kind }: { id: number; kind: 'publish' | 'close' | 'archive' }) => {
      if (kind === 'publish') await publishQuiz(id)
      else if (kind === 'close') await closeQuiz(id)
      else await archiveQuiz(id)
    },
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['teacher'] }),
  })

  const quizzes = (data?.content ?? [])
    .filter(q => filter === 'all' || (filter === 'draft' ? q.status === 'DRAFT' || q.status === 'REVIEWED' : q.status === filter))
    .filter(q => !search.trim() || q.title.toLowerCase().includes(search.trim().toLowerCase()))
    .sort((a, b) => b.quizId - a.quizId)

  const run = (id: number, kind: 'publish' | 'close' | 'archive', message: string) => {
    if (window.confirm(message)) action.mutate({ id, kind })
  }

  return (
    <div className="mx-auto max-w-6xl space-y-6">
      <PageHeader
        title="Bài kiểm tra"
        description="Soạn quiz, xuất bản rồi giao cho lớp ở trang quản lý lớp."
        action={
          <div className="flex gap-2">
            <AiQuizDialog />
            <Link to="/teacher/quizzes/new" className={cn(buttonVariants(), 'h-10 rounded-xl px-4')}>
              <Plus size={16} aria-hidden="true" /> Tạo quiz
            </Link>
          </div>
        }
      />

      <div className="flex flex-col gap-3 sm:flex-row sm:items-center">
        <div className="-mx-4 flex gap-2 overflow-x-auto px-4 scrollbar-none sm:mx-0 sm:px-0">
          {FILTERS.map(f => (
            <button
              key={f.id}
              onClick={() => setFilter(f.id)}
              className={cn(
                'min-h-9 whitespace-nowrap rounded-full border px-4 text-sm font-medium transition-colors',
                filter === f.id ? 'border-primary bg-primary text-primary-foreground' : 'border-border bg-card text-muted-foreground hover:text-foreground',
              )}
            >
              {f.label}
            </button>
          ))}
        </div>
        <div className="relative sm:ml-auto sm:w-64">
          <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" aria-hidden="true" />
          <Input type="search" value={search} onChange={e => setSearch(e.target.value)} placeholder="Tìm quiz..." aria-label="Tìm quiz" className="h-10 bg-card pl-9" />
        </div>
      </div>

      {isError && <ErrorState />}
      {action.isError && <FormError message={getErrorMessage(action.error)} />}

      {isLoading ? (
        <CardSkeleton count={3} />
      ) : quizzes.length === 0 ? (
        <EmptyState
          icon={ClipboardList}
          title={(data?.content.length ?? 0) === 0 ? 'Bạn chưa có quiz nào' : 'Không có quiz phù hợp'}
          action={(data?.content.length ?? 0) === 0 ? (
            <Link to="/teacher/quizzes/new" className={cn(buttonVariants(), 'h-11 rounded-xl px-5')}><Plus size={16} /> Tạo quiz đầu tiên</Link>
          ) : undefined}
        />
      ) : (
        <ul className="divide-y divide-border/60 overflow-hidden rounded-2xl border border-border/50 bg-card dark:bg-card/70">
          {quizzes.map(q => {
            const s = STATUS[q.status] ?? { label: q.status, tone: 'muted' as const }
            const draft = q.status === 'DRAFT' || q.status === 'REVIEWED'
            const busy = action.isPending && action.variables?.id === q.quizId
            return (
              <li key={q.quizId} className="flex flex-col gap-3 p-4 sm:flex-row sm:items-center">
                <Link to={`/teacher/quizzes/${q.quizId}`} className="group min-w-0 flex-1">
                  <div className="flex flex-wrap items-center gap-2">
                    <p className="truncate font-semibold text-foreground group-hover:text-primary">{q.title}</p>
                    <Pill tone={s.tone}>{s.label}</Pill>
                    {q.sourceType === 'AI' && <Pill tone="violet">AI</Pill>}
                  </div>
                  <p className="mt-0.5 text-xs text-muted-foreground">Tạo {formatDate(q.createdAt)}</p>
                </Link>
                <div className="flex flex-wrap gap-2">
                  {draft && (
                    <>
                      <Link to={`/teacher/quizzes/${q.quizId}`} className={cn(buttonVariants({ variant: 'outline' }), 'h-9 rounded-lg')}>
                        <Pencil size={14} aria-hidden="true" /> Sửa
                      </Link>
                      <Button className="h-9 rounded-lg" disabled={busy} onClick={() => run(q.quizId, 'publish', `Xuất bản "${q.title}"?`)}>
                        <Rocket size={14} aria-hidden="true" /> Xuất bản
                      </Button>
                    </>
                  )}
                  {q.status === 'PUBLISHED' && (
                    <Button variant="outline" className="h-9 rounded-lg" disabled={busy} onClick={() => run(q.quizId, 'close', `Đóng "${q.title}"? Không giao thêm được nữa.`)}>
                      <Lock size={14} aria-hidden="true" /> Đóng
                    </Button>
                  )}
                  {q.status === 'CLOSED' && (
                    <Button variant="outline" className="h-9 rounded-lg" disabled={busy} onClick={() => run(q.quizId, 'archive', `Lưu trữ "${q.title}"?`)}>
                      <Archive size={14} aria-hidden="true" /> Lưu trữ
                    </Button>
                  )}
                </div>
              </li>
            )
          })}
        </ul>
      )}
    </div>
  )
}
