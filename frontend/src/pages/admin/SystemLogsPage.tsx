import { useMemo, useState } from 'react'
import { Activity, ChevronDown } from 'lucide-react'
import { Select } from '@/components/teacher/FormKit'
import { CardSkeleton, EmptyState, ErrorState, PageHeader, Pill } from '@/components/student/StudentUi'
import { useAdminUsers, useAuditLogs } from '@/hooks/useAdminData'
import { AUDIT_ACTION_LABEL } from '@/lib/admin'
import { formatDateTime } from '@/lib/format'
import { cn } from '@/lib/utils'
import { AUDIT_ACTIONS } from '@/types/admin'
import type { AuditAction, AuditLog } from '@/types/admin'

const PAGE_SIZE = 50

function actionTone(a: AuditAction): 'blue' | 'emerald' | 'red' | 'amber' | 'violet' | 'muted' {
  if (a.endsWith('FAILED')) return 'red'
  if (a.startsWith('PAYMENT') || a.startsWith('REFUND')) return 'emerald'
  if (a === 'ROLE_CHANGED' || a.startsWith('PERMISSION')) return 'amber'
  if (a === 'PASSWORD_CHANGE') return 'violet'
  return 'blue'
}

/** Nhật ký hệ thống (audit log) — GET /audit-logs, lọc theo người dùng / hành động / loại tài nguyên. */
export default function SystemLogsPage() {
  const [userId, setUserId] = useState('')
  const [action, setAction] = useState<AuditAction | ''>('')
  const [resourceType, setResourceType] = useState('')
  const [limit, setLimit] = useState(PAGE_SIZE)
  const users = useAdminUsers()
  const logs = useAuditLogs({
    userId: userId ? Number(userId) : undefined,
    action: action || undefined,
    resourceType: resourceType || undefined,
  })

  const userName = useMemo(() => new Map((users.data ?? []).map(u => [u.userId, `${u.name} (${u.email})`])), [users.data])
  const resourceTypes = useMemo(
    () => [...new Set((logs.data ?? []).map(l => l.resourceType).filter(Boolean))] as string[],
    [logs.data],
  )
  const sorted = [...(logs.data ?? [])].sort((a, b) => b.createdAt.localeCompare(a.createdAt))

  return (
    <div className="mx-auto max-w-6xl space-y-6">
      <PageHeader title="Nhật ký hệ thống" description="Đăng nhập, đổi mật khẩu, đổi vai trò, thanh toán... của mọi người dùng." />

      <div className="grid gap-3 sm:grid-cols-3">
        <Select aria-label="Lọc người dùng" value={userId} onChange={e => { setUserId(e.target.value); setLimit(PAGE_SIZE) }} className="h-10">
          <option value="">Mọi người dùng</option>
          {(users.data ?? []).map(u => <option key={u.userId} value={u.userId}>{u.name} — {u.email}</option>)}
        </Select>
        <Select aria-label="Lọc hành động" value={action} onChange={e => { setAction(e.target.value as AuditAction | ''); setLimit(PAGE_SIZE) }} className="h-10">
          <option value="">Mọi hành động</option>
          {AUDIT_ACTIONS.map(a => <option key={a} value={a}>{AUDIT_ACTION_LABEL[a]}</option>)}
        </Select>
        <Select aria-label="Lọc loại tài nguyên" value={resourceType} onChange={e => { setResourceType(e.target.value); setLimit(PAGE_SIZE) }} className="h-10">
          <option value="">Mọi loại tài nguyên</option>
          {resourceTypes.map(t => <option key={t} value={t}>{t}</option>)}
        </Select>
      </div>

      {logs.isError && <ErrorState />}

      {logs.isLoading ? (
        <CardSkeleton count={3} className="md:grid-cols-1 xl:grid-cols-1" />
      ) : sorted.length === 0 ? (
        <EmptyState icon={Activity} title="Không có nhật ký phù hợp" />
      ) : (
        <>
          <p className="text-sm text-muted-foreground">{sorted.length} bản ghi</p>
          <ul className="divide-y divide-border/50 overflow-hidden rounded-2xl border border-border/50 bg-card dark:bg-card/70">
            {sorted.slice(0, limit).map(l => <LogRow key={l.id} log={l} who={l.userId != null ? userName.get(l.userId) ?? `User #${l.userId}` : 'Hệ thống'} />)}
          </ul>
          {sorted.length > limit && (
            <button
              onClick={() => setLimit(n => n + PAGE_SIZE)}
              className="mx-auto flex min-h-10 items-center gap-1 rounded-xl px-4 text-sm font-medium text-primary hover:bg-primary/10"
            >
              Xem thêm <ChevronDown size={15} aria-hidden="true" />
            </button>
          )}
        </>
      )}
    </div>
  )
}

function LogRow({ log, who }: { log: AuditLog; who: string }) {
  const [open, setOpen] = useState(false)
  return (
    <li>
      <button
        onClick={() => setOpen(v => !v)}
        aria-expanded={open}
        className="flex w-full flex-col gap-1 p-4 text-left hover:bg-muted/40 sm:flex-row sm:items-center sm:gap-4"
      >
        <span className="w-36 shrink-0 text-xs tabular-nums text-muted-foreground">{formatDateTime(log.createdAt)}</span>
        <span className="shrink-0"><Pill tone={actionTone(log.action)}>{AUDIT_ACTION_LABEL[log.action] ?? log.action}</Pill></span>
        <span className="min-w-0 flex-1 truncate text-sm text-foreground">{log.metadata || '—'}</span>
        <span className="truncate text-xs text-muted-foreground sm:w-56 sm:text-right">{who}</span>
        <ChevronDown size={15} className={cn('hidden shrink-0 text-muted-foreground transition-transform sm:block', open && 'rotate-180')} aria-hidden="true" />
      </button>
      {open && (
        <dl className="grid gap-x-6 gap-y-1 bg-muted/30 px-4 py-3 text-xs sm:grid-cols-2">
          <div><dt className="inline text-muted-foreground">Tài nguyên: </dt><dd className="inline text-foreground">{log.resourceType ?? '—'} {log.resourceId ? `#${log.resourceId}` : ''}</dd></div>
          <div><dt className="inline text-muted-foreground">IP: </dt><dd className="inline font-mono text-foreground">{log.ipAddress ?? '—'}</dd></div>
          <div className="sm:col-span-2"><dt className="inline text-muted-foreground">Thiết bị: </dt><dd className="inline break-all text-foreground">{log.userAgent ?? '—'}</dd></div>
        </dl>
      )}
    </li>
  )
}
