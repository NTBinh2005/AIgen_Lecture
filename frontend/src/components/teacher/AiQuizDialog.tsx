import { useEffect, useRef, useState } from 'react'
import type { FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { useQueryClient } from '@tanstack/react-query'
import { AlertTriangle, Loader2, Sparkles } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import {
  Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle, DialogTrigger,
} from '@/components/ui/dialog'
import { Field, FormError, Select, SubmitButton } from './FormKit'
import { generateAiQuiz, getAiQuizJob } from '@/api/teacherQuizApi'
import { teacherKeys, useTeacherLectures } from '@/hooks/useTeacherData'
import { getErrorMessage } from '@/lib/format'

const POLL_MS = 3000
/** Phòng khi job treo: tự dừng chờ sau 2 phút */
const TIMEOUT_MS = 2 * 60 * 1000

type Phase = { kind: 'form' } | { kind: 'running'; quizId: number; startedAt: number } | { kind: 'failed'; quizId: number; reason: string | null }

/** Tạo quiz bằng AI từ nội dung một bài giảng (QUIZ-02). */
export function AiQuizDialog() {
  const [open, setOpen] = useState(false)
  const [title, setTitle] = useState('')
  const [lectureId, setLectureId] = useState('')
  const [phase, setPhase] = useState<Phase>({ kind: 'form' })
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const lectures = useTeacherLectures()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null)

  // Bài giảng có nội dung văn bản để AI đọc
  const options = (lectures.data?.content ?? []).filter(l => l.status !== 'ARCHIVED' && l.originalSource)

  // Poll trạng thái job khi đang chạy
  useEffect(() => {
    if (phase.kind !== 'running') return
    let cancelled = false
    const tick = async () => {
      try {
        const job = await getAiQuizJob(String(phase.quizId))
        if (cancelled) return
        if (job.status === 'DONE') {
          queryClient.invalidateQueries({ queryKey: teacherKeys.quizzes })
          setOpen(false)
          navigate(`/teacher/quizzes/${phase.quizId}`)
          return
        }
        if (job.status === 'FAILED' || Date.now() - phase.startedAt > TIMEOUT_MS) {
          queryClient.invalidateQueries({ queryKey: teacherKeys.quizzes })
          // FAILED: backend trả lý do trong `error`; hết giờ chờ: reason = null
          setPhase({ kind: 'failed', quizId: phase.quizId, reason: job.status === 'FAILED' ? job.error ?? 'AI không tạo được câu hỏi.' : null })
          return
        }
      } catch (err) {
        if (cancelled) return
        setError(getErrorMessage(err))
        setPhase({ kind: 'form' })
        return
      }
      timer.current = setTimeout(tick, POLL_MS)
    }
    timer.current = setTimeout(tick, POLL_MS)
    return () => { cancelled = true; if (timer.current) clearTimeout(timer.current) }
  }, [phase, navigate, queryClient])

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault()
    if (!title.trim() || !lectureId) return
    setError(null)
    setSubmitting(true)
    try {
      const job = await generateAiQuiz(title.trim(), Number(lectureId))
      setPhase({ kind: 'running', quizId: job.quizId, startedAt: Date.now() })
    } catch (err) {
      setError(getErrorMessage(err, 'Không gửi được yêu cầu tạo quiz.'))
    } finally {
      setSubmitting(false)
    }
  }

  const handleOpenChange = (next: boolean) => {
    // Không cho đóng khi đang chờ AI để tránh mất theo dõi
    if (!next && phase.kind === 'running') return
    setOpen(next)
    if (next) { setTitle(''); setLectureId(''); setError(null); setPhase({ kind: 'form' }) }
  }

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogTrigger asChild>
        <Button variant="outline" className="h-10 rounded-xl px-4">
          <Sparkles size={16} className="text-primary" aria-hidden="true" /> Tạo bằng AI
        </Button>
      </DialogTrigger>
      <DialogContent className="sm:max-w-md" showCloseButton={phase.kind !== 'running'}>
        {phase.kind === 'running' ? (
          <div className="space-y-4 py-4 text-center" role="status">
            <Loader2 className="mx-auto h-10 w-10 animate-spin text-primary" aria-hidden="true" />
            <DialogTitle>AI đang soạn câu hỏi...</DialogTitle>
            <DialogDescription>Thường mất dưới một phút. Bạn sẽ được chuyển tới trang sửa quiz khi xong.</DialogDescription>
          </div>
        ) : phase.kind === 'failed' ? (
          <div className="space-y-4">
            <DialogHeader>
              <DialogTitle className="flex items-center gap-2">
                <AlertTriangle size={18} className="text-amber-500" aria-hidden="true" />
                {phase.reason ? 'AI không tạo được câu hỏi' : 'AI chưa tạo xong'}
              </DialogTitle>
              <DialogDescription>
                {phase.reason
                  ? 'Quiz nháp đã được tạo nhưng chưa có câu hỏi — bạn có thể tự soạn hoặc thử lại.'
                  : 'Sau 2 phút AI vẫn chưa sinh xong câu hỏi. Quiz nháp đã được tạo — bạn có thể tự soạn hoặc thử lại sau.'}
              </DialogDescription>
              {phase.reason && (
                <p className="rounded-xl bg-destructive/10 p-3 text-left text-sm text-destructive" role="alert">{phase.reason}</p>
              )}
            </DialogHeader>
            <DialogFooter className="gap-2">
              <Button variant="outline" className="h-10 rounded-xl" onClick={() => setPhase({ kind: 'form' })}>Thử lại</Button>
              <Button className="h-10 rounded-xl" onClick={() => { setOpen(false); navigate(`/teacher/quizzes/${phase.quizId}`) }}>
                Mở quiz nháp
              </Button>
            </DialogFooter>
          </div>
        ) : (
          <form onSubmit={handleSubmit} className="space-y-4">
            <DialogHeader>
              <DialogTitle>Tạo quiz bằng AI</DialogTitle>
              <DialogDescription>AI đọc nội dung bài giảng và soạn câu hỏi. Bạn có thể sửa lại trước khi xuất bản.</DialogDescription>
            </DialogHeader>
            <Field label="Tên quiz *">
              <Input required value={title} onChange={e => setTitle(e.target.value)} className="h-11" placeholder="VD: Ôn tập chương 1" />
            </Field>
            <Field label="Bài giảng nguồn *" hint="Chỉ hiện bài giảng đã có nội dung văn bản">
              <Select required value={lectureId} onChange={e => setLectureId(e.target.value)} disabled={lectures.isLoading}>
                <option value="">{lectures.isLoading ? 'Đang tải...' : options.length ? '— Chọn bài giảng —' : 'Chưa có bài giảng phù hợp'}</option>
                {options.map(l => <option key={l.lectureId} value={l.lectureId}>{l.title}</option>)}
              </Select>
            </Field>
            <FormError message={error} />
            <DialogFooter>
              <SubmitButton pending={submitting} disabled={!title.trim() || !lectureId}>
                <Sparkles size={15} aria-hidden="true" /> Tạo quiz
              </SubmitButton>
            </DialogFooter>
          </form>
        )}
      </DialogContent>
    </Dialog>
  )
}
