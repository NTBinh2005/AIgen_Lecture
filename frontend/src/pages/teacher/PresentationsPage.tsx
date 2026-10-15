import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { ArrowRight, Presentation as PresentationIcon, Search } from 'lucide-react'
import { Input } from '@/components/ui/input'
import { getPresentations } from '@/api/presentationApi'
import { CreatePresentationDialog } from '@/components/teacher/CreatePresentationDialog'
import { PresentationStatusPill } from '@/components/teacher/PresentationStatusPill'
import { CardSkeleton, EmptyState, ErrorState, PageHeader } from '@/components/student/StudentUi'
import { formatDate } from '@/lib/format'
import { PRESENTATION_TEMPLATES } from '@/types/presentation'
import { usePagination } from '@/hooks/usePagination'
import { Pagination } from '@/components/common/Pagination'

export default function TeacherPresentationsPage() {
  const [search, setSearch] = useState('')
  const list = useQuery({
    queryKey: ['presentations', 'list'],
    queryFn: () => getPresentations({ size: 100 }),
    // Có bản đang được AI soạn → tự làm mới
    refetchInterval: q => (q.state.data?.content.some(p => p.status === 'GENERATING') ? 5000 : false),
  })

  const items = (list.data?.content ?? [])
    .filter(p => !search.trim() || p.title.toLowerCase().includes(search.trim().toLowerCase()))

  const pager = usePagination(items, 15, search)
  const templateLabel = (id: string) => PRESENTATION_TEMPLATES.find(t => t.id === id)?.label ?? id

  return (
    <div className="mx-auto max-w-6xl space-y-6">
      <PageHeader
        title="Bản trình chiếu"
        description="Bộ slide thuyết trình do AI soạn từ bài giảng hoặc tài liệu, xuất được file PowerPoint."
        action={<CreatePresentationDialog />}
      />

      {(list.data?.content.length ?? 0) > 0 && (
        <div className="relative sm:w-72">
          <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" aria-hidden="true" />
          <Input type="search" value={search} onChange={e => setSearch(e.target.value)} placeholder="Tìm bản trình chiếu..." aria-label="Tìm bản trình chiếu" className="h-10 bg-card pl-9" />
        </div>
      )}

      {list.isError && <ErrorState />}

      {list.isLoading ? (
        <CardSkeleton count={3} />
      ) : items.length === 0 ? (
        <EmptyState
          icon={PresentationIcon}
          title={(list.data?.content.length ?? 0) === 0 ? 'Chưa có bản trình chiếu nào' : 'Không có kết quả phù hợp'}
          action={(list.data?.content.length ?? 0) === 0 ? <CreatePresentationDialog /> : undefined}
          className="py-16"
        />
      ) : (
      <>
        <div className="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-3">
          {pager.pageItems.map(p => (
            <Link
              key={p.presentationId}
              to={`/teacher/presentations/${p.presentationId}`}
              className="group flex flex-col rounded-2xl border border-border/50 bg-card p-5 transition-all hover:border-primary/30 hover:shadow-lg dark:bg-card/70"
            >
              <div className="mb-3 flex items-start justify-between gap-3">
                <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-primary/10 text-primary">
                  <PresentationIcon size={20} aria-hidden="true" />
                </div>
                <PresentationStatusPill status={p.status} />
              </div>
              <h3 className="line-clamp-2 font-bold leading-snug text-foreground group-hover:text-primary">{p.title}</h3>
              <p className="mt-2 text-xs text-muted-foreground">
                Mẫu {templateLabel(p.templateId)} · Phiên bản {p.currentVersionNumber} · {p.currentVersion?.slides.length ?? 0} slide
              </p>
              <p className="text-xs text-muted-foreground">Cập nhật {formatDate(p.updatedAt)}</p>
              <span className="mt-4 flex items-center gap-1 text-sm font-semibold text-primary">
                Mở <ArrowRight size={15} className="transition-transform group-hover:translate-x-0.5" aria-hidden="true" />
              </span>
            </Link>
          ))}
        </div>
        <Pagination page={pager.page} totalPages={pager.totalPages} total={pager.total} pageSize={pager.pageSize} onChange={pager.setPage} label="Phân trang bài trình chiếu" />
      </>
      )}
    </div>
  )
}
