import { useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { AlertTriangle, ArrowLeft, Loader2, Plus, Rocket, Save } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { QuestionEditor } from '@/components/teacher/QuestionEditor'
import { FormError } from '@/components/teacher/FormKit'
import { ErrorState, Pill } from '@/components/student/StudentUi'
import { createQuiz, getQuiz, publishQuiz, updateQuiz } from '@/api/teacherQuizApi'
import { teacherKeys } from '@/hooks/useTeacherData'
import { getErrorMessage } from '@/lib/format'
import { newQuestion, toQuestionForms, toQuestionPayload, validateQuestion } from '@/lib/quiz'
import type { QuestionForm } from '@/lib/quiz'
import type { QuizDetail } from '@/types/teacher'

/** /teacher/quizzes/new và /teacher/quizzes/:quizId */
export default function QuizEditorPage() {
  const { quizId: idParam } = useParams<{ quizId: string }>()
  const quizId = idParam ? Number(idParam) : null
  const existing = useQuery({ queryKey: ['teacher', 'quiz', quizId], queryFn: () => getQuiz(quizId!), enabled: quizId != null })

  if (existing.isError) return <div className="mx-auto max-w-3xl"><ErrorState message="Không tải được quiz." /></div>
  if (quizId != null && !existing.data) {
    return <div className="flex justify-center py-20"><Loader2 className="h-8 w-8 animate-spin text-muted-foreground" /></div>
  }
  // key: khởi tạo lại form khi chuyển giữa các quiz
  return <QuizForm key={quizId ?? 'new'} quiz={existing.data ?? null} />
}

function QuizForm({ quiz }: { quiz: QuizDetail | null }) {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [title, setTitle] = useState(quiz?.title ?? '')
  const [questions, setQuestions] = useState<QuestionForm[]>(() =>
    quiz?.questions.length ? toQuestionForms(quiz.questions) : [newQuestion()])
  const [error, setError] = useState<string | null>(null)
  const [saved, setSaved] = useState(false)

  const editable = !quiz || quiz.status === 'DRAFT' || quiz.status === 'REVIEWED'

  const validate = (): boolean => {
    if (!title.trim()) { setError('Vui lòng nhập tên quiz.'); return false }
    if (questions.length === 0) { setError('Quiz cần ít nhất 1 câu hỏi.'); return false }
    const msg = questions.map(validateQuestion).find(Boolean)
    if (msg) { setError(msg); return false }
    setError(null)
    return true
  }

  const save = useMutation({
    mutationFn: async ({ publish }: { publish: boolean }) => {
      const payload = { title: title.trim(), questions: toQuestionPayload(questions) }
      const result = quiz ? await updateQuiz(quiz.quizId, payload) : await createQuiz(payload)
      if (publish) await publishQuiz(result.quizId)
      return { quizId: result.quizId, publish }
    },
    onSuccess: ({ quizId, publish }) => {
      queryClient.invalidateQueries({ queryKey: teacherKeys.quizzes })
      queryClient.invalidateQueries({ queryKey: ['teacher', 'quiz'] })
      if (publish) navigate('/teacher/quizzes')
      else if (!quiz) navigate(`/teacher/quizzes/${quizId}`, { replace: true })
      else { setSaved(true); setTimeout(() => setSaved(false), 2000) }
    },
  })

  const submit = (publish: boolean) => {
    if (!validate()) return
    if (publish && !window.confirm('Xuất bản quiz? Sau khi xuất bản sẽ không sửa được câu hỏi.')) return
    save.mutate({ publish })
  }

  const update = (i: number, q: QuestionForm) => setQuestions(prev => prev.map((x, j) => (j === i ? q : x)))
  const move = (i: number, dir: -1 | 1) => setQuestions(prev => {
    const next = [...prev]
    ;[next[i], next[i + dir]] = [next[i + dir], next[i]]
    return next
  })

  const totalPoints = questions.reduce((s, q) => s + (Number(q.points) || 0), 0)

  return (
    <div className="mx-auto max-w-3xl space-y-6 pb-24">
      <Link to="/teacher/quizzes" className="inline-flex min-h-10 items-center gap-1.5 text-sm font-medium text-muted-foreground hover:text-foreground">
        <ArrowLeft size={16} aria-hidden="true" /> Bài kiểm tra
      </Link>

      <div className="flex flex-wrap items-center gap-3">
        <h1 className="text-2xl font-bold tracking-tight text-foreground sm:text-3xl">{quiz ? 'Sửa quiz' : 'Tạo quiz mới'}</h1>
        {quiz && <Pill tone={editable ? 'amber' : 'emerald'}>{editable ? 'Nháp' : quiz.status}</Pill>}
      </div>

      {!editable && (
        <p className="flex gap-2 rounded-xl bg-muted p-3 text-sm text-muted-foreground">
          <AlertTriangle size={16} className="mt-0.5 shrink-0" aria-hidden="true" />
          Quiz đã xuất bản nên không sửa được. Hãy tạo quiz mới nếu muốn thay đổi câu hỏi.
        </p>
      )}

      <fieldset disabled={!editable || save.isPending} className="space-y-4">
        <Input
          value={title}
          onChange={e => setTitle(e.target.value)}
          placeholder="Tên quiz, VD: Kiểm tra 15 phút — Chương 1"
          aria-label="Tên quiz"
          className="h-12 text-base font-semibold"
        />

        {questions.map((q, i) => (
          <QuestionEditor
            key={q.key}
            question={q}
            index={i}
            total={questions.length}
            onChange={next => update(i, next)}
            onRemove={() => setQuestions(prev => prev.filter((_, j) => j !== i))}
            onMove={dir => move(i, dir)}
          />
        ))}

        <button
          type="button"
          onClick={() => setQuestions(prev => [...prev, newQuestion()])}
          className="flex w-full items-center justify-center gap-2 rounded-2xl border-2 border-dashed border-border/60 py-3 text-sm font-medium text-muted-foreground transition-all hover:border-primary/40 hover:bg-primary/5 hover:text-primary"
        >
          <Plus size={18} aria-hidden="true" /> Thêm câu hỏi
        </button>
      </fieldset>

      <FormError message={error ?? (save.isError ? getErrorMessage(save.error) : null)} />

      {editable && (
        <div className="fixed inset-x-0 bottom-0 z-20 border-t border-border/50 bg-background/90 backdrop-blur-xl lg:left-72">
          <div className="mx-auto flex max-w-3xl items-center gap-2 p-3 sm:px-4">
            <p className="hidden text-sm text-muted-foreground sm:block">{questions.length} câu · {totalPoints} điểm</p>
            {saved && <p className="text-sm text-emerald-600 dark:text-emerald-400" role="status">Đã lưu</p>}
            <Button variant="outline" className="ml-auto h-10 rounded-xl" disabled={save.isPending} onClick={() => submit(false)}>
              {save.isPending && !save.variables?.publish ? <Loader2 className="h-4 w-4 animate-spin" /> : <Save size={15} aria-hidden="true" />} Lưu nháp
            </Button>
            <Button className="h-10 rounded-xl" disabled={save.isPending} onClick={() => submit(true)}>
              {save.isPending && save.variables?.publish ? <Loader2 className="h-4 w-4 animate-spin" /> : <Rocket size={15} aria-hidden="true" />} Lưu & xuất bản
            </Button>
          </div>
        </div>
      )}
    </div>
  )
}
