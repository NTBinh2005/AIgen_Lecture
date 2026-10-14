import { useEffect, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Loader2, Search, Trash2, UserPlus } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { FormError } from './FormKit'
import { searchTeachers } from '@/api/teacherClassApi'
import type { CollaboratorUser } from '@/api/lectureApi'
import { getErrorMessage } from '@/lib/format'
import { notify } from '@/lib/notify'
import { useAuthStore } from '@/store/authStore'

interface CollaboratorsDialogProps {
  open: boolean
  onClose: () => void
  /** Tên tài nguyên hiển thị ở tiêu đề */
  resourceTitle: string
  /** Khoá cache riêng cho từng tài nguyên, VD ['lecture', 9] */
  queryKey: readonly unknown[]
  load: () => Promise<CollaboratorUser[]>
  add: (userId: number) => Promise<void>
  remove: (userId: number) => Promise<void>
}

/** Quản lý cộng tác viên (đồng biên tập) của bài giảng / bản trình chiếu: tìm giáo viên theo email để thêm. */
export function CollaboratorsDialog({ open, onClose, resourceTitle, queryKey, load, add, remove }: CollaboratorsDialogProps) {
  const [query, setQuery] = useState('')
  const [debounced, setDebounced] = useState('')
  const me = useAuthStore(s => s.user?.id)
  const queryClient = useQueryClient()
  const key = ['collaborators', ...queryKey]
  const list = useQuery({ queryKey: key, queryFn: load, enabled: open })
  const refresh = () => queryClient.invalidateQueries({ queryKey: key })

  useEffect(() => {
    const t = setTimeout(() => setDebounced(query.trim()), 300)
    return () => clearTimeout(t)
  }, [query])

  const search = useQuery({
    queryKey: ['teachers', 'search', debounced],
    queryFn: () => searchTeachers(debounced),
    enabled: open && debounced.length >= 3,
  })

  const addMutation = useMutation({
    mutationFn: (u: CollaboratorUser) => add(u.userId),
    onSuccess: (_d, u) => { refresh(); notify.success(`Đã thêm ${u.name}`) },
  })
  const removeMutation = useMutation({
    mutationFn: (u: CollaboratorUser) => remove(u.userId),
    onSuccess: (_d, u) => { refresh(); notify.success(`Đã gỡ ${u.name}`) },
  })

  const existing = new Set((list.data ?? []).map(u => u.userId))
  const candidates = (search.data ?? []).filter(u => !existing.has(u.userId) && String(u.userId) !== me)

  return (
    <Dialog open={open} onOpenChange={next => { if (!next) { setQuery(''); addMutation.reset(); removeMutation.reset(); onClose() } }}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>Cộng tác viên</DialogTitle>
          <DialogDescription>
            Giáo viên được thêm sẽ cùng sửa "{resourceTitle}".
          </DialogDescription>
        </DialogHeader>

        <div className="relative">
          <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" aria-hidden="true" />
          <Input
            type="search"
            value={query}
            onChange={e => setQuery(e.target.value)}
            placeholder="Tìm giáo viên theo email (ít nhất 3 ký tự)"
            aria-label="Tìm giáo viên theo email"
            className="h-10 pl-9"
          />
        </div>

        {debounced.length >= 3 && (
          <div className="max-h-48 overflow-y-auto rounded-xl border border-border/50">
            {search.isLoading ? (
              <div className="flex justify-center p-3"><Loader2 className="h-5 w-5 animate-spin text-muted-foreground" /></div>
            ) : search.isError ? (
              <p className="p-3 text-center text-sm text-destructive">{getErrorMessage(search.error, 'Không tìm được giáo viên.')}</p>
            ) : candidates.length === 0 ? (
              <p className="p-3 text-center text-sm text-muted-foreground">Không có giáo viên phù hợp.</p>
            ) : (
              <ul className="divide-y divide-border/50">
                {candidates.map(u => (
                  <li key={u.userId} className="flex items-center justify-between gap-3 p-3 text-sm">
                    <div className="min-w-0">
                      <p className="truncate font-medium text-foreground">{u.name}</p>
                      <p className="truncate text-xs text-muted-foreground">{u.email}</p>
                    </div>
                    <Button variant="outline" className="h-9 shrink-0 rounded-lg" disabled={addMutation.isPending} onClick={() => addMutation.mutate(u)}>
                      <UserPlus size={14} aria-hidden="true" /> Thêm
                    </Button>
                  </li>
                ))}
              </ul>
            )}
          </div>
        )}

        <FormError message={addMutation.isError ? getErrorMessage(addMutation.error) : removeMutation.isError ? getErrorMessage(removeMutation.error) : null} />

        <h3 className="text-sm font-semibold text-foreground">Đang cộng tác</h3>
        {list.isLoading ? (
          <div className="flex justify-center py-6"><Loader2 className="h-5 w-5 animate-spin text-muted-foreground" /></div>
        ) : list.isError ? (
          <FormError message={getErrorMessage(list.error)} />
        ) : (list.data ?? []).length === 0 ? (
          <p className="py-2 text-center text-sm text-muted-foreground">Chưa có cộng tác viên.</p>
        ) : (
          <ul className="divide-y divide-border/50 rounded-xl border border-border/50">
            {list.data!.map(u => (
              <li key={u.userId} className="flex items-center justify-between gap-3 p-3 text-sm">
                <div className="min-w-0">
                  <p className="truncate font-medium text-foreground">{u.name}</p>
                  <p className="truncate text-xs text-muted-foreground">{u.email}</p>
                </div>
                <button
                  onClick={() => removeMutation.mutate(u)}
                  disabled={removeMutation.isPending}
                  className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg text-muted-foreground hover:bg-destructive/10 hover:text-destructive"
                  aria-label={`Gỡ ${u.name}`}
                >
                  <Trash2 size={15} />
                </button>
              </li>
            ))}
          </ul>
        )}
      </DialogContent>
    </Dialog>
  )
}
