import { useState } from 'react'
import type { FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Loader2, Trash2, UserPlus } from 'lucide-react'
import { Input } from '@/components/ui/input'
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { FormError, SubmitButton } from './FormKit'
import { getErrorMessage } from '@/lib/format'

interface CollaboratorsDialogProps {
  open: boolean
  onClose: () => void
  /** Tên tài nguyên hiển thị ở tiêu đề */
  resourceTitle: string
  /** Khoá cache riêng cho từng tài nguyên, VD ['lecture', 9] */
  queryKey: readonly unknown[]
  load: () => Promise<number[]>
  add: (userId: number) => Promise<void>
  remove: (userId: number) => Promise<void>
}

/**
 * Quản lý cộng tác viên (đồng biên tập) của bài giảng / bản trình chiếu.
 * Backend chỉ trả userId và chưa có API tìm giáo viên cho TEACHER nên phải nhập ID.
 */
export function CollaboratorsDialog({ open, onClose, resourceTitle, queryKey, load, add, remove }: CollaboratorsDialogProps) {
  const [value, setValue] = useState('')
  const queryClient = useQueryClient()
  const key = ['collaborators', ...queryKey]
  const list = useQuery({ queryKey: key, queryFn: load, enabled: open })
  const refresh = () => queryClient.invalidateQueries({ queryKey: key })
  const addMutation = useMutation({ mutationFn: add, onSuccess: () => { setValue(''); refresh() } })
  const removeMutation = useMutation({ mutationFn: remove, onSuccess: refresh })

  const handleSubmit = (e: FormEvent) => {
    e.preventDefault()
    const id = Number(value)
    if (Number.isInteger(id) && id > 0) addMutation.mutate(id)
  }

  return (
    <Dialog open={open} onOpenChange={next => { if (!next) { setValue(''); addMutation.reset(); removeMutation.reset(); onClose() } }}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>Cộng tác viên</DialogTitle>
          <DialogDescription>
            Giáo viên được thêm sẽ cùng sửa "{resourceTitle}". Chỉ thêm được tài khoản giáo viên.
          </DialogDescription>
        </DialogHeader>

        <form onSubmit={handleSubmit} className="flex gap-2">
          <Input
            type="number"
            min={1}
            value={value}
            onChange={e => setValue(e.target.value)}
            placeholder="ID giáo viên"
            aria-label="ID giáo viên"
            className="h-10"
          />
          <SubmitButton pending={addMutation.isPending} disabled={!value}>
            <UserPlus size={15} aria-hidden="true" /> Thêm
          </SubmitButton>
        </form>

        <FormError message={addMutation.isError ? getErrorMessage(addMutation.error) : removeMutation.isError ? getErrorMessage(removeMutation.error) : null} />

        {list.isLoading ? (
          <div className="flex justify-center py-6"><Loader2 className="h-5 w-5 animate-spin text-muted-foreground" /></div>
        ) : list.isError ? (
          <FormError message={getErrorMessage(list.error)} />
        ) : (list.data ?? []).length === 0 ? (
          <p className="py-4 text-center text-sm text-muted-foreground">Chưa có cộng tác viên.</p>
        ) : (
          <ul className="divide-y divide-border/50 rounded-xl border border-border/50">
            {list.data!.map(id => (
              <li key={id} className="flex items-center justify-between gap-3 p-3 text-sm">
                <span className="font-medium text-foreground">Giáo viên #{id}</span>
                <button
                  onClick={() => removeMutation.mutate(id)}
                  disabled={removeMutation.isPending}
                  className="flex h-9 w-9 items-center justify-center rounded-lg text-muted-foreground hover:bg-destructive/10 hover:text-destructive"
                  aria-label={`Gỡ giáo viên #${id}`}
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
