import { Pill } from '@/components/student/StudentUi'
import { ROLE_LABEL } from '@/lib/admin'
import type { Role } from '@/store/authStore'
import type { UserStatus } from '@/types/admin'

export function RolePill({ role }: { role: Role }) {
  const tone = role === 'ADMIN' ? 'amber' : role === 'TEACHER' ? 'violet' : 'blue'
  return <Pill tone={tone}>{ROLE_LABEL[role] ?? role}</Pill>
}

export function UserStatusPill({ status }: { status: UserStatus }) {
  return status === 'ACTIVE' ? <Pill tone="emerald">Hoạt động</Pill> : <Pill tone="red">Đã khoá</Pill>
}
