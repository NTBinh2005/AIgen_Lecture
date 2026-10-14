import { useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { ArrowRight, Hash, School, Search, Trash2, User } from 'lucide-react'
import { Input } from '@/components/ui/input'
import { ClassStatusPill } from '@/components/teacher/ClassStatusPill'
import { FormError, Select } from '@/components/teacher/FormKit'
import { CardSkeleton, EmptyState, ErrorState, PageHeader } from '@/components/student/StudentUi'
import { deleteClass } from '@/api/adminApi'
import { adminKeys, useAdminClasses, useAdminEnrollments } from '@/hooks/useAdminData'
import { formatDate, getErrorMessage } from '@/lib/format'
import type { ClassStatus } from '@/types/student'

export default function AdminClassesPage() {
  const classes = useAdminClasses()
  const enrollments = useAdminEnrollments()
  const queryClient = useQueryClient()
  const [search, setSearch] = useState('')
  const [status, setStatus] = useState<ClassStatus | 'all'>('all')

  const remove = useMutation({
    mutationFn: deleteClass,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: adminKeys.all }),
  })

  /** Số học sinh đang học theo lớp */
  const activeCount = useMemo(() => {
    const m = new Map<number, number>()
    ;(enrollments.data ?? []).filter(e => e.status === 'ACTIVE').forEach(e => m.set(e.classId, (m.get(e.classId) ?? 0) + 1))
    return m
  }, [enrollments.data])

  const list = (classes.data ?? [])
    .filter(c => status === 'all' || c.status === status)
    .filter(c => {
      const k = search.trim().toLowerCase()
      return !k || c.className.toLowerCase().includes(k) || c.classCode.toLowerCase().includes(k) || c.teacherName?.toLowerCase().includes(k)
    })
    .sort((a, b) => b.classId - a.classId)

  return (
    <div className="mx-auto max-w-6xl space-y-6">
      <PageHeader title="Lớp học" description="Toàn bộ lớp trong hệ thống. Mở một lớp để quản lý học sinh, bài giảng, buổi học và lịch." />

      <div className="flex flex-col gap-3 sm:flex-row">
        <div className="relative flex-1">
          <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" aria-hidden="true" />
          <Input type="search" value={search} onChange={e => setSearch(e.target.value)} placeholder="Tìm theo tên lớp, mã lớp, giáo viên..." aria-label="Tìm lớp" className="h-10 bg-card pl-9" />
        </div>
        <Select aria-label="Lọc trạng thái" value={status} onChange={e => setStatus(e.target.value as ClassStatus | 'all')} className="h-10 sm:w-48">
          <option value="all">Mọi trạng thái</option>
          <option value="ACTIVE">Đang hoạt động</option>
          <option value="DRAFT">Nháp</option>
          <option value="CLOSED">Đã đóng</option>
          <option value="ARCHIVED">Lưu trữ</option>
        </Select>
      </div>

      {classes.isError && <ErrorState />}
      {remove.isError && <FormError message={getErrorMessage(remove.error)} />}

      {classes.isLoading ? (
        <CardSkeleton count={3} />
      ) : list.length === 0 ? (
        <EmptyState icon={School} title="Không có lớp phù hợp" />
      ) : (
        <div className="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-3">
          {list.map(c => (
            <div key={c.classId} className="flex flex-col rounded-2xl border border-border/50 bg-card p-5 dark:bg-card/70">
              <div className="mb-2 flex items-start justify-between gap-3">
                <h3 className="line-clamp-2 font-bold leading-snug text-foreground">{c.className}</h3>
                <ClassStatusPill status={c.status} />
              </div>
              <dl className="space-y-1.5 text-sm text-muted-foreground">
                <div className="flex items-center gap-2"><User size={14} aria-hidden="true" /><dt className="sr-only">Giáo viên</dt><dd>{c.teacherName}</dd></div>
                <div className="flex items-center gap-2"><Hash size={14} aria-hidden="true" /><dt className="sr-only">Mã lớp</dt><dd className="font-mono">{c.classCode}</dd><span className="text-xs">· #{c.classId}</span></div>
                <div className="text-xs">
                  {activeCount.get(c.classId) ?? 0} học sinh{c.maxStudents ? ` / ${c.maxStudents}` : ''} · Tạo {formatDate(c.createdAt)}
                </div>
              </dl>
              <div className="mt-4 flex gap-2">
                <Link
                  to={`/admin/classes/${c.classId}`}
                  className="flex h-10 flex-1 items-center justify-center gap-1 rounded-xl border border-border text-sm font-medium hover:bg-muted"
                >
                  Quản lý <ArrowRight size={15} aria-hidden="true" />
                </Link>
                <button
                  onClick={() => { if (window.confirm(`Vô hiệu hoá lớp "${c.className}"? Thao tác này dành cho trường hợp tạo nhầm.`)) remove.mutate(c.classId) }}
                  disabled={remove.isPending}
                  className="flex h-10 w-10 items-center justify-center rounded-xl text-muted-foreground hover:bg-destructive/10 hover:text-destructive"
                  aria-label={`Vô hiệu hoá ${c.className}`}
                  title="Vô hiệu hoá lớp"
                >
                  <Trash2 size={16} />
                </button>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}
