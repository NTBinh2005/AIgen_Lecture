import { useState } from 'react'
import type { FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Eye, MonitorPlay, Plus, Trash2 } from 'lucide-react'
import { Button } from '@/components/ui/button'
import {
  Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle, DialogTrigger,
} from '@/components/ui/dialog'
import { Field, FormError, Select, SubmitButton } from '../FormKit'
import { CardSkeleton, EmptyState, ErrorState } from '@/components/student/StudentUi'
import {
  assignPresentationToClass, getClassPresentations, getPresentations, unassignPresentationFromClass,
} from '@/api/presentationApi'
import { formatDate, getErrorMessage } from '@/lib/format'
import { confirmDialog, notify } from '@/lib/notify'
import { usePagination } from '@/hooks/usePagination'
import { Pagination } from '@/components/common/Pagination'

const classPresentationsKey = (classId: number) => ['classes', classId, 'presentations'] as const

/** Tab "Trình chiếu" của lớp: giao bản trình chiếu đã xuất bản, giống tab Bài giảng. */
export function PresentationsTab({ classId }: { classId: number }) {
  const queryClient = useQueryClient()
  const list = useQuery({ queryKey: classPresentationsKey(classId), queryFn: () => getClassPresentations(classId) })
  const remove = useMutation({
    mutationFn: (presentationId: string) => unassignPresentationFromClass(classId, presentationId),
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: classPresentationsKey(classId) }); notify.success('Đã bỏ giao bản trình chiếu') },
    onError: e => notify.apiError(e, 'Không bỏ giao được bản trình chiếu.'),
  })
  const items = list.data ?? []
  const pager = usePagination(items, 15, null)

  if (list.isLoading) return <CardSkeleton count={2} />
  if (list.isError) return <ErrorState message={getErrorMessage(list.error, 'Không tải được bản trình chiếu của lớp.')} />

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between gap-3">
        <p className="text-sm text-muted-foreground">Học sinh của lớp xem được phiên bản đã xuất bản của các bản trình chiếu được giao.</p>
        <AssignPresentationDialog classId={classId} assignedIds={items.map(p => p.presentationId)} />
      </div>

      {items.length === 0 ? (
        <EmptyState icon={MonitorPlay} title="Chưa giao bản trình chiếu nào" description="Chỉ giao được bản trình chiếu đã xuất bản." />
      ) : (
        <>
          <ul className="divide-y divide-border/60 overflow-hidden rounded-2xl border border-border/50 bg-card dark:bg-card/70">
            {pager.pageItems.map(p => (
              <li key={p.presentationId} className="flex items-center gap-3 p-4">
                <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-violet-500/10 text-violet-600 dark:text-violet-400">
                  <MonitorPlay size={18} aria-hidden="true" />
                </div>
                <div className="min-w-0 flex-1">
                  <p className="truncate font-medium text-foreground">{p.title}</p>
                  <p className="text-xs text-muted-foreground">{p.slideCount} slide · Giao ngày {formatDate(p.assignedAt)}</p>
                </div>
                <Link
                  to={`/teacher/presentations/${p.presentationId}`}
                  className="flex h-9 w-9 items-center justify-center rounded-lg text-muted-foreground hover:bg-muted hover:text-foreground"
                  aria-label={`Mở ${p.title}`}
                >
                  <Eye size={16} />
                </Link>
                <button
                  onClick={async () => { if (await confirmDialog(`Bỏ giao "${p.title}" khỏi lớp?`)) remove.mutate(p.presentationId) }}
                  disabled={remove.isPending}
                  className="flex h-9 w-9 items-center justify-center rounded-lg text-muted-foreground hover:bg-destructive/10 hover:text-destructive"
                  aria-label={`Bỏ giao ${p.title}`}
                >
                  <Trash2 size={16} />
                </button>
              </li>
            ))}
          </ul>
          <Pagination page={pager.page} totalPages={pager.totalPages} total={pager.total} pageSize={pager.pageSize} onChange={pager.setPage} label="Phân trang bản trình chiếu của lớp" />
        </>
      )}
    </div>
  )
}

function AssignPresentationDialog({ classId, assignedIds }: { classId: number; assignedIds: string[] }) {
  const [open, setOpen] = useState(false)
  const [presentationId, setPresentationId] = useState('')
  const queryClient = useQueryClient()
  const presentations = useQuery({
    queryKey: ['presentations', 'list', 'assignable'],
    queryFn: () => getPresentations({ size: 100 }),
    enabled: open,
  })

  // Chỉ bản đã xuất bản (có phiên bản xuất bản) và chưa lưu trữ
  const available = (presentations.data?.content ?? [])
    .filter(p => p.publishedVersionId && p.status !== 'ARCHIVED' && !assignedIds.includes(p.presentationId))

  const mutation = useMutation({
    mutationFn: () => assignPresentationToClass(classId, presentationId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: classPresentationsKey(classId) })
      notify.success('Đã giao bản trình chiếu cho lớp')
      setOpen(false)
    },
  })

  const handleSubmit = (e: FormEvent) => {
    e.preventDefault()
    if (presentationId) mutation.mutate()
  }

  return (
    <Dialog open={open} onOpenChange={next => { setOpen(next); if (next) { setPresentationId(''); mutation.reset() } }}>
      <DialogTrigger asChild>
        <Button className="h-10 shrink-0 rounded-xl px-4"><Plus size={16} aria-hidden="true" /> Giao trình chiếu</Button>
      </DialogTrigger>
      <DialogContent className="sm:max-w-md">
        <form onSubmit={handleSubmit} className="space-y-4">
          <DialogHeader>
            <DialogTitle>Giao bản trình chiếu cho lớp</DialogTitle>
            <DialogDescription>Chỉ hiện bản trình chiếu đã xuất bản và chưa được giao cho lớp này.</DialogDescription>
          </DialogHeader>
          <Field label="Bản trình chiếu">
            <Select value={presentationId} onChange={e => setPresentationId(e.target.value)} disabled={presentations.isLoading}>
              <option value="">{presentations.isLoading ? 'Đang tải...' : available.length ? '— Chọn bản trình chiếu —' : 'Không có bản trình chiếu phù hợp'}</option>
              {available.map(p => <option key={p.presentationId} value={p.presentationId}>{p.title}</option>)}
            </Select>
          </Field>
          {!presentations.isLoading && available.length === 0 && (
            <p className="text-sm text-muted-foreground">
              Hãy mở <Link to="/teacher/presentations" className="font-medium text-primary hover:underline">bản trình chiếu</Link> và bấm <b>Lưu & xuất bản</b> trước khi giao.
            </p>
          )}
          <FormError message={mutation.isError ? getErrorMessage(mutation.error) : null} />
          <DialogFooter>
            <SubmitButton pending={mutation.isPending} disabled={!presentationId}>Giao</SubmitButton>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
