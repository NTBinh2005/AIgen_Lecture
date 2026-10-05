import { Pill } from '@/components/student/StudentUi'
import type { ClassStatus } from '@/types/student'

const STATUS: Record<ClassStatus, { label: string; tone: 'emerald' | 'amber' | 'muted' | 'blue' }> = {
  DRAFT: { label: 'Nháp', tone: 'amber' },
  ACTIVE: { label: 'Đang hoạt động', tone: 'emerald' },
  CLOSED: { label: 'Đã đóng', tone: 'muted' },
  ARCHIVED: { label: 'Lưu trữ', tone: 'muted' },
}

export function ClassStatusPill({ status }: { status: ClassStatus }) {
  const s = STATUS[status] ?? { label: status, tone: 'muted' as const }
  return <Pill tone={s.tone}>{s.label}</Pill>
}
