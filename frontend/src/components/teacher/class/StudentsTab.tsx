import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Check, CheckCircle2, Copy, Loader2, Search, UserPlus, Users, X, XCircle } from 'lucide-react'
import { Button } from '@/components/ui/button'
import {
  Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle, DialogTrigger,
} from '@/components/ui/dialog'
import { FormError, Select, SubmitButton, fieldClass } from '../FormKit'
import { EmptyState, Pill } from '@/components/student/StudentUi'
import { enrollStudents, searchStudents, updateEnrollmentStatus } from '@/api/teacherClassApi'
import type { StudentSearchResult } from '@/api/teacherClassApi'
import { teacherKeys } from '@/hooks/useTeacherData'
import { formatDate, getErrorMessage } from '@/lib/format'
import type { ClassDetail, Enrollment, EnrollmentStatus } from '@/types/student'
import type { BulkEnrollmentItem } from '@/types/teacher'

const STATUS_OPTIONS: Array<{ value: EnrollmentStatus; label: string }> = [
  { value: 'ACTIVE', label: 'Đang học' },
  { value: 'SUSPENDED', label: 'Tạm ngưng' },
  { value: 'COMPLETED', label: 'Hoàn thành' },
  { value: 'CANCELLED', label: 'Đã hủy' },
]

interface StudentsTabProps {
  classInfo: ClassDetail
  students: Enrollment[]
}

