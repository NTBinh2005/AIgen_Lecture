import { useState } from 'react'
import { AlertTriangle, HelpCircle } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { cn } from '@/lib/utils'
import { useDialogStore } from '@/lib/notify'
import type { DialogRequest } from '@/lib/notify'

/** Gắn một lần ở gốc ứng dụng: hiển thị hộp thoại do confirmDialog() / promptDialog() yêu cầu. */
export function ConfirmHost() {
  const request = useDialogStore(s => s.request)
  const close = useDialogStore(s => s.close)

  const cancel = () => {
    if (!request) return
    if (request.kind === 'confirm') request.resolve(false)
    else request.resolve(null)
    close()
  }

  return (
    <Dialog open={request !== null} onOpenChange={open => { if (!open) cancel() }}>
      {request && <DialogBody key={request.title} request={request} onCancel={cancel} onDone={close} />}
    </Dialog>
  )
}

function DialogBody({ request, onCancel, onDone }: { request: DialogRequest; onCancel: () => void; onDone: () => void }) {
  const isPrompt = request.kind === 'prompt'
  const [value, setValue] = useState(() => (request.kind === 'prompt' ? request.defaultValue ?? '' : ''))
  const blocked = request.kind === 'prompt' && request.required === true && !value.trim()
  const Icon = request.destructive ? AlertTriangle : HelpCircle

  const submit = () => {
    if (blocked) return
    if (request.kind === 'confirm') request.resolve(true)
    else request.resolve(value.trim())
    onDone()
  }

  return (
    <DialogContent
      showCloseButton={false}
      className="gap-5 rounded-2xl p-6 sm:max-w-md"
      {...(request.description ? {} : { 'aria-describedby': undefined })}
    >
      <form onSubmit={e => { e.preventDefault(); submit() }} className="flex flex-col gap-5">
        <DialogHeader className="flex-row items-start gap-4">
          <span className={cn(
            'flex h-10 w-10 shrink-0 items-center justify-center rounded-xl',
            request.destructive ? 'bg-destructive/10 text-destructive' : 'bg-primary/10 text-primary',
          )}>
            <Icon className="h-5 w-5" />
          </span>
          <div className="flex min-w-0 flex-col gap-1.5">
            <DialogTitle className="text-base font-semibold leading-snug">{request.title}</DialogTitle>
            {request.description && (
              <DialogDescription className="text-sm leading-relaxed">{request.description}</DialogDescription>
            )}
          </div>
        </DialogHeader>

        {isPrompt && (
          <label className="flex flex-col gap-1.5 text-sm font-medium">
            {request.label}
            <Input
              autoFocus
              value={value}
              onChange={e => setValue(e.target.value)}
              placeholder={request.placeholder}
              className="h-11 rounded-xl"
            />
          </label>
        )}

        <div className="flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
          <Button type="button" variant="outline" className="h-10 rounded-xl" onClick={onCancel}>
            {request.cancelText ?? 'Huỷ'}
          </Button>
          <Button
            type="submit"
            autoFocus={!isPrompt}
            variant={request.destructive ? 'destructive' : 'default'}
            className="h-10 rounded-xl"
            disabled={blocked}
          >
            {request.confirmText ?? 'Xác nhận'}
          </Button>
        </div>
      </form>
    </DialogContent>
  )
}
