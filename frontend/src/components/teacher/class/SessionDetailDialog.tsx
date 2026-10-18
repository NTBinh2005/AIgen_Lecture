import { useState } from 'react'
import type { FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { FileText, Loader2, Plus, Presentation, Trash2 } from 'lucide-react'
import { Input } from '@/components/ui/input'
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { FormError, Select, SubmitButton } from '../FormKit'
import {
  addSessionResource, getSessionResources, removeSessionResource,
} from '@/api/teacherLiveApi'
import { formatDateTime, getErrorMessage } from '@/lib/format'
import type { LiveSession } from '@/types/student'

interface SessionDetailDialogProps {
  session: LiveSession | null
  /** Giáo viên được thêm / xoá tài nguyên */
  canManage: boolean
  onClose: () => void
}

/** Tài nguyên đính kèm của một buổi học. (Ghi hình và danh sách người tham gia đã bị backend bỏ.) */
export function SessionDetailDialog({ session, canManage, onClose }: SessionDetailDialogProps) {
  const sessionId = session?.sessionId
  const queryClient = useQueryClient()
  const enabled = sessionId != null
  const base = ['session', sessionId] as const

  const resources = useQuery({ queryKey: [...base, 'resources'], queryFn: () => getSessionResources(sessionId!), enabled })

  const refresh = () => queryClient.invalidateQueries({ queryKey: base })
  const remove = useMutation({ mutationFn: (id: number) => removeSessionResource(sessionId!, id), onSuccess: refresh })

  return (
    <Dialog open={session != null} onOpenChange={open => { if (!open) onClose() }}>
      <DialogContent className="max-h-[90vh] overflow-y-auto sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>{session?.title}</DialogTitle>
          <DialogDescription>{session ? `${formatDateTime(session.startsAt)} · ${session.className}` : ''}</DialogDescription>
        </DialogHeader>

        {/* ── Tài nguyên ── */}
        <section className="space-y-2">
          <h3 className="flex items-center gap-2 text-sm font-semibold text-foreground"><FileText size={15} aria-hidden="true" /> Tài nguyên buổi học</h3>
          {remove.isError && <FormError message={getErrorMessage(remove.error)} />}
          {resources.isLoading ? <Spinner /> : (resources.data ?? []).length === 0 ? (
            <p className="text-sm text-muted-foreground">Chưa có tài nguyên đính kèm.</p>
          ) : (
            <ul className="divide-y divide-border/50 rounded-xl border border-border/50">
              {resources.data!.map(r => (
                <li key={r.id} className="flex items-center gap-3 p-3">
                  {r.resourceType === 'PRESENTATION' ? <Presentation size={16} className="shrink-0 text-primary" aria-hidden="true" /> : <FileText size={16} className="shrink-0 text-primary" aria-hidden="true" />}
                  <div className="min-w-0 flex-1">
                    <p className="truncate text-sm font-medium text-foreground">{r.title || `${r.resourceType} #${r.resourceId}`}</p>
                    <p className="text-xs text-muted-foreground">
                      {r.resourceType === 'PRESENTATION' ? 'Bản trình chiếu' : 'Tài liệu'} #{r.resourceId} · {r.downloadAllowed ? 'Cho phép tải' : 'Chỉ xem'}
                    </p>
                  </div>
                  {canManage && (
                    <button
                      onClick={() => remove.mutate(r.id)}
                      disabled={remove.isPending}
                      className="flex h-9 w-9 items-center justify-center rounded-lg text-muted-foreground hover:bg-destructive/10 hover:text-destructive"
                      aria-label={`Gỡ ${r.title ?? 'tài nguyên'}`}
                    >
                      <Trash2 size={15} />
                    </button>
                  )}
                </li>
              ))}
            </ul>
          )}
          {canManage && sessionId != null && <AddResourceForm sessionId={sessionId} onAdded={refresh} />}
        </section>

      </DialogContent>
    </Dialog>
  )
}

function Spinner() {
  return <div className="flex justify-center py-3"><Loader2 className="h-5 w-5 animate-spin text-muted-foreground" /></div>
}

function AddResourceForm({ sessionId, onAdded }: { sessionId: number; onAdded: () => void }) {
  const [form, setForm] = useState({ type: 'PRESENTATION' as 'PRESENTATION' | 'ASSET', resourceId: '', title: '', download: true })
  const mutation = useMutation({
    mutationFn: () => addSessionResource(sessionId, {
      resourceType: form.type,
      resourceId: Number(form.resourceId),
      title: form.title.trim(),
      downloadAllowed: form.download,
    }),
    onSuccess: () => { setForm(f => ({ ...f, resourceId: '', title: '' })); onAdded() },
  })
  const handleSubmit = (e: FormEvent) => {
    e.preventDefault()
    mutation.mutate()
  }
  return (
    <form onSubmit={handleSubmit} className="space-y-2 rounded-xl bg-muted/40 p-3">
      <div className="grid gap-2 sm:grid-cols-[9rem_6rem_1fr]">
        <Select aria-label="Loại tài nguyên" value={form.type} onChange={e => setForm(f => ({ ...f, type: e.target.value as 'PRESENTATION' | 'ASSET' }))} className="h-10">
          <option value="PRESENTATION">Bản trình chiếu</option>
          <option value="ASSET">Tài liệu</option>
        </Select>
        <Input required type="number" min={1} value={form.resourceId} onChange={e => setForm(f => ({ ...f, resourceId: e.target.value }))} placeholder="Mã số" aria-label="Mã tài nguyên" className="h-10" />
        <Input required maxLength={255} value={form.title} onChange={e => setForm(f => ({ ...f, title: e.target.value }))} placeholder="Tên hiển thị" aria-label="Tên hiển thị" className="h-10" />
      </div>
      <div className="flex items-center justify-between gap-3">
        <label className="flex min-h-9 items-center gap-2 text-sm text-muted-foreground">
          <input type="checkbox" checked={form.download} onChange={e => setForm(f => ({ ...f, download: e.target.checked }))} className="h-4 w-4" />
          Cho phép tải về
        </label>
        <SubmitButton pending={mutation.isPending} className="h-9"><Plus size={14} aria-hidden="true" /> Đính kèm</SubmitButton>
      </div>
      {/* Backend dùng mã số Long cho tài nguyên, trong khi bản trình chiếu / tài liệu dùng UUID */}
      <p className="text-xs text-muted-foreground">Lưu ý: backend hiện chưa kiểm tra mã tài nguyên có tồn tại hay không.</p>
      <FormError message={mutation.isError ? getErrorMessage(mutation.error) : null} />
    </form>
  )
}
