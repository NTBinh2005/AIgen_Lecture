import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ArrowRightLeft, BookOpen, Eye, FilePen, Search, Send } from 'lucide-react'
import { Input } from '@/components/ui/input'
import { getLectures, publishLecture } from '@/api/lectureApi'
import { migrateLectureQuizzes } from '@/api/adminApi'
import type { LectureAccessScope, LectureResponse, LectureStatus, VideoStatus } from '@/api/lectureApi'
import { useAdminUsers } from '@/hooks/useAdminData'
import { LectureStatusBadge } from '@/components/common/LectureStatusBadge'
import { FormError, Select } from '@/components/teacher/FormKit'
import { CardSkeleton, EmptyState, ErrorState, PageHeader, Pill } from '@/components/student/StudentUi'
import { formatDate, getErrorMessage } from '@/lib/format'
import { confirmDialog, notify } from '@/lib/notify'
import { Pagination } from '@/components/common/Pagination'

const PAGE_SIZE = 15

const VIDEO_LABEL: Record<string, string> = {
  DONE: 'Có video',
  PROCESSING: 'Đang render video',
  FAILED: 'Render video lỗi',
}

const STATUS_OPTIONS: [LectureStatus, string][] = [
  ['DRAFT', 'Bản nháp'], ['PROCESSING', 'AI đang tạo'], ['READY', 'Sẵn sàng'],
  ['PUBLISHED', 'Đã xuất bản'], ['FAILED', 'Tạo lỗi'], ['ARCHIVED', 'Lưu trữ'],
]
const VIDEO_OPTIONS: [VideoStatus, string][] = [
  ['NOT_REQUESTED', 'Chưa yêu cầu video'], ['PENDING', 'Chờ render'], ['PROCESSING', 'Đang render'],
  ['DONE', 'Có video'], ['FAILED', 'Render lỗi'],
]

interface Filters { teacherId: string; status: '' | LectureStatus; videoStatus: '' | VideoStatus; accessScope: '' | LectureAccessScope }
const NO_FILTERS: Filters = { teacherId: '', status: '', videoStatus: '', accessScope: '' }

/** Toàn bộ bài giảng của mọi giáo viên (GET /lectures với ADMIN), lọc theo tiêu đề / giáo viên / trạng thái / phạm vi / video. */
export default function AdminLecturesPage() {
  const [search, setSearch] = useState('')
  const [debounced, setDebounced] = useState('')
  const [page, setPage] = useState(0)
  const [filters, setFilters] = useState<Filters>(NO_FILTERS)
  const queryClient = useQueryClient()
  const users = useAdminUsers()
  const teachers = (users.data ?? []).filter(u => u.role === 'TEACHER').sort((a, b) => a.name.localeCompare(b.name, 'vi'))
  const hasFilters = debounced !== '' || Object.values(filters).some(Boolean)
  const setFilter = <K extends keyof Filters>(key: K) => (e: { target: { value: string } }) => {
    setFilters(f => ({ ...f, [key]: e.target.value }))
    setPage(0)
  }

  useEffect(() => {
    const id = setTimeout(() => { setDebounced(search.trim()); setPage(0) }, 300)
    return () => clearTimeout(id)
  }, [search])

  const lectures = useQuery({
    queryKey: ['admin', 'lectures', page, debounced, filters],
    queryFn: () => getLectures({
      title: debounced || undefined,
      teacherId: filters.teacherId ? Number(filters.teacherId) : undefined,
      status: filters.status || undefined,
      videoStatus: filters.videoStatus || undefined,
      accessScope: filters.accessScope || undefined,
      page,
      size: PAGE_SIZE,
      sort: 'createdAt,desc',
    }),
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

      <div className="flex flex-wrap items-center gap-2" role="group" aria-label="Bộ lọc bài giảng">
        <Select value={filters.teacherId} onChange={setFilter('teacherId')} aria-label="Lọc theo giáo viên" className="w-full bg-card sm:w-56">
          <option value="">Mọi giáo viên</option>
          {teachers.map(t => <option key={t.userId} value={t.userId}>{t.name} ({t.email})</option>)}
        </Select>
        <Select value={filters.status} onChange={setFilter('status')} aria-label="Lọc theo trạng thái" className="w-full bg-card sm:w-44">
          <option value="">Mọi trạng thái</option>
          {STATUS_OPTIONS.map(([v, label]) => <option key={v} value={v}>{label}</option>)}
        </Select>
        <Select value={filters.accessScope} onChange={setFilter('accessScope')} aria-label="Lọc theo phạm vi" className="w-full bg-card sm:w-40">
          <option value="">Mọi phạm vi</option>
          <option value="CLASS">Lớp học</option>
          <option value="PRIVATE">Riêng tư</option>
        </Select>
        <Select value={filters.videoStatus} onChange={setFilter('videoStatus')} aria-label="Lọc theo video" className="w-full bg-card sm:w-52">
          <option value="">Mọi trạng thái video</option>
          {VIDEO_OPTIONS.map(([v, label]) => <option key={v} value={v}>{label}</option>)}
        </Select>
        {hasFilters && (
          <button
            type="button"
            onClick={() => { setFilters(NO_FILTERS); setSearch(''); setDebounced(''); setPage(0) }}
            className="h-10 rounded-lg px-3 text-sm font-medium text-primary hover:bg-primary/10"
          >
            Xoá bộ lọc
          </button>
        )}
      </div>

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
        <EmptyState icon={BookOpen} title={hasFilters ? 'Không có bài giảng phù hợp' : 'Chưa có bài giảng nào'} />
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

      <Pagination page={page} totalPages={totalPages} total={lectures.data?.totalElements} pageSize={PAGE_SIZE} onChange={setPage} disabled={lectures.isFetching} label="Phân trang bài giảng" />
    </div>
  )
}
