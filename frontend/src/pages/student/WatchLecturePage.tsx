import { useEffect, useState, useRef } from 'react'
import { useParams } from 'react-router-dom'
import {
  Loader2, CheckCircle2, XCircle, Clock, Video, ArrowLeft,
  HelpCircle, Send, MessageCircle, Trash2, Reply,
} from 'lucide-react'
import { Button } from '@/components/ui/button'
import {
  getVideoStatus, getQuizzes, submitAnswer, getComments, addComment, deleteComment,
  type VideoStatus, type LectureResponse, type QuizResponse, type SubmitAnswerResponse,
  type CommentResponse,
} from '@/api/lectureApi'
import axiosInstance from '@/api/axiosInstance'
import { useAuthStore } from '@/store/authStore'


// ─── Types ─────────────────────────────────────────────────────────────────────

type PageState = 'loading' | 'polling' | 'ready' | 'failed' | 'not-found'

interface QuizState {
  selected: string | null          // đáp án đang chọn (chưa nộp)
  result: SubmitAnswerResponse | null  // kết quả sau khi nộp
  submitting: boolean
}

// ─── Comment Thread Component ──────────────────────────────────────────────────

function CommentThread({
  lectureId,
}: {
  lectureId: number
}) {
  const { user } = useAuthStore()
  const [comments, setComments] = useState<CommentResponse[]>([])
  const [loadingComments, setLoadingComments] = useState(true)
  const [newComment, setNewComment] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [replyTo, setReplyTo] = useState<{ id: number; name: string } | null>(null)
  const inputRef = useRef<HTMLTextAreaElement>(null)

  const fetchComments = async () => {
    try {
      const data = await getComments(lectureId)
      setComments(data)
    } catch {
      // ignore
    } finally {
      setLoadingComments(false)
    }
  }

  useEffect(() => { fetchComments() }, [lectureId])

  const handleSubmit = async () => {
    if (!newComment.trim() || submitting) return
    setSubmitting(true)
    try {
      await addComment(lectureId, {
        content: newComment.trim(),
        parentCommentId: replyTo?.id ?? null,
      })
      setNewComment('')
      setReplyTo(null)
      await fetchComments()
    } catch {
      // ignore
    } finally {
      setSubmitting(false)
    }
  }

  const handleDelete = async (commentId: number) => {
    if (!window.confirm('Xóa bình luận này?')) return
    try {
      await deleteComment(lectureId, commentId)
      await fetchComments()
    } catch {
      // ignore
    }
  }

  const handleReply = (id: number, name: string) => {
    setReplyTo({ id, name })
    setTimeout(() => inputRef.current?.focus(), 100)
  }

  const roleLabel = (role: string) =>
    role === 'TEACHER' ? 'Giáo viên' : role === 'ADMIN' ? 'Admin' : 'Học sinh'

  const roleBadge = (role: string) => {
    if (role === 'TEACHER') return 'bg-primary/10 text-primary border-primary/20'
    if (role === 'ADMIN') return 'bg-amber-500/10 text-amber-600 border-amber-500/20'
    return 'bg-emerald-500/10 text-emerald-600 border-emerald-500/20'
  }

  const formatTime = (iso: string) => {
    const d = new Date(iso)
    return d.toLocaleString('vi-VN', { day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit' })
  }

  const SingleComment = ({ c, isReply = false }: { c: CommentResponse; isReply?: boolean }) => (
    <div className={`flex gap-3 ${isReply ? 'ml-10 mt-3' : ''}`}>
      <div className={`w-8 h-8 rounded-full flex-shrink-0 flex items-center justify-center text-xs font-bold text-white shadow-sm ${
        c.userRole === 'TEACHER' ? 'bg-primary' : c.userRole === 'ADMIN' ? 'bg-amber-500' : 'bg-emerald-500'
      }`}>
        {c.userName.charAt(0).toUpperCase()}
      </div>
      <div className="flex-1 min-w-0">
        <div className="flex items-center flex-wrap gap-2 mb-1">
          <span className="text-sm font-semibold text-foreground">{c.userName}</span>
          <span className={`text-xs px-2 py-0.5 rounded-full border font-medium ${roleBadge(c.userRole)}`}>
            {roleLabel(c.userRole)}
          </span>
          <span className="text-xs text-muted-foreground">{formatTime(c.createdAt)}</span>
        </div>
        <p className="text-sm text-foreground leading-relaxed whitespace-pre-wrap">{c.content}</p>
        <div className="flex gap-3 mt-1.5">
          {!isReply && (
            <button
              onClick={() => handleReply(c.commentId, c.userName)}
              className="text-xs text-muted-foreground hover:text-primary transition-colors flex items-center gap-1"
            >
              <Reply size={12} /> Trả lời
            </button>
          )}
          {(user?.id === String(c.userId) || user?.role === 'ADMIN') && (
            <button
              onClick={() => handleDelete(c.commentId)}
              className="text-xs text-muted-foreground hover:text-destructive transition-colors flex items-center gap-1"
            >
              <Trash2 size={12} /> Xóa
            </button>
          )}
        </div>
      </div>
    </div>
  )

  return (
    <div className="space-y-5">
      <div className="flex items-center gap-3">
        <div className="w-8 h-8 rounded-lg bg-primary/10 flex items-center justify-center">
          <MessageCircle size={18} className="text-primary" />
        </div>
        <div>
          <h2 className="text-lg font-bold text-foreground">Hỏi đáp & Bình luận</h2>
          <p className="text-xs text-muted-foreground">Đặt câu hỏi hoặc thảo luận về bài giảng.</p>
        </div>
        <span className="ml-auto bg-primary/10 text-primary text-xs font-semibold px-2.5 py-1 rounded-full">
          {comments.reduce((acc, c) => acc + 1 + c.replies.length, 0)} bình luận
        </span>
      </div>

      {/* Input box */}
      <div className="bg-card border border-border/50 rounded-2xl p-4 space-y-3">
        {replyTo && (
          <div className="flex items-center gap-2 text-xs text-primary bg-primary/5 border border-primary/20 rounded-lg px-3 py-1.5">
            <Reply size={12} /> Đang trả lời <strong>{replyTo.name}</strong>
            <button onClick={() => setReplyTo(null)} className="ml-auto text-muted-foreground hover:text-foreground">✕</button>
          </div>
        )}
        <textarea
          ref={inputRef}
          id="comment-input"
          value={newComment}
          onChange={(e) => setNewComment(e.target.value)}
          onKeyDown={(e) => { if (e.key === 'Enter' && (e.ctrlKey || e.metaKey)) handleSubmit() }}
          placeholder={replyTo ? `Trả lời ${replyTo.name}...` : 'Viết bình luận hoặc câu hỏi... (Ctrl+Enter để gửi)'}
          rows={3}
          className="w-full resize-none bg-muted/30 border border-border/40 rounded-xl px-4 py-3 text-sm
            focus:outline-none focus:ring-2 focus:ring-primary/25 focus:border-primary/40 transition-all"
        />
        <div className="flex justify-end">
          <Button
            size="sm"
            disabled={!newComment.trim() || submitting}
            onClick={handleSubmit}
            className="rounded-xl gap-2"
          >
            {submitting ? <Loader2 size={14} className="animate-spin" /> : <Send size={14} />}
            Gửi bình luận
          </Button>
        </div>
      </div>

      {/* Comment list */}
      {loadingComments ? (
        <div className="flex justify-center py-8">
          <Loader2 size={24} className="animate-spin text-primary" />
        </div>
      ) : comments.length === 0 ? (
        <div className="rounded-xl border border-dashed border-border/50 px-6 py-8 text-center text-sm text-muted-foreground">
          Chưa có bình luận nào. Hãy là người đầu tiên đặt câu hỏi!
        </div>
      ) : (
        <div className="space-y-5">
          {comments.map((c) => (
            <div key={c.commentId} className="bg-card border border-border/50 rounded-2xl p-4">
              <SingleComment c={c} />
              {c.replies.map((r) => (
                <SingleComment key={r.commentId} c={r} isReply />
              ))}
            </div>
          ))}
        </div>
      )}
    </div>
  )
}

// ─── Status Banner ─────────────────────────────────────────────────────────────

function StatusBanner({ status }: { status: VideoStatus }) {
  const configs: Record<VideoStatus, { icon: React.ElementType; label: string; cls: string }> = {
    PENDING: {
      icon: Clock,
      label: 'Video đang chờ xử lý...',
      cls: 'bg-yellow-50 border-yellow-200 text-yellow-700',
    },
    PROCESSING: {
      icon: Loader2,
      label: 'Đang tạo video bài giảng, vui lòng chờ...',
      cls: 'bg-blue-50 border-blue-200 text-blue-700',
    },
    DONE: {
      icon: CheckCircle2,
      label: 'Video đã sẵn sàng!',
      cls: 'bg-green-50 border-green-200 text-green-700',
    },
    FAILED: {
      icon: XCircle,
      label: 'Tạo video thất bại. Vui lòng liên hệ giáo viên.',
      cls: 'bg-red-50 border-red-200 text-red-700',
    },
  }
  const { icon: Icon, label, cls } = configs[status]
  return (
    <div className={`flex items-center gap-3 rounded-xl border px-4 py-3 text-sm font-medium ${cls}`}>
      <Icon size={18} className={status === 'PROCESSING' ? 'animate-spin' : ''} />
      <span>{label}</span>
    </div>
  )
}

// ─── Quiz Card ─────────────────────────────────────────────────────────────────

function QuizCard({
  quiz,
  index,
  state,
  onSelect,
  onSubmit,
}: {
  quiz: QuizResponse
  index: number
  state: QuizState
  onSelect: (letter: string) => void
  onSubmit: () => void
}) {
  const letters = ['A', 'B', 'C', 'D']
  const submitted = state.result !== null

  const getOptionStyle = (letter: string) => {
    if (!submitted) {
      return state.selected === letter
        ? 'border-primary bg-primary/10 text-primary'
        : 'border-border/60 hover:border-primary/40 hover:bg-primary/5 text-foreground cursor-pointer'
    }
    // Sau khi nộp
    const isCorrect = state.result!.correctAnswer === letter
    const isChosen = state.result!.submittedAnswer === letter
    if (isCorrect) return 'border-green-500 bg-green-50 text-green-700 font-semibold'
    if (isChosen && !isCorrect) return 'border-red-400 bg-red-50 text-red-600'
    return 'border-border/40 text-muted-foreground opacity-60'
  }

  return (
    <div className="bg-card border border-border/50 rounded-2xl p-5 space-y-4">
      {/* Header */}
      <div className="flex items-start gap-3">
        <div className="w-7 h-7 rounded-lg bg-primary/10 text-primary text-xs font-bold flex items-center justify-center flex-shrink-0 mt-0.5">
          {index + 1}
        </div>
        <p className="text-sm font-semibold text-foreground leading-snug">{quiz.questionText}</p>
      </div>

      {/* Options */}
      <div className="grid grid-cols-1 gap-2">
        {quiz.options.map((opt, i) => {
          const letter = letters[i]
          return (
            <button
              key={i}
              disabled={submitted}
              onClick={() => !submitted && onSelect(letter)}
              className={`flex items-center gap-3 px-4 py-3 rounded-xl border text-sm transition-all duration-200 text-left ${getOptionStyle(letter)}`}
            >
              <span className={`w-6 h-6 rounded-full text-xs font-bold flex-shrink-0 flex items-center justify-center ${
                submitted && state.result!.correctAnswer === letter
                  ? 'bg-green-500 text-white'
                  : submitted && state.result!.submittedAnswer === letter && state.result!.correctAnswer !== letter
                  ? 'bg-red-400 text-white'
                  : state.selected === letter && !submitted
                  ? 'bg-primary text-white'
                  : 'bg-muted text-muted-foreground'
              }`}>
                {letter}
              </span>
              <span>{opt}</span>
              {submitted && state.result!.correctAnswer === letter && (
                <CheckCircle2 size={16} className="ml-auto text-green-500 flex-shrink-0" />
              )}
              {submitted && state.result!.submittedAnswer === letter && state.result!.correctAnswer !== letter && (
                <XCircle size={16} className="ml-auto text-red-400 flex-shrink-0" />
              )}
            </button>
          )
        })}
      </div>

      {/* Result feedback */}
      {submitted && (
        <div className={`flex items-center gap-2 rounded-xl px-4 py-3 text-sm font-medium ${
          state.result!.isCorrect
            ? 'bg-green-50 border border-green-200 text-green-700'
            : 'bg-red-50 border border-red-200 text-red-700'
        }`}>
          {state.result!.isCorrect ? (
            <><CheckCircle2 size={16} /> Chính xác! Bạn đã trả lời đúng.</>
          ) : (
            <><XCircle size={16} /> Chưa đúng. Đáp án đúng là <strong>{state.result!.correctAnswer}</strong>.</>
          )}
          {!state.result!.isFirstAttempt && (
            <span className="ml-auto text-xs opacity-70">(Lần làm lại)</span>
          )}
        </div>
      )}

      {/* Submit button */}
      {!submitted && (
        <Button
          size="sm"
          disabled={!state.selected || state.submitting}
          onClick={onSubmit}
          className="w-full rounded-xl"
        >
          {state.submitting ? (
            <><Loader2 size={14} className="animate-spin mr-2" />Đang nộp...</>
          ) : (
            <><Send size={14} className="mr-2" />Nộp câu trả lời</>
          )}
        </Button>
      )}
    </div>
  )
}

// ─── Main Page ─────────────────────────────────────────────────────────────────

/**
 * Trang xem video bài giảng (Student view).
 *
 * Nhận lectureId từ URL param.
 * - Nếu video DONE → hiển thị <video> player + Quiz bên dưới
 * - Nếu PROCESSING/PENDING → poll mỗi 3 giây và hiển thị loading state
 * - Nếu FAILED → hiển thị thông báo lỗi
 */
export default function WatchLecturePage() {
  const { lectureId } = useParams<{ lectureId: string }>()

  const [pageState, setPageState] = useState<PageState>('loading')
  const [lecture, setLecture] = useState<LectureResponse | null>(null)
  const [videoStatus, setVideoStatus] = useState<VideoStatus>('PENDING')
  const [videoUrl, setVideoUrl] = useState<string | null>(null)
  const pollerRef = useRef<ReturnType<typeof setInterval> | null>(null)
  const videoRef = useRef<HTMLVideoElement>(null)

  // Quiz state
  const [quizzes, setQuizzes] = useState<QuizResponse[]>([])
  const [quizStates, setQuizStates] = useState<Map<number, QuizState>>(new Map())

  // ── Load lecture details ─────────────────────────────────────────────────────

  useEffect(() => {
    if (!lectureId) {
      setPageState('not-found')
      return
    }

    const fetchLecture = async () => {
      try {
        const res = await axiosInstance.get<LectureResponse>(`/lectures/${lectureId}`)
        const data = res.data
        setLecture(data)
        setVideoStatus(data.videoStatus)

        if (data.videoStatus === 'DONE') {
          setVideoUrl(data.videoUrl)
          setPageState('ready')
          loadQuizzes(Number(lectureId))
        } else if (data.videoStatus === 'FAILED') {
          setPageState('failed')
        } else {
          setPageState('polling')
          startPolling(Number(lectureId))
        }
      } catch (err: unknown) {
        const axiosError = err as { response?: { status: number } }
        if (axiosError?.response?.status === 401) {
          // Token hết hạn → axiosInstance interceptor đã handle logout
          return
        }
        if (axiosError?.response?.status === 404) {
          setPageState('not-found')
        } else {
          setPageState('failed')
        }
      }
    }

    fetchLecture()
    return () => stopPolling()
  }, [lectureId])

  // ── Load quizzes ─────────────────────────────────────────────────────────────

  const loadQuizzes = async (id: number) => {
    try {
      const data = await getQuizzes(id)
      setQuizzes(data)
      // Khởi tạo state cho từng câu hỏi
      const stateMap = new Map<number, QuizState>()
      data.forEach(q => {
        stateMap.set(q.elementId, { selected: null, result: null, submitting: false })
      })
      setQuizStates(stateMap)
    } catch {
      // Không crash nếu bài giảng không có quiz
    }
  }

  // ── Polling ──────────────────────────────────────────────────────────────────

  const startPolling = (id: number) => {
    pollerRef.current = setInterval(async () => {
      try {
        const status = await getVideoStatus(id)
        setVideoStatus(status.videoStatus)
        if (status.videoStatus === 'DONE') {
          setVideoUrl(status.videoUrl)
          setPageState('ready')
          stopPolling()
          loadQuizzes(id)
        } else if (status.videoStatus === 'FAILED') {
          setPageState('failed')
          stopPolling()
        }
      } catch {
        // ignore transient errors
      }
    }, 3000)
  }

  const stopPolling = () => {
    if (pollerRef.current) {
      clearInterval(pollerRef.current)
      pollerRef.current = null
    }
  }

  // ── Quiz Handlers ────────────────────────────────────────────────────────────

  const handleSelectAnswer = (elementId: number, letter: string) => {
    setQuizStates(prev => {
      const next = new Map(prev)
      const curr = next.get(elementId) ?? { selected: null, result: null, submitting: false }
      next.set(elementId, { ...curr, selected: letter })
      return next
    })
  }

  const handleSubmitAnswer = async (elementId: number) => {
    const state = quizStates.get(elementId)
    if (!state?.selected || state.result) return

    setQuizStates(prev => {
      const next = new Map(prev)
      next.set(elementId, { ...state, submitting: true })
      return next
    })

    try {
      const startTime = Date.now()
      const result = await submitAnswer({
        elementId,
        submittedAnswer: state.selected,
        timeSpent: Math.round((Date.now() - startTime) / 1000),
      })
      setQuizStates(prev => {
        const next = new Map(prev)
        next.set(elementId, { selected: state.selected, result, submitting: false })
        return next
      })
    } catch {
      setQuizStates(prev => {
        const next = new Map(prev)
        next.set(elementId, { ...state, submitting: false })
        return next
      })
    }
  }

  // ── Render ───────────────────────────────────────────────────────────────────

  if (pageState === 'loading') {
    return (
      <div className="flex items-center justify-center min-h-[50vh]">
        <Loader2 size={32} className="animate-spin text-primary" />
      </div>
    )
  }

  if (pageState === 'not-found') {
    return (
      <div className="max-w-lg mx-auto text-center space-y-4 py-20">
        <Video size={48} className="mx-auto text-muted-foreground" />
        <h1 className="text-xl font-bold text-foreground">Không tìm thấy bài giảng</h1>
        <p className="text-muted-foreground text-sm">Bài giảng này không tồn tại hoặc đã bị xóa.</p>
        <Button variant="outline" onClick={() => history.back()}>
          <ArrowLeft size={16} className="mr-2" /> Quay lại
        </Button>
      </div>
    )
  }

  return (
    <div className="max-w-4xl mx-auto space-y-6 animate-in fade-in slide-in-from-bottom-4 duration-500">

      {/* Back button + title */}
      <div className="flex items-start gap-4">
        <button
          onClick={() => history.back()}
          className="mt-1 p-2 rounded-lg hover:bg-muted transition-colors text-muted-foreground hover:text-foreground"
          aria-label="Quay lại"
        >
          <ArrowLeft size={20} />
        </button>
        <div>
          <h1 className="text-2xl font-bold text-foreground leading-tight">
            {lecture?.title ?? 'Đang tải...'}
          </h1>
          <p className="text-sm text-muted-foreground mt-1">
            Giáo viên: {lecture?.teacherName ?? '—'} · Tạo ngày:{' '}
            {lecture?.createdAt ? new Date(lecture.createdAt).toLocaleDateString('vi-VN') : '—'}
          </p>
        </div>
      </div>

      {/* Status banner (ẩn khi đã DONE và có player) */}
      {videoStatus !== 'DONE' && <StatusBanner status={videoStatus} />}

      {/* Video Player */}
      {pageState === 'ready' && videoUrl ? (
        <div className="rounded-2xl overflow-hidden border border-border/50 bg-black shadow-xl shadow-black/20 animate-in fade-in zoom-in-95 duration-500">
          <video
            ref={videoRef}
            controls
            className="w-full aspect-video"
            src={videoUrl}
            aria-label={`Video bài giảng: ${lecture?.title}`}
          >
            <p className="text-white p-4">
              Trình duyệt không hỗ trợ video HTML5.{' '}
              <a href={videoUrl} className="underline" download>
                Tải video về
              </a>
            </p>
          </video>
        </div>
      ) : pageState === 'polling' ? (
        /* Loading skeleton khi đang render */
        <div className="rounded-2xl bg-muted/30 border border-border/30 aspect-video flex flex-col items-center justify-center gap-6 animate-pulse">
          <div className="w-16 h-16 rounded-2xl bg-primary/10 flex items-center justify-center">
            <Loader2 size={32} className="text-primary animate-spin" />
          </div>
          <div className="text-center space-y-2">
            <p className="text-sm font-medium text-foreground">Remotion đang render video...</p>
            <p className="text-xs text-muted-foreground">
              Quá trình này có thể mất từ 1–5 phút tùy số lượng slides
            </p>
          </div>
          <div className="flex gap-1.5">
            {[0, 1, 2].map((i) => (
              <div
                key={i}
                className="w-2 h-2 rounded-full bg-primary animate-bounce"
                style={{ animationDelay: `${i * 150}ms` }}
              />
            ))}
          </div>
        </div>
      ) : pageState === 'failed' ? (
        <div className="rounded-2xl bg-destructive/5 border border-destructive/20 aspect-video flex flex-col items-center justify-center gap-4">
          <XCircle size={48} className="text-destructive" />
          <p className="text-sm text-destructive font-medium">
            Không thể tạo video cho bài giảng này.
          </p>
        </div>
      ) : null}

      {/* ── Quiz Section ──────────────────────────────────────────────────────── */}
      {pageState === 'ready' && (
        <div className="space-y-5 animate-in fade-in slide-in-from-bottom-4 duration-700 delay-200">
          <div className="flex items-center gap-3">
            <div className="w-8 h-8 rounded-lg bg-primary/10 flex items-center justify-center">
              <HelpCircle size={18} className="text-primary" />
            </div>
            <div>
              <h2 className="text-lg font-bold text-foreground">Bài tập trắc nghiệm</h2>
              <p className="text-xs text-muted-foreground">
                Kiểm tra kiến thức sau khi xem video. Kết quả được lưu lại.
              </p>
            </div>
            {quizzes.length > 0 && (
              <span className="ml-auto bg-primary/10 text-primary text-xs font-semibold px-2.5 py-1 rounded-full">
                {quizzes.length} câu
              </span>
            )}
          </div>

          {quizzes.length === 0 ? (
            <div className="rounded-xl border border-dashed border-border/50 px-6 py-8 text-center text-sm text-muted-foreground">
              Bài giảng này chưa có câu hỏi trắc nghiệm.
            </div>
          ) : (
            <div className="space-y-4">
              {quizzes.map((quiz, idx) => {
                const state = quizStates.get(quiz.elementId) ?? {
                  selected: null, result: null, submitting: false,
                }
                return (
                  <QuizCard
                    key={quiz.elementId}
                    quiz={quiz}
                    index={idx}
                    state={state}
                    onSelect={(letter) => handleSelectAnswer(quiz.elementId, letter)}
                    onSubmit={() => handleSubmitAnswer(quiz.elementId)}
                  />
                )
              })}

              {/* Score summary when all submitted */}
              {quizzes.length > 0 && quizzes.every(q => quizStates.get(q.elementId)?.result) && (
                <div className="rounded-2xl border border-primary/20 bg-primary/5 p-5 text-center space-y-1 animate-in fade-in zoom-in-95 duration-300">
                  <p className="text-sm text-muted-foreground">Kết quả bài làm</p>
                  <p className="text-3xl font-bold text-primary">
                    {quizzes.filter(q => quizStates.get(q.elementId)?.result?.isCorrect).length}
                    <span className="text-muted-foreground text-xl font-normal"> / {quizzes.length}</span>
                  </p>
                  <p className="text-sm text-muted-foreground">câu trả lời đúng</p>
                </div>
              )}
            </div>
          )}
        </div>
      )}

      {/* ── Q&A Comment Section ────────────────────────────────────────────── */}
      {pageState === 'ready' && (
        <div className="animate-in fade-in slide-in-from-bottom-4 duration-700 delay-300">
          <CommentThread lectureId={Number(lectureId)} />
        </div>
      )}
    </div>
  )
}
