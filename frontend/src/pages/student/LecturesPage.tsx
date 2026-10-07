import { useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { PlaySquare, Search } from 'lucide-react'
import { Input } from '@/components/ui/input'
import { useAllClassLectures, useMyClasses } from '@/hooks/useStudentData'
import { CardSkeleton, EmptyState, ErrorState, PageHeader } from '@/components/student/StudentUi'
import { formatDate } from '@/lib/format'
import { cn } from '@/lib/utils'

/**
 * Bài giảng được giao qua các lớp đã ghi danh.
 * (GET /lectures/student vẫn trả rỗng nên lấy từ GET /classes/{id}/lectures.)
 */
export default function StudentLecturesPage() {
  const [search, setSearch] = useState('')
  const [classFilter, setClassFilter] = useState<number | 'all'>('all')
  const classes = useMyClasses()
  const lectures = useAllClassLectures()

  const filtered = useMemo(() => {
    const keyword = search.trim().toLowerCase()
    return lectures.data.filter(l =>
      (classFilter === 'all' || l.classId === classFilter) &&
      (!keyword || l.title.toLowerCase().includes(keyword)),
    )
  }, [lectures.data, search, classFilter])

  return (
    <div className="mx-auto max-w-6xl space-y-6">
      <PageHeader
        title="Bài giảng"
        description="Bài giảng giáo viên đã giao cho các lớp của bạn."
        action={
          <div className="relative w-full sm:w-72">
            <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" aria-hidden="true" />
            <Input
              type="search"
              placeholder="Tìm bài giảng..."
              aria-label="Tìm bài giảng"
              className="h-10 bg-card pl-9"
              value={search}
              onChange={e => setSearch(e.target.value)}
            />
          </div>
        }
      />

      {/* Lọc theo lớp */}
      {(classes.data?.length ?? 0) > 1 && (
        <div className="-mx-4 flex gap-2 overflow-x-auto px-4 pb-1 scrollbar-none sm:mx-0 sm:flex-wrap sm:px-0">
          {[{ classId: 'all' as const, className: 'Tất cả lớp' }, ...(classes.data ?? [])].map(c => (
            <button
              key={c.classId}
              onClick={() => setClassFilter(c.classId)}
              className={cn(
                'min-h-9 whitespace-nowrap rounded-full border px-4 text-sm font-medium transition-colors',
                classFilter === c.classId
                  ? 'border-primary bg-primary text-primary-foreground'
                  : 'border-border bg-card text-muted-foreground hover:text-foreground',
              )}
            >
              {c.className}
            </button>
          ))}
        </div>
      )}

      {lectures.isError && <ErrorState />}

      {lectures.isLoading ? (
        <CardSkeleton count={6} />
      ) : filtered.length === 0 ? (
        <EmptyState
          icon={PlaySquare}
          title={lectures.data.length === 0 ? 'Chưa có bài giảng nào được giao' : 'Không tìm thấy bài giảng phù hợp'}
          description={lectures.data.length === 0 ? 'Tham gia lớp học để nhận bài giảng từ giáo viên.' : undefined}
        />
      ) : (
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
          {filtered.map(l => (
            <Link
              key={`${l.classId}-${l.lectureId}`}
              to={`/student/lectures/${l.lectureId}`}
              className="group flex flex-col overflow-hidden rounded-2xl border border-border/50 bg-card transition-all hover:border-primary/30 hover:shadow-xl hover:shadow-primary/5 dark:bg-card/70"
            >
              <div className="relative flex aspect-video items-center justify-center overflow-hidden bg-linear-to-br from-primary/20 via-violet-500/15 to-indigo-500/20">
                <div className="flex h-14 w-14 items-center justify-center rounded-2xl border border-border/50 bg-background/70 shadow-lg backdrop-blur-sm transition-transform duration-300 group-hover:scale-110">
                  <PlaySquare size={28} className="ml-0.5 text-primary" aria-hidden="true" />
                </div>
                <span className="absolute bottom-3 left-3 max-w-[80%] truncate rounded-lg bg-black/60 px-2 py-1 text-xs font-medium text-white backdrop-blur-sm">
                  {l.className}
                </span>
              </div>
              <div className="flex flex-1 flex-col p-4">
                <h3 className="line-clamp-2 font-bold leading-snug text-foreground group-hover:text-primary">{l.title}</h3>
                <p className="mt-auto pt-3 text-xs text-muted-foreground">Giao ngày {formatDate(l.assignedAt)}</p>
              </div>
            </Link>
          ))}
        </div>
      )}
    </div>
  )
}