export function StudentsTab({ classInfo, students }: StudentsTabProps) {
  const queryClient = useQueryClient()
  const [copied, setCopied] = useState(false)
  const statusMutation = useMutation({
    mutationFn: ({ studentId, status }: { studentId: number; status: EnrollmentStatus }) =>
      updateEnrollmentStatus(classInfo.classId, studentId, status),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: teacherKeys.students(classInfo.classId) }),
  })

  const copyCode = async () => {
    try {
      await navigator.clipboard.writeText(classInfo.classCode)
      setCopied(true)
      setTimeout(() => setCopied(false), 1500)
    } catch { /* clipboard bị chặn */ }
  }

  const activeCount = students.filter(s => s.status === 'ACTIVE').length

  return (
    <div className="space-y-4">
      {/* Mời học sinh */}
      <div className="flex flex-col gap-3 rounded-2xl border border-primary/20 bg-primary/5 p-4 sm:flex-row sm:items-center">
        <div className="min-w-0 flex-1">
          <p className="text-sm font-semibold text-foreground">Mời học sinh bằng mã lớp</p>
          <p className="text-sm text-muted-foreground">
            {classInfo.status === 'ACTIVE'
              ? 'Gửi mã này để học sinh tự tham gia ở mục "Lớp học của tôi".'
              : 'Lớp cần được kích hoạt thì học sinh mới tham gia được.'}
          </p>
        </div>
        <div className="flex gap-2">
          <button
            onClick={copyCode}
            className="flex h-10 items-center gap-2 rounded-xl border border-border bg-background px-4 font-mono text-sm font-bold tracking-wider hover:bg-muted"
            aria-label="Sao chép mã lớp"
          >
            {classInfo.classCode}
            {copied ? <CheckCircle2 size={15} className="text-emerald-500" /> : <Copy size={15} className="text-muted-foreground" />}
          </button>
          <AddStudentsDialog classId={classInfo.classId} disabled={classInfo.status !== 'ACTIVE'} />
        </div>
      </div>

      <p className="text-sm text-muted-foreground">
        {activeCount} học sinh đang học{classInfo.maxStudents ? ` / tối đa ${classInfo.maxStudents}` : ''}
      </p>

      {statusMutation.isError && <FormError message={getErrorMessage(statusMutation.error)} />}

      {students.length === 0 ? (
        <EmptyState icon={Users} title="Lớp chưa có học sinh" />
      ) : (
        <ul className="divide-y divide-border/60 overflow-hidden rounded-2xl border border-border/50 bg-card dark:bg-card/70">
          {students.map(s => (
            <li key={s.studentId} className="flex flex-col gap-3 p-4 sm:flex-row sm:items-center">
              <div className="flex min-w-0 flex-1 items-center gap-3">
                <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-linear-to-tr from-blue-600 to-indigo-600 text-sm font-bold text-white">
                  {s.studentName.charAt(0).toUpperCase()}
                </div>
                <div className="min-w-0">
                  <p className="truncate font-medium text-foreground">{s.studentName}</p>
                  <p className="text-xs text-muted-foreground">ID {s.studentId} · Tham gia {formatDate(s.enrolledAt)}</p>
                </div>
              </div>
              <Select
                aria-label={`Trạng thái của ${s.studentName}`}
                value={s.status}
                disabled={statusMutation.isPending}
                onChange={e => statusMutation.mutate({ studentId: s.studentId, status: e.target.value as EnrollmentStatus })}
                className="h-10 sm:w-40"
              >
                {STATUS_OPTIONS.map(o => <option key={o.value} value={o.value}>{o.label}</option>)}
              </Select>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}

function AddStudentsDialog({ classId, disabled }: { classId: number; disabled: boolean }) {
  const [open, setOpen] = useState(false)
  const [keyword, setKeyword] = useState('')
  const [debounced, setDebounced] = useState('')
  const [selected, setSelected] = useState<StudentSearchResult[]>([])
  const queryClient = useQueryClient()

  useEffect(() => {
    const id = setTimeout(() => setDebounced(keyword.trim()), 300)
    return () => clearTimeout(id)
  }, [keyword])

  const search = useQuery({
    queryKey: ['teacher', 'student-search', debounced],
    queryFn: () => searchStudents(debounced),
    enabled: open && debounced.length >= 2,
  })

  const mutation = useMutation({
    mutationFn: (ids: number[]) => enrollStudents(classId, ids),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: teacherKeys.students(classId) }),
  })

  const toggle = (s: StudentSearchResult) =>
    setSelected(prev => prev.some(p => p.userId === s.userId) ? prev.filter(p => p.userId !== s.userId) : [...prev, s])

  const handleSubmit = (e: FormEvent) => {
    e.preventDefault()
    if (selected.length) mutation.mutate(selected.map(s => s.userId))
  }

  const reset = () => { setKeyword(''); setDebounced(''); setSelected([]); mutation.reset() }

  return (
    <Dialog open={open} onOpenChange={next => { setOpen(next); if (!next) reset() }}>
      <DialogTrigger asChild>
        <Button className="h-10 rounded-xl px-4" disabled={disabled}>
          <UserPlus size={16} aria-hidden="true" /> Thêm
        </Button>
      </DialogTrigger>
      <DialogContent className="sm:max-w-md">
        {mutation.data ? (
          <BulkResult items={mutation.data} onClose={() => setOpen(false)} />
        ) : (
          <form onSubmit={handleSubmit} className="space-y-4">
            <DialogHeader>
              <DialogTitle>Thêm học sinh</DialogTitle>
              <DialogDescription>Tìm theo email và chọn một hoặc nhiều học sinh.</DialogDescription>
            </DialogHeader>

            <div className="relative">
              <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" aria-hidden="true" />
              <input
                autoFocus
                type="search"
                value={keyword}
                onChange={e => setKeyword(e.target.value)}
                placeholder="Nhập ít nhất 2 ký tự của email..."
                aria-label="Tìm học sinh theo email"
                className={`${fieldClass} pl-9`}
              />
            </div>

            {selected.length > 0 && (
              <div className="flex flex-wrap gap-2">
                {selected.map(s => (
                  <span key={s.userId} className="flex items-center gap-1 rounded-full bg-primary/10 py-1 pl-3 pr-1 text-xs font-medium text-primary">
                    {s.name}
                    <button type="button" onClick={() => toggle(s)} className="flex h-5 w-5 items-center justify-center rounded-full hover:bg-primary/20" aria-label={`Bỏ chọn ${s.name}`}>
                      <X size={12} />
                    </button>
                  </span>
                ))}
              </div>
            )}

            <div className="max-h-64 overflow-y-auto rounded-xl border border-border/50">
              {debounced.length < 2 ? (
                <p className="p-4 text-center text-sm text-muted-foreground">Gõ email để tìm học sinh.</p>
              ) : search.isLoading ? (
                <div className="flex justify-center p-4"><Loader2 className="h-5 w-5 animate-spin text-muted-foreground" /></div>
              ) : search.isError ? (
                <p className="p-4 text-center text-sm text-destructive">{getErrorMessage(search.error, 'Không tìm được học sinh.')}</p>
              ) : (search.data ?? []).length === 0 ? (
                <p className="p-4 text-center text-sm text-muted-foreground">Không có học sinh nào khớp.</p>
              ) : (
                <ul className="divide-y divide-border/50">
                  {search.data!.map(s => {
                    const on = selected.some(p => p.userId === s.userId)
                    return (
                      <li key={s.userId}>
                        <button
                          type="button"
                          onClick={() => toggle(s)}
                          aria-pressed={on}
                          className="flex min-h-12 w-full items-center gap-3 px-3 py-2 text-left hover:bg-muted/60"
                        >
                          <span className={`flex h-5 w-5 shrink-0 items-center justify-center rounded border ${on ? 'border-primary bg-primary text-primary-foreground' : 'border-border'}`}>
                            {on && <Check size={13} />}
                          </span>
                          <span className="min-w-0">
                            <span className="block truncate text-sm font-medium text-foreground">{s.name}</span>
                            <span className="block truncate text-xs text-muted-foreground">{s.email}</span>
                          </span>
                        </button>
                      </li>
                    )
                  })}
                </ul>
              )}
            </div>

            <FormError message={mutation.isError ? getErrorMessage(mutation.error) : null} />
            <DialogFooter>
              <SubmitButton pending={mutation.isPending} disabled={!selected.length}>
                Thêm {selected.length || ''} học sinh
              </SubmitButton>
            </DialogFooter>
          </form>
        )}
      </DialogContent>
    </Dialog>
  )
}

function BulkResult({ items, onClose }: { items: BulkEnrollmentItem[]; onClose: () => void }) {
  const ok = items.filter(i => i.success).length
  return (
    <div className="space-y-4">
      <DialogHeader>
        <DialogTitle>Kết quả thêm học sinh</DialogTitle>
        <DialogDescription>Thành công {ok}/{items.length}.</DialogDescription>
      </DialogHeader>
      <ul className="max-h-64 space-y-2 overflow-y-auto">
        {items.map(i => (
          <li key={i.studentId} className="flex items-start gap-2 text-sm">
            {i.success
              ? <CheckCircle2 size={16} className="mt-0.5 shrink-0 text-emerald-500" aria-label="Thành công" />
              : <XCircle size={16} className="mt-0.5 shrink-0 text-destructive" aria-label="Thất bại" />}
            <span>
              <span className="font-medium">ID {i.studentId}</span>
              {i.enrollment && <span className="text-muted-foreground"> — {i.enrollment.studentName}</span>}
              {i.error && <span className="block text-destructive">{i.error}</span>}
            </span>
          </li>
        ))}
      </ul>
      {ok > 0 && <Pill tone="emerald">Đã cập nhật danh sách lớp</Pill>}
      <DialogFooter>
        <Button className="h-10 rounded-xl" onClick={onClose}>Đóng</Button>
      </DialogFooter>
    </div>
  )
}
