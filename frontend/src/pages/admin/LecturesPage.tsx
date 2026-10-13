import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ArrowRightLeft, BookOpen, ChevronLeft, ChevronRight, Eye, FilePen, Search, Send } from 'lucide-react'
import { Input } from '@/components/ui/input'
import { getLectures, publishLecture } from '@/api/lectureApi'
import { migrateLectureQuizzes } from '@/api/adminApi'
import type { LectureResponse } from '@/api/lectureApi'
import { LectureStatusBadge } from '@/components/common/LectureStatusBadge'
import { FormError } from '@/components/teacher/FormKit'
import { CardSkeleton, EmptyState, ErrorState, PageHeader, Pill } from '@/components/student/StudentUi'
import { formatDate, getErrorMessage } from '@/lib/format'
import { confirmDialog, notify } from '@/lib/notify'

const PAGE_SIZE = 15

const VIDEO_LABEL: Record<string, string> = {
  DONE: 'Có video',
  PROCESSING: 'Đang render video',
  FAILED: 'Render video lỗi',
}

/** Toàn bộ bài giảng của mọi giáo viên (GET /lectures với ADMIN). Backend mới hỗ trợ lọc theo tiêu đề. */
export default function AdminLecturesPage() {
  const [search, setSearch] = useState('')
  const [debounced, setDebounced] = useState('')
  const [page, setPage] = useState(0)
  const queryClient = useQueryClient()

  useEffect(() => {
    const id = setTimeout(() => { setDebounced(search.trim()); setPage(0) }, 300)
    return () => clearTimeout(id)
  }, [search])

  const lectures = useQuery({
    queryKey: ['admin', 'lectures', page, debounced],
    queryFn: () => getLectures({ title: debounced || undefined, page, size: PAGE_SIZE, sort: 'createdAt,desc' }),
    placeholderData: keepPreviousData,
  })

  const publish = useMutation({
    mutationFn: publishLecture,
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ['admin', 'lectures'] }); notify.success('Đã xuất bản bài giảng') },
    onError: e => notify.apiError(e, 'Không xuất bản được bài giảng.'),
  })

  const [migrated, setMigrated] = useState<number | null>(null)
  const migrate = useMutation({
    mutationFn: migrateLectureQuizzes,
    onSuccess: (_d, lectureId) => { setMigrated(lectureId); queryClient.invalidateQueries({ queryKey: ['admin'] }); notify.success('Đã chuyển câu hỏi sang module Bài kiểm tra') },
    onError: e => notify.apiError(e, 'Không chuyển được câu hỏi.'),
  })

  const items = lectures.data?.content ?? []
  const totalPages = lectures.data?.totalPages ?? 0

  // Có bản nháp chưa xuất bản: bài chưa publish, hoặc đã publish nhưng bị sửa sau đó
  const canPublishNow = (l: LectureResponse) =>
    l.status === 'DRAFT' || l.status === 'READY' || (l.status === 'PUBLISHED' && !!l.hasUnpublishedChanges)

  return (
    <div className="mx-auto max-w-6xl space-y-6">
      <PageHeader
        title="Bài giảng"
        description={`Bài giảng của mọi giáo viên${lectures.data ? ` (${lectures.data.totalElements})` : ''}.`}
        action={
          <div className="relative w-full sm:w-72">
            <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" aria-hidden="true" />
            <Input type="search" value={search} onChange={e => setSearch(e.target.value)} placeholder="Tìm theo tiêu đề..." aria-label="Tìm bài giảng" className="h-10 bg-card pl-9" />
          </div>
        }
      />

      {lectures.isError && <ErrorState />}
      {publish.isError && <FormError message={getErrorMessage(publish.error, 'Không xuất bản được bài giảng.')} />}
      {migrate.isError && <FormError message={getErrorMessage(migrate.error, 'Không chuyển được quiz.')} />}
      {migrated != null && !migrate.isError && (
        <p className="rounded-xl bg-emerald-500/10 p-3 text-sm text-emerald-700 dark:text-emerald-400" role="status">
          Đã chạy chuyển quiz cho bài giảng #{migrated}. Xem kết quả ở trang Bài kiểm tra.
        </p>
      )}

      {lectures.isLoading ? (
        <CardSkeleton count={3} className="md:grid-cols-1 xl:grid-cols-1" />
      ) : items.length === 0 ? (
        <EmptyState icon={BookOpen} title={debounced ? 'Không có bài giảng phù hợp' : 'Chưa có bài giảng nào'} />
      ) : (
        <ul className="divide-y divide-border/60 overflow-hidden rounded-2xl border border-border/50 bg-card dark:bg-card/70">
          {items.map(l => (
            <li key={l.lectureId} className="flex flex-col gap-3 p-4 sm:flex-row sm:items-center">
              <div className="min-w-0 flex-1">
                <div className="flex flex-wrap items-center gap-2">
                  <p className="truncate font-semibold text-foreground">{l.title}</p>
                  <LectureStatusBadge status={l.status} />
                  {l.status === 'PUBLISHED' && l.hasUnpublishedChanges && <Pill tone="amber">Có bản sửa chưa xuất bản</Pill>}
                </div>
                <p className="mt-0.5 text-xs text-muted-foreground">
                  #{l.lectureId} · GV: {l.teacherName} · {l.accessScope === 'CLASS' ? 'Lớp học' : 'Riêng tư'} · Tạo {formatDate(l.createdAt)}
                  {VIDEO_LABEL[l.videoStatus] ? ` · ${VIDEO_LABEL[l.videoStatus]}` : ''}
                </p>
              </div>
              <div className="flex flex-wrap gap-2">
                <Link to={`/admin/lectures/${l.lectureId}`} className="flex h-9 items-center gap-1.5 rounded-lg border border-border px-3 text-sm font-medium hover:bg-muted">
                  <Eye size={14} aria-hidden="true" /> Xem
                </Link>
                {l.status !== 'ARCHIVED' && (
                  <Link to={`/admin/lectures/${l.lectureId}/edit`} className="flex h-9 items-center gap-1.5 rounded-lg border border-border px-3 text-sm font-medium hover:bg-muted">
                    <FilePen size={14} aria-hidden="true" /> Sửa
                  </Link>
                )}
                <button
                  disabled={migrate.isPending}
                  onClick={async () => { if (await confirmDialog({ title: 'Chuyển câu hỏi sang module Bài kiểm tra?', description: `Các câu hỏi kiểu cũ của "${l.title}" sẽ được chuyển thành quiz.`, confirmText: 'Chuyển' })) migrate.mutate(l.lectureId) }}
                  className="flex h-9 items-center gap-1.5 rounded-lg border border-border px-3 text-sm font-medium hover:bg-muted disabled:opacity-50"
                  title="Chuyển quiz kiểu cũ (ai_elements) sang module Quiz"
                >
                  <ArrowRightLeft size={14} aria-hidden="true" /> Chuyển quiz
                </button>
                {canPublishNow(l) && (
                  <button
                    disabled={publish.isPending}
                    onClick={async () => { if (await confirmDialog({ title: `Xuất bản "${l.title}"?`, confirmText: 'Xuất bản' })) publish.mutate(l.lectureId) }}
                    className="flex h-9 items-center gap-1.5 rounded-lg bg-primary px-3 text-sm font-medium text-primary-foreground hover:bg-primary/85 disabled:opacity-50"
                  >
                    <Send size={14} aria-hidden="true" /> Xuất bản
                  </button>
                )}
              </div>
            </li>
          ))}
        </ul>
      )}

      {totalPages > 1 && (
        <div className="flex items-center justify-center gap-3">
          <button
            onClick={() => setPage(p => Math.max(0, p - 1))}
            disabled={page === 0}
            className="flex h-10 w-10 items-center justify-center rounded-xl border border-border hover:bg-muted disabled:opacity-40"
            aria-label="Trang trước"
          >
            <ChevronLeft size={18} />
          </button>
          <span className="text-sm tabular-nums text-muted-foreground">Trang {page + 1} / {totalPages}</span>
          <button
            onClick={() => setPage(p => Math.min(totalPages - 1, p + 1))}
            disabled={page >= totalPages - 1}
            className="flex h-10 w-10 items-center justify-center rounded-xl border border-border hover:bg-muted disabled:opacity-40"
            aria-label="Trang sau"
          >
            <ChevronRight size={18} />
          </button>
        </div>
      )}
    </div>
  )
}
