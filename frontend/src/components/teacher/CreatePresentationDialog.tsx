import { useEffect, useRef, useState } from 'react'
import type { FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { AlertTriangle, Loader2, Plus, Sparkles } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import {
  Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle, DialogTrigger,
} from '@/components/ui/dialog'
import { Field, FormError, Select, SubmitButton } from './FormKit'
import { cancelGenerationJob, getGenerationJob } from '@/api/lectureApi'
import { createPresentationFromFile, createPresentationFromLecture } from '@/api/presentationApi'
import { useTeacherLectures } from '@/hooks/useTeacherData'
import { getErrorMessage } from '@/lib/format'
import { PRESENTATION_TEMPLATES } from '@/types/presentation'

const POLL_MS = 3000
const MAX_FILE_SIZE = 20 * 1024 * 1024

type Phase =
  | { kind: 'form' }
  | { kind: 'running'; presentationId: string; jobId: string; progress: number }
  | { kind: 'failed'; presentationId: string; reason: string }

/** Tạo bản trình chiếu bằng AI từ một bài giảng có sẵn hoặc từ file tài liệu. */
export function CreatePresentationDialog() {
  const [open, setOpen] = useState(false)
  const [source, setSource] = useState<'lecture' | 'file'>('lecture')
  const [title, setTitle] = useState('')
  const [lectureId, setLectureId] = useState('')
  const [file, setFile] = useState<File | null>(null)
  const [templateId, setTemplateId] = useState<string>(PRESENTATION_TEMPLATES[0].id)
  const [phase, setPhase] = useState<Phase>({ kind: 'form' })
  const [error, setError] = useState<string | null>(null)
  const lectures = useTeacherLectures()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null)

  // Chỉ bài giảng đã có phiên bản (slide) mới làm nguồn được
  const options = (lectures.data?.content ?? []).filter(l => l.status !== 'ARCHIVED' && l.currentVersionId)

  const start = useMutation({
    mutationFn: async () => {
      if (source === 'lecture') {
        const lecture = options.find(l => String(l.lectureId) === lectureId)
        if (!lecture?.currentVersionId) throw new Error('Chưa chọn bài giảng.')
        return createPresentationFromLecture(lecture.currentVersionId, title.trim(), templateId)
      }
      if (!file) throw new Error('Chưa chọn file.')
      return createPresentationFromFile(file, title.trim(), templateId)
    },
    onSuccess: res => setPhase({ kind: 'running', presentationId: res.presentationId, jobId: res.jobId, progress: 0 }),
    onError: err => setError(err instanceof Error && !('response' in err) ? err.message : getErrorMessage(err, 'Không gửi được yêu cầu.')),
  })

  const cancel = useMutation({
    mutationFn: (jobId: string) => cancelGenerationJob(jobId),
    onSettled: () => { queryClient.invalidateQueries({ queryKey: ['presentations'] }); setPhase({ kind: 'form' }) },
  })

  const jobId = phase.kind === 'running' ? phase.jobId : null
  const presentationId = phase.kind === 'running' ? phase.presentationId : null

  useEffect(() => {
    if (!jobId || !presentationId) return
    let stopped = false
    const tick = async () => {
      try {
        const job = await getGenerationJob(jobId)
        if (stopped) return
        if (job.status === 'DONE') {
          queryClient.invalidateQueries({ queryKey: ['presentations'] })
          setOpen(false)
          navigate(`/teacher/presentations/${presentationId}`)
          return
        }
        if (job.status === 'FAILED' || job.status === 'CANCELLED') {
          queryClient.invalidateQueries({ queryKey: ['presentations'] })
          setPhase({ kind: 'failed', presentationId, reason: job.safeErrorMessage || (job.status === 'CANCELLED' ? 'Đã huỷ.' : 'AI không tạo được slide.') })
          return
        }
        setPhase(p => (p.kind === 'running' ? { ...p, progress: job.progress ?? p.progress } : p))
      } catch {
        // lỗi mạng tạm thời → thử lại ở vòng sau
      }
      timer.current = setTimeout(tick, POLL_MS)
    }
    timer.current = setTimeout(tick, POLL_MS)
    return () => { stopped = true; if (timer.current) clearTimeout(timer.current) }
  }, [jobId, presentationId, navigate, queryClient])

  const handleSubmit = (e: FormEvent) => {
    e.preventDefault()
    setError(null)
    if (source === 'file' && file && file.size > MAX_FILE_SIZE) { setError('File vượt quá 20MB.'); return }
    start.mutate()
  }

  const handleOpenChange = (next: boolean) => {
    if (!next && phase.kind === 'running') return // đang chờ AI: dùng nút Huỷ
    setOpen(next)
    if (next) { setTitle(''); setLectureId(''); setFile(null); setError(null); setPhase({ kind: 'form' }); start.reset() }
  }

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogTrigger asChild>
        <Button className="h-10 rounded-xl px-4"><Plus size={16} aria-hidden="true" /> Tạo bản trình chiếu</Button>
      </DialogTrigger>
      <DialogContent className="sm:max-w-md" showCloseButton={phase.kind !== 'running'}>
        {phase.kind === 'running' ? (
          <div className="space-y-4 py-2 text-center" role="status">
            <Loader2 className="mx-auto h-10 w-10 animate-spin text-primary" aria-hidden="true" />
            <DialogTitle>AI đang soạn slide... {phase.progress > 0 ? `${phase.progress}%` : ''}</DialogTitle>
            <DialogDescription>Có thể mất 1–3 phút khi dịch vụ AI đang bận. Bạn sẽ được chuyển tới trang sửa khi xong.</DialogDescription>
            <div className="mx-auto h-2 max-w-xs overflow-hidden rounded-full bg-muted">
              <div className="h-full rounded-full bg-primary transition-all" style={{ width: `${Math.max(phase.progress, 5)}%` }} />
            </div>
            <Button variant="outline" className="h-10 rounded-xl" disabled={cancel.isPending} onClick={() => cancel.mutate(phase.jobId)}>
              Huỷ
            </Button>
          </div>
        ) : phase.kind === 'failed' ? (
          <div className="space-y-4">
            <DialogHeader>
              <DialogTitle className="flex items-center gap-2"><AlertTriangle size={18} className="text-amber-500" aria-hidden="true" /> Chưa tạo được slide</DialogTitle>
              <DialogDescription>{phase.reason}</DialogDescription>
            </DialogHeader>
            <DialogFooter><Button className="h-10 rounded-xl" onClick={() => setPhase({ kind: 'form' })}>Thử lại</Button></DialogFooter>
          </div>
        ) : (
          <form onSubmit={handleSubmit} className="space-y-4">
            <DialogHeader>
              <DialogTitle>Tạo bản trình chiếu</DialogTitle>
              <DialogDescription>AI soạn bộ slide thuyết trình; bạn sửa lại rồi xuất file PowerPoint.</DialogDescription>
            </DialogHeader>
            <Field label="Tiêu đề *">
              <Input required maxLength={255} value={title} onChange={e => setTitle(e.target.value)} className="h-11" />
            </Field>
            <div className="grid grid-cols-2 gap-2" role="radiogroup" aria-label="Nguồn nội dung">
              {([['lecture', 'Từ bài giảng'], ['file', 'Từ file']] as const).map(([id, label]) => (
                <button
                  key={id}
                  type="button"
                  role="radio"
                  aria-checked={source === id}
                  onClick={() => setSource(id)}
                  className={`h-10 rounded-xl border text-sm font-medium transition-colors ${source === id ? 'border-primary bg-primary/10 text-primary' : 'border-border text-muted-foreground hover:text-foreground'}`}
                >
                  {label}
                </button>
              ))}
            </div>
            {source === 'lecture' ? (
              <Field label="Bài giảng nguồn *">
                <Select required value={lectureId} onChange={e => setLectureId(e.target.value)} disabled={lectures.isLoading}>
                  <option value="">{lectures.isLoading ? 'Đang tải...' : options.length ? '— Chọn bài giảng —' : 'Chưa có bài giảng'}</option>
                  {options.map(l => <option key={l.lectureId} value={l.lectureId}>{l.title}</option>)}
                </Select>
              </Field>
            ) : (
              <Field label="File tài liệu *" hint="PDF, DOCX hoặc PPTX, tối đa 20MB">
                <input
                  required
                  type="file"
                  accept=".pdf,.docx,.pptx"
                  onChange={e => setFile(e.target.files?.[0] ?? null)}
                  className="block w-full text-sm text-muted-foreground file:mr-3 file:h-10 file:cursor-pointer file:rounded-xl file:border-0 file:bg-muted file:px-4 file:text-sm file:font-medium file:text-foreground"
                />
              </Field>
            )}
            <Field label="Mẫu trình bày">
              <Select value={templateId} onChange={e => setTemplateId(e.target.value)}>
                {PRESENTATION_TEMPLATES.map(t => <option key={t.id} value={t.id}>{t.label}</option>)}
              </Select>
            </Field>
            <FormError message={error} />
            <DialogFooter>
              <SubmitButton pending={start.isPending} disabled={!title.trim() || (source === 'lecture' ? !lectureId : !file)}>
                <Sparkles size={15} aria-hidden="true" /> Tạo bằng AI
              </SubmitButton>
            </DialogFooter>
          </form>
        )}
      </DialogContent>
    </Dialog>
  )
}
