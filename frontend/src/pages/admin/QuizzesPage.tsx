import { useState } from 'react'
import { ClipboardList, Search } from 'lucide-react'
import { Input } from '@/components/ui/input'
import { Select } from '@/components/teacher/FormKit'
import { CardSkeleton, EmptyState, ErrorState, PageHeader, Pill } from '@/components/student/StudentUi'
import { useAdminQuizzes } from '@/hooks/useAdminData'
import { formatDate } from '@/lib/format'
import type { QuizStatus } from '@/types/teacher'

const STATUS: Record<QuizStatus, { label: string; tone: 'amber' | 'emerald' | 'muted' }> = {
  DRAFT: { label: 'Nháp', tone: 'amber' },
  REVIEWED: { label: 'Nháp', tone: 'amber' },
  PUBLISHED: { label: 'Đã xuất bản', tone: 'emerald' },
  CLOSED: { label: 'Đã đóng', tone: 'muted' },
  ARCHIVED: { label: 'Lưu trữ', tone: 'muted' },
}

/** Danh sách quiz của mọi giáo viên (chỉ xem — soạn/xuất bản thuộc về giáo viên sở hữu). */
export default function AdminQuizzesPage() {
  const { data, isLoading, isError } = useAdminQuizzes()
  const [search, setSearch] = useState('')
  const [status, setStatus] = useState<QuizStatus | 'all'>('all')

  const quizzes = (data?.content ?? [])
    .filter(q => status === 'all' || q.status === status)
    .filter(q => !search.trim() || q.title.toLowerCase().includes(search.trim().toLowerCase()))
    .sort((a, b) => b.quizId - a.quizId)

  return (
    <div className="mx-auto max-w-6xl space-y-6">
      <PageHeader title="Bài kiểm tra" description={`Toàn bộ quiz trong hệ thống (${data?.totalElements ?? 0}).`} />

      <div className="flex flex-col gap-3 sm:flex-row">
        <div className="relative flex-1">
          <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" aria-hidden="true" />
          <Input type="search" value={search} onChange={e => setSearch(e.target.value)} placeholder="Tìm quiz..." aria-label="Tìm quiz" className="h-10 bg-card pl-9" />
        </div>
        <Select aria-label="Lọc trạng thái" value={status} onChange={e => setStatus(e.target.value as QuizStatus | 'all')} className="h-10 sm:w-48">
          <option value="all">Mọi trạng thái</option>
          <option value="DRAFT">Nháp</option>
          <option value="PUBLISHED">Đã xuất bản</option>
          <option value="CLOSED">Đã đóng</option>
          <option value="ARCHIVED">Lưu trữ</option>
        </Select>
      </div>

      {isError && <ErrorState />}
      {isLoading ? (
        <CardSkeleton count={3} className="md:grid-cols-1 xl:grid-cols-1" />
      ) : quizzes.length === 0 ? (
        <EmptyState icon={ClipboardList} title="Không có quiz phù hợp" />
      ) : (
        <ul className="divide-y divide-border/60 overflow-hidden rounded-2xl border border-border/50 bg-card dark:bg-card/70">
          {quizzes.map(q => {
            const s = STATUS[q.status] ?? { label: q.status, tone: 'muted' as const }
            return (
              <li key={q.quizId} className="flex flex-col gap-1 p-4 sm:flex-row sm:items-center sm:gap-3">
                <div className="min-w-0 flex-1">
                  <p className="truncate font-medium text-foreground">{q.title}</p>
                  <p className="text-xs text-muted-foreground">
                    #{q.quizId} · {q.sourceType === 'AI' ? 'Tạo bằng AI' : 'Soạn tay'}
                    {q.sourceLectureId ? ` · từ bài giảng #${q.sourceLectureId}` : ''} · Tạo {formatDate(q.createdAt)}
                  </p>
                </div>
                <Pill tone={s.tone}>{s.label}</Pill>
              </li>
            )
          })}
        </ul>
      )}
    </div>
  )
}
