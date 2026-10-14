import type { LectureStatus } from '@/api/lectureApi'

const STATUS_META: Record<LectureStatus, { label: string; badge: string; dot: string }> = {
  DRAFT: {
    label: 'Bản nháp',
    badge: 'bg-amber-500/10 text-amber-700 border-amber-500/20 dark:bg-amber-500/15 dark:text-amber-400',
    dot: 'bg-amber-500',
  },
  PROCESSING: {
    label: 'AI đang tạo',
    badge: 'bg-blue-500/10 text-blue-700 border-blue-500/20 dark:bg-blue-500/15 dark:text-blue-400',
    dot: 'bg-blue-500 animate-pulse',
  },
  READY: {
    label: 'Sẵn sàng',
    badge: 'bg-sky-500/10 text-sky-700 border-sky-500/20 dark:bg-sky-500/15 dark:text-sky-400',
    dot: 'bg-sky-500',
  },
  PUBLISHED: {
    label: 'Đã xuất bản',
    badge: 'bg-emerald-500/10 text-emerald-600 border-emerald-500/20 dark:bg-emerald-500/15 dark:text-emerald-400',
    dot: 'bg-emerald-500',
  },
  FAILED: {
    label: 'Lỗi',
    badge: 'bg-destructive/10 text-destructive border-destructive/20 dark:bg-destructive/15',
    dot: 'bg-destructive',
  },
  ARCHIVED: {
    label: 'Đã lưu trữ',
    badge: 'bg-muted text-muted-foreground border-border/50',
    dot: 'bg-muted-foreground',
  },
}

/** Bài giảng có nội dung để xem (không còn đang tạo / lỗi) */
export function isLectureViewable(status: LectureStatus | undefined): boolean {
  return status === 'DRAFT' || status === 'READY' || status === 'PUBLISHED'
}

export function LectureStatusBadge({ status, className = '' }: { status: LectureStatus; className?: string }) {
  const meta = STATUS_META[status] ?? STATUS_META.DRAFT
  return (
    <span className={`inline-flex items-center gap-1.5 px-3 py-1.5 rounded-full text-xs font-semibold border ${meta.badge} ${className}`}>
      <span className={`w-1.5 h-1.5 rounded-full ${meta.dot}`} />
      {meta.label}
    </span>
  )
}
