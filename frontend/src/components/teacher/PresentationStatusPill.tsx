import { Pill } from '@/components/student/StudentUi'
import type { PresentationStatus } from '@/types/presentation'

const STATUS: Record<PresentationStatus, { label: string; tone: 'amber' | 'emerald' | 'muted' | 'blue' | 'red' }> = {
  DRAFT: { label: 'Nháp', tone: 'amber' },
  GENERATING: { label: 'AI đang soạn', tone: 'blue' },
  READY: { label: 'Sẵn sàng', tone: 'blue' },
  PUBLISHED: { label: 'Đã xuất bản', tone: 'emerald' },
  FAILED: { label: 'Tạo thất bại', tone: 'red' },
  ARCHIVED: { label: 'Lưu trữ', tone: 'muted' },
}

export function PresentationStatusPill({ status }: { status: PresentationStatus }) {
  const s = STATUS[status] ?? { label: status, tone: 'muted' as const }
  return <Pill tone={s.tone} pulse={status === 'GENERATING'}>{s.label}</Pill>
}
