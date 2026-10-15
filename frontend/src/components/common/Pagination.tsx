import { ChevronLeft, ChevronRight } from 'lucide-react'
import { cn } from '@/lib/utils'

interface PaginationProps {
  /** Trang hiện tại, tính từ 0 */
  page: number
  totalPages: number
  onChange: (page: number) => void
  /** Có thì hiện "11–20 / 57 mục" */
  total?: number
  pageSize?: number
  disabled?: boolean
  label?: string
  className?: string
}

/** Các số trang cần hiện: luôn có trang đầu, trang cuối và 1 trang mỗi bên trang hiện tại; chỗ trống là '…'. */
function pageList(page: number, totalPages: number): (number | '…')[] {
  if (totalPages <= 7) return Array.from({ length: totalPages }, (_, i) => i)
  const keep = new Set([0, totalPages - 1, page - 1, page, page + 1].filter(p => p >= 0 && p < totalPages))
  if (page <= 2) [1, 2, 3].forEach(p => keep.add(p))
  if (page >= totalPages - 3) [totalPages - 4, totalPages - 3, totalPages - 2].forEach(p => keep.add(p))
  const sorted = [...keep].sort((a, b) => a - b)
  return sorted.flatMap((p, i) => (i > 0 && p - sorted[i - 1] > 1 ? ['…' as const, p] : [p]))
}

const btn = 'flex h-9 min-w-9 items-center justify-center rounded-lg px-2.5 text-sm font-medium tabular-nums transition-colors disabled:pointer-events-none disabled:opacity-40'

/** Thanh phân trang dùng chung (client hoặc server). Ẩn khi chỉ có 1 trang. */
export function Pagination({ page, totalPages, onChange, total, pageSize, disabled, label = 'Phân trang', className }: PaginationProps) {
  if (totalPages <= 1) return null
  const from = total != null && pageSize ? page * pageSize + 1 : null
  const to = total != null && pageSize ? Math.min(total, (page + 1) * pageSize) : null

  return (
    <nav aria-label={label} className={cn('flex flex-col items-center justify-between gap-3 sm:flex-row', className)}>
      <p className="text-sm tabular-nums text-muted-foreground">
        {from != null ? <>Hiển thị <span className="font-medium text-foreground">{from}–{to}</span> / {total}</> : <>Trang {page + 1} / {totalPages}</>}
      </p>
      <div className="flex items-center gap-1">
        <button type="button" className={cn(btn, 'border border-border hover:bg-muted')} disabled={disabled || page === 0} onClick={() => onChange(page - 1)} aria-label="Trang trước">
          <ChevronLeft size={16} aria-hidden="true" />
        </button>
        {pageList(page, totalPages).map((p, i) =>
          p === '…' ? (
            <span key={`gap-${i}`} className="px-1 text-sm text-muted-foreground" aria-hidden="true">…</span>
          ) : (
            <button
              key={p}
              type="button"
              disabled={disabled}
              onClick={() => onChange(p)}
              aria-current={p === page ? 'page' : undefined}
              aria-label={`Trang ${p + 1}`}
              className={cn(btn, p === page ? 'bg-primary text-primary-foreground' : 'hover:bg-muted')}
            >
              {p + 1}
            </button>
          ),
        )}
        <button type="button" className={cn(btn, 'border border-border hover:bg-muted')} disabled={disabled || page + 1 >= totalPages} onClick={() => onChange(page + 1)} aria-label="Trang sau">
          <ChevronRight size={16} aria-hidden="true" />
        </button>
      </div>
    </nav>
  )
}
