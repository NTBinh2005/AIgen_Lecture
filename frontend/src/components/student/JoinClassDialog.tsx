import { useState } from 'react'
import type { FormEvent } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { CheckCircle2, KeyRound, Loader2, Plus } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import {
  Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle, DialogTrigger,
} from '@/components/ui/dialog'
import { selfEnroll } from '@/api/classApi'
import { getErrorMessage } from '@/lib/format'

interface JoinClassDialogProps {
  /** Nút mở dialog to hơn khi dùng trong trạng thái rỗng */
  size?: 'default' | 'lg'
}

/** ENRL-06: Học sinh tự tham gia lớp bằng mã lớp do giáo viên cung cấp. */
export function JoinClassDialog({ size = 'default' }: JoinClassDialogProps) {
  const [open, setOpen] = useState(false)
  const [code, setCode] = useState('')
  const queryClient = useQueryClient()

  const mutation = useMutation({
    mutationFn: (classCode: string) => selfEnroll(classCode),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['student'] })
    },
  })

  const handleSubmit = (e: FormEvent) => {
    e.preventDefault()
    const trimmed = code.trim()
    if (trimmed) mutation.mutate(trimmed)
  }

  const handleOpenChange = (next: boolean) => {
    setOpen(next)
    if (!next) {
      setCode('')
      mutation.reset()
    }
  }

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogTrigger asChild>
        <Button className={size === 'lg' ? 'h-11 rounded-xl px-5' : 'h-10 rounded-xl px-4'}>
          <Plus size={16} aria-hidden="true" />
          Tham gia lớp
        </Button>
      </DialogTrigger>
      <DialogContent className="sm:max-w-md">
        {mutation.isSuccess ? (
          <div className="space-y-4 py-2 text-center">
            <CheckCircle2 className="mx-auto h-12 w-12 text-emerald-500" aria-hidden="true" />
            <div>
              <DialogTitle className="text-lg">Đã tham gia lớp</DialogTitle>
              <DialogDescription className="mt-1">
                Bạn đã vào lớp <span className="font-semibold text-foreground">{mutation.data.className}</span>.
              </DialogDescription>
            </div>
            <Button className="h-10 w-full rounded-xl" onClick={() => handleOpenChange(false)}>Xong</Button>
          </div>
        ) : (
          <form onSubmit={handleSubmit} className="space-y-4">
            <DialogHeader>
              <DialogTitle>Tham gia lớp học</DialogTitle>
              <DialogDescription>Nhập mã lớp giáo viên đã gửi cho bạn. Lớp phải đang hoạt động.</DialogDescription>
            </DialogHeader>
            <div className="relative">
              <KeyRound className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" aria-hidden="true" />
              <Input
                autoFocus
                value={code}
                onChange={e => setCode(e.target.value.toUpperCase())}
                placeholder="VD: FE10476"
                maxLength={20}
                aria-label="Mã lớp"
                className="h-11 pl-9 font-mono tracking-wider"
              />
            </div>
            {mutation.isError && (
              <p className="text-sm text-destructive" role="alert">
                {getErrorMessage(mutation.error, 'Không tham gia được lớp. Kiểm tra lại mã lớp.')}
              </p>
            )}
            <DialogFooter>
              <Button type="submit" className="h-10 w-full rounded-xl sm:w-auto" disabled={!code.trim() || mutation.isPending}>
                {mutation.isPending && <Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" />}
                Tham gia
              </Button>
            </DialogFooter>
          </form>
        )}
      </DialogContent>
    </Dialog>
  )
}
