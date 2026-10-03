/**
 * Thành phần form nhỏ dùng trong các dialog của Teacher.
 */
import type { ReactNode, SelectHTMLAttributes, TextareaHTMLAttributes } from 'react'
import { Loader2 } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { cn } from '@/lib/utils'

export const fieldClass =
  'h-11 w-full rounded-xl border border-input bg-background px-3 text-sm text-foreground outline-none transition placeholder:text-muted-foreground focus:border-primary focus:ring-4 focus:ring-primary/10 disabled:opacity-60 dark:bg-input/30'

interface FieldProps {
  label: string
  hint?: string
  error?: string
  className?: string
  children: ReactNode
}

export function Field({ label, hint, error, className, children }: FieldProps) {
  return (
    <label className={cn('block space-y-1.5', className)}>
      <span className="text-sm font-medium text-foreground">{label}</span>
      {children}
      {error ? (
        <span className="block text-xs text-destructive">{error}</span>
      ) : hint ? (
        <span className="block text-xs text-muted-foreground">{hint}</span>
      ) : null}
    </label>
  )
}

export function Select({ className, ...props }: SelectHTMLAttributes<HTMLSelectElement>) {
  return <select {...props} className={cn(fieldClass, 'pr-8', className)} />
}

export function TextArea({ className, ...props }: TextareaHTMLAttributes<HTMLTextAreaElement>) {
  return <textarea {...props} className={cn(fieldClass, 'h-auto min-h-20 resize-y py-2.5', className)} />
}

export function FormError({ message }: { message?: string | null }) {
  if (!message) return null
  return (
    <p className="rounded-xl border border-destructive/20 bg-destructive/10 px-3 py-2 text-sm text-destructive" role="alert">
      {message}
    </p>
  )
}

interface SubmitButtonProps {
  pending: boolean
  children: ReactNode
  disabled?: boolean
  className?: string
}

export function SubmitButton({ pending, children, disabled, className }: SubmitButtonProps) {
  return (
    <Button type="submit" className={cn('h-10 rounded-xl px-4', className)} disabled={pending || disabled}>
      {pending && <Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" />}
      {children}
    </Button>
  )
}
