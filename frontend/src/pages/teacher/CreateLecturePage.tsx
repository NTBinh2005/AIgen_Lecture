import { useRef, useState } from 'react'
import { isAxiosError } from 'axios'
import { FileUp, Plus, Trash2, Sparkles, Loader2, CheckCircle2, XCircle, Clock, UploadCloud, HelpCircle } from 'lucide-react'
import { Button } from '@/components/ui/button'
import {
  Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle,
} from '@/components/ui/dialog'
import {
  createLecture, getVideoStatus, generateFromFile,
  type VideoStatus, type SlideDto, type QuizDto,
} from '@/api/lectureApi'


// ─── Types ─────────────────────────────────────────────────────────────────────

interface SlideForm {
  id: string
  title: string
  bulletPoints: string[]
  narrationText: string
  imagePrompt?: string
  lessonPhase?: string
  teachingGoal?: string
  teacherAction?: string
  interactionPrompt?: string
}

interface QuizForm {
  id: string
  questionText: string
  options: string[]   // 4 options: ["A. ...", "B. ...", ...]
  correctAnswer: string  // 'A' | 'B' | 'C' | 'D'
}

const MAX_UPLOAD_SIZE_BYTES = 500 * 1024 * 1024
const MIN_QUIZ_QUESTIONS = 1
const MAX_QUIZ_QUESTIONS = 20

type FormStep = 'form' | 'generating' | 'done' | 'failed'

// ─── Helper ────────────────────────────────────────────────────────────────────

function newSlide(): SlideForm {
  return {
    id: crypto.randomUUID(),
    title: '',
    bulletPoints: [''],
    narrationText: '',
  }
}

function newQuiz(): QuizForm {
  return {
    id: crypto.randomUUID(),
    questionText: '',
    options: ['A. ', 'B. ', 'C. ', 'D. '],
    correctAnswer: 'A',
  }
}

// ─── Status Badge ──────────────────────────────────────────────────────────────

function VideoStatusBadge({ status }: { status: VideoStatus }) {
  const map = {
    PENDING: { icon: Clock, label: 'Chờ xử lý', cls: 'text-yellow-600 bg-yellow-50 border-yellow-200' },
    PROCESSING: { icon: Loader2, label: 'Đang tạo video...', cls: 'text-blue-600 bg-blue-50 border-blue-200' },
    DONE: { icon: CheckCircle2, label: 'Video đã sẵn sàng', cls: 'text-green-600 bg-green-50 border-green-200' },
    FAILED: { icon: XCircle, label: 'Tạo video thất bại', cls: 'text-red-600 bg-red-50 border-red-200' },
  }
  const { icon: Icon, label, cls } = map[status]
  return (
    <span className={`inline-flex items-center gap-1.5 px-3 py-1.5 rounded-full text-sm font-medium border ${cls}`}>
      <Icon size={16} className={status === 'PROCESSING' ? 'animate-spin' : ''} />
      {label}
    </span>
  )
}

// ─── Main Page ─────────────────────────────────────────────────────────────────

/**
 * Trang Teacher tạo bài giảng mới.
 * Cho phép điền tiêu đề + nhiều slides + quiz (do AI sinh hoặc tự soạn) → submit → poll video-status.
 */
export default function CreateLecturePage() {

  const [step, setStep] = useState<FormStep>('form')
  const [lectureTitle, setLectureTitle] = useState('')
  const [slides, setSlides] = useState<SlideForm[]>([newSlide()])
  const [quizzes, setQuizzes] = useState<QuizForm[]>([])
  const [videoStatus, setVideoStatus] = useState<VideoStatus>('PENDING')
  const [videoUrl, setVideoUrl] = useState<string | null>(null)
  const [videoProgress, setVideoProgress] = useState(0)
  const [error, setError] = useState<string | null>(null)
  const [pollTimer, setPollTimer] = useState<ReturnType<typeof setInterval> | null>(null)

  // States cho file upload LLM
  const [isGeneratingLLM, setIsGeneratingLLM] = useState(false)
  const [pendingFile, setPendingFile] = useState<File | null>(null)
  const [isQuizCountDialogOpen, setIsQuizCountDialogOpen] = useState(false)
  const [quizQuestionCount, setQuizQuestionCount] = useState('5')
  const [quizCountError, setQuizCountError] = useState<string | null>(null)
  const fileInputRef = useRef<HTMLInputElement>(null)
  const quizSectionRef = useRef<HTMLDivElement>(null)
  const [highlightQuiz, setHighlightQuiz] = useState(false)

  const handleScrollToQuiz = () => {
    quizSectionRef.current?.scrollIntoView({ behavior: 'smooth', block: 'start' })
    setHighlightQuiz(false)
    setTimeout(() => {
      setHighlightQuiz(true)
      setTimeout(() => setHighlightQuiz(false), 1200)
    }, 300)
  }

  // ── Slide CRUD ──────────────────────────────────────────────────────────────

  const addSlide = () => setSlides((prev) => [...prev, newSlide()])

  const removeSlide = (id: string) =>
    setSlides((prev) => prev.length > 1 ? prev.filter((s) => s.id !== id) : prev)

  const updateSlide = (id: string, field: keyof Omit<SlideForm, 'id' | 'bulletPoints'>, value: string) =>
    setSlides((prev) => prev.map((s) => s.id === id ? { ...s, [field]: value } : s))

  const updateBullet = (slideId: string, bulletIdx: number, value: string) =>
    setSlides((prev) => prev.map((s) =>
      s.id === slideId
        ? { ...s, bulletPoints: s.bulletPoints.map((b, i) => i === bulletIdx ? value : b) }
        : s,
    ))

  const addBullet = (slideId: string) =>
    setSlides((prev) => prev.map((s) =>
      s.id === slideId ? { ...s, bulletPoints: [...s.bulletPoints, ''] } : s,
    ))

  const removeBullet = (slideId: string, bulletIdx: number) =>
    setSlides((prev) => prev.map((s) =>
      s.id === slideId && s.bulletPoints.length > 1
        ? { ...s, bulletPoints: s.bulletPoints.filter((_, i) => i !== bulletIdx) }
        : s,
    ))

  // ── Quiz CRUD ───────────────────────────────────────────────────────────────

  const addQuiz = () => setQuizzes((prev) => [...prev, newQuiz()])

  const removeQuiz = (id: string) => setQuizzes((prev) => prev.filter((q) => q.id !== id))

  const updateQuizField = (id: string, field: 'questionText' | 'correctAnswer', value: string) =>
    setQuizzes((prev) => prev.map((q) => q.id === id ? { ...q, [field]: value } : q))

  const updateQuizOption = (quizId: string, optIdx: number, value: string) =>
    setQuizzes((prev) => prev.map((q) =>
      q.id === quizId
        ? { ...q, options: q.options.map((o, i) => i === optIdx ? value : o) }
        : q,
    ))

  // ── Sinh slide + quiz bằng LLM từ File ─────────────────────────────────────

  const handleFileSelected = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    if (!file) return

    // Xóa file đang chọn để có thể upload lại file cũ nếu muốn
    if (fileInputRef.current) {
      fileInputRef.current.value = ''
    }

    if (file.size > MAX_UPLOAD_SIZE_BYTES) {
      setError('File quá lớn. Vui lòng chọn file tối đa 500 MB.')
      return
    }

    setError(null)
    setQuizCountError(null)
    setPendingFile(file)
    setIsQuizCountDialogOpen(true)
  }

  const handleGenerateFromFile = async () => {
    const questionCount = Number(quizQuestionCount)
    if (!pendingFile) return
    if (!Number.isInteger(questionCount)
      || questionCount < MIN_QUIZ_QUESTIONS
      || questionCount > MAX_QUIZ_QUESTIONS) {
      setQuizCountError(`Vui lòng nhập số câu hỏi từ ${MIN_QUIZ_QUESTIONS} đến ${MAX_QUIZ_QUESTIONS}.`)
      return
    }

    const file = pendingFile
    setIsQuizCountDialogOpen(false)
    setPendingFile(null)
    setQuizCountError(null)

    try {
      setIsGeneratingLLM(true)
      setError(null)
      const data = await generateFromFile(file, questionCount)

      // Ánh xạ slides
      if (data.slides && data.slides.length > 0) {
        const newSlideForms: SlideForm[] = data.slides.map(s => ({
          id: crypto.randomUUID(),
          title: s.title || '',
          bulletPoints: s.bulletPoints?.length > 0 ? s.bulletPoints : [''],
          narrationText: s.narrationText || '',
          imagePrompt: s.imagePrompt,
          lessonPhase: s.lessonPhase,
          teachingGoal: s.teachingGoal,
          teacherAction: s.teacherAction,
          interactionPrompt: s.interactionPrompt,
        }))
        setSlides(newSlideForms)
      } else {
        setError('AI không sinh được slide nào từ tài liệu này.')
      }

      // Ánh xạ quizzes (có thể null/empty — không crash)
      if (data.quizzes && data.quizzes.length > 0) {
        const newQuizForms: QuizForm[] = data.quizzes.map(q => ({
          id: crypto.randomUUID(),
          questionText: q.questionText || '',
          options: q.options?.length === 4 ? q.options : ['A. ', 'B. ', 'C. ', 'D. '],
          correctAnswer: q.correctAnswer || 'A',
        }))
        setQuizzes(newQuizForms)
      }
      // Nếu quizzes rỗng/null → giữ nguyên quizzes hiện tại (không reset)

    } catch (err: unknown) {
      const message = isAxiosError<{ message?: string }>(err)
        ? err.response?.data?.message
        : undefined
      setError(message || 'Lỗi khi gọi AI. Vui lòng thử lại.')
    } finally {
      setIsGeneratingLLM(false)
    }
  }

  const handleQuizCountDialogChange = (open: boolean) => {
    setIsQuizCountDialogOpen(open)
    if (!open) {
      setPendingFile(null)
      setQuizCountError(null)
    }
  }

  // ── Poll video status ────────────────────────────────────────────────────────

  const startPolling = (lectureId: number) => {
    const timer = setInterval(async () => {
      try {
        const status = await getVideoStatus(lectureId)
        setVideoStatus(status.videoStatus)
        if (typeof status.progress === 'number') {
          setVideoProgress(Math.max(0, Math.min(1, status.progress)))
        }
        if (status.videoStatus === 'DONE') {
          setVideoUrl(status.videoUrl)
          setStep('done')
          clearInterval(timer)
        } else if (status.videoStatus === 'FAILED') {
          setError(status.errorMessage || 'Không thể render video. Vui lòng thử lại.')
          setStep('failed')
          clearInterval(timer)
        }
      } catch {
        // Bỏ qua lỗi poll tạm thời (network hiccup)
      }
    }, 3000)
    setPollTimer(timer)
  }

  // ── Submit ───────────────────────────────────────────────────────────────────

  const handleSubmit = async () => {
    if (!lectureTitle.trim()) {
      setError('Vui lòng nhập tiêu đề bài giảng.')
      return
    }
    const invalidSlide = slides.findIndex((s) => !s.title.trim() || !s.narrationText.trim())
    if (invalidSlide !== -1) {
      setError(`Slide ${invalidSlide + 1} chưa có tiêu đề hoặc nội dung đọc.`)
      return
    }

    setError(null)
    setStep('generating')
    setVideoStatus('PENDING')
    setVideoProgress(0)

    const slideDtos: SlideDto[] = slides.map((s) => ({
      title: s.title,
      bulletPoints: s.bulletPoints.filter((b) => b.trim()),
      narrationText: s.narrationText,
      imagePrompt: s.imagePrompt,
      lessonPhase: s.lessonPhase,
      teachingGoal: s.teachingGoal,
      teacherAction: s.teacherAction,
      interactionPrompt: s.interactionPrompt,
    }))

    const quizDtos: QuizDto[] = quizzes
      .filter(q => q.questionText.trim())
      .map(q => ({
        questionText: q.questionText,
        options: q.options,
        correctAnswer: q.correctAnswer,
      }))

    try {
      const lecture = await createLecture({
        title: lectureTitle,
        slides: slideDtos,
        quizzes: quizDtos.length > 0 ? quizDtos : undefined,
      })
      setVideoStatus(lecture.videoStatus)
      startPolling(lecture.lectureId)
    } catch {
      setError('Không thể tạo bài giảng. Vui lòng thử lại.')
      setStep('form')
    }
  }

  const handleReset = () => {
    if (pollTimer) clearInterval(pollTimer)
    setStep('form')
    setLectureTitle('')
    setSlides([newSlide()])
    setQuizzes([])
    setVideoStatus('PENDING')
    setVideoProgress(0)
    setVideoUrl(null)
    setError(null)
  }

  // ── Render ───────────────────────────────────────────────────────────────────

  return (
    <div className="max-w-3xl mx-auto space-y-8">

      <Dialog open={isQuizCountDialogOpen} onOpenChange={handleQuizCountDialogChange}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Chọn số câu hỏi</DialogTitle>
            <DialogDescription>
              AI sẽ tạo đúng số câu hỏi trắc nghiệm bạn chọn từ tài liệu này.
            </DialogDescription>
          </DialogHeader>
          <div className="space-y-2 py-2">
            <label htmlFor="quiz-question-count" className="text-sm font-medium text-foreground">
              Số câu hỏi (1–20)
            </label>
            <input
              id="quiz-question-count"
              type="number"
              min={MIN_QUIZ_QUESTIONS}
              max={MAX_QUIZ_QUESTIONS}
              step={1}
              value={quizQuestionCount}
              onChange={(event) => {
                setQuizQuestionCount(event.target.value)
                setQuizCountError(null)
              }}
              onKeyDown={(event) => {
                if (event.key === 'Enter') void handleGenerateFromFile()
              }}
              className="w-full px-3 py-2 rounded-lg border border-border/60 bg-background text-foreground focus:outline-none focus:ring-2 focus:ring-primary/40"
              autoFocus
            />
            {quizCountError && <p className="text-xs text-destructive">{quizCountError}</p>}
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => handleQuizCountDialogChange(false)}>
              Hủy
            </Button>
            <Button onClick={() => void handleGenerateFromFile()}>
              Xác nhận và tạo
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Header */}
      <div className="flex items-center gap-3">
        <div className="w-10 h-10 rounded-xl bg-primary/10 flex items-center justify-center">
          <Sparkles size={20} className="text-primary" />
        </div>
        <div>
          <h1 className="text-2xl font-bold text-foreground">Tạo bài giảng mới</h1>
          <p className="text-sm text-muted-foreground">Điền thông tin slides + quiz → AI render video tự động</p>
        </div>
      </div>

      {/* ── Bước 1: Form ────────────────────────────────────────────────────── */}
      {step === 'form' && (
        <div className="space-y-6 animate-in fade-in slide-in-from-bottom-4 duration-500">

          {/* AI Generator Box */}
          <div className="bg-primary/5 border border-primary/20 rounded-2xl p-6 space-y-4">
            <div className="flex items-start justify-between gap-4">
              <div>
                <h3 className="font-semibold text-primary text-lg flex items-center gap-2">
                  <Sparkles size={18} />
                  Tạo nhanh bằng AI
                </h3>
                <p className="text-sm text-muted-foreground mt-1">Upload tài liệu bài giảng (PDF, DOCX, PPTX) tối đa 500 MB. AI sẽ chuyển tài liệu thành <strong>giáo án có mở bài, giải thích, ví dụ, tương tác và tổng kết</strong>, kèm câu hỏi trắc nghiệm.</p>
              </div>
              <input
                type="file"
                ref={fileInputRef}
                className="hidden"
                accept=".pdf,.docx,.pptx"
                onChange={handleFileSelected}
              />
              <Button
                variant="default"
                className="shrink-0 rounded-xl"
                disabled={isGeneratingLLM}
                onClick={() => fileInputRef.current?.click()}
              >
                {isGeneratingLLM ? (
                  <>
                    <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                    Đang tạo...
                  </>
                ) : (
                  <>
                    <UploadCloud className="mr-2 h-4 w-4" />
                    Upload Tài Liệu
                  </>
                )}
              </Button>
            </div>
            {isGeneratingLLM && (
              <div className="text-xs text-primary/80 animate-pulse font-medium">
                Vui lòng đợi để AI đọc hiểu, thiết kế giáo án và chỉ dẫn cử chỉ cho giáo viên 3D...
              </div>
            )}
          </div>

          {/* Lecture title */}
          <div className="bg-card border border-border/50 rounded-2xl p-6 space-y-3">
            <label className="text-sm font-semibold text-foreground" htmlFor="lecture-title">
              Tiêu đề bài giảng
            </label>
            <input
              id="lecture-title"
              type="text"
              value={lectureTitle}
              onChange={(e) => setLectureTitle(e.target.value)}
              placeholder="VD: Cấu trúc dữ liệu — Cây tìm kiếm nhị phân"
              className="w-full px-4 py-2.5 rounded-xl border border-border/60 bg-background text-foreground placeholder:text-muted-foreground focus:outline-none focus:ring-2 focus:ring-primary/40 transition"
            />
          </div>

          {/* Slides */}
          {slides.map((slide, slideIdx) => (
            <div
              key={slide.id}
              className="bg-card border border-border/50 rounded-2xl p-6 space-y-4 relative animate-in fade-in slide-in-from-bottom-2 duration-300"
            >
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-2">
                  <span className="text-xs font-bold uppercase tracking-wider text-muted-foreground">
                    Cảnh {slideIdx + 1}
                  </span>
                  {slide.lessonPhase && (
                    <span className="text-[10px] font-bold rounded-full bg-primary/10 text-primary px-2 py-1">
                      {slide.lessonPhase}
                    </span>
                  )}
                  {slide.teacherAction && (
                    <span className="text-[10px] font-bold rounded-full bg-sky-500/10 text-sky-600 px-2 py-1">
                      Cử chỉ: {slide.teacherAction}
                    </span>
                  )}
                </div>
                {slides.length > 1 && (
                  <button
                    onClick={() => removeSlide(slide.id)}
                    className="text-muted-foreground hover:text-destructive transition-colors p-1 rounded"
                    aria-label="Xóa slide"
                  >
                    <Trash2 size={16} />
                  </button>
                )}
              </div>

              {/* Slide title */}
              <div className="space-y-1.5">
                <label className="text-xs font-medium text-muted-foreground">Tiêu đề slide</label>
                <input
                  type="text"
                  value={slide.title}
                  onChange={(e) => updateSlide(slide.id, 'title', e.target.value)}
                  placeholder="VD: Giới thiệu về Cây nhị phân"
                  className="w-full px-3 py-2 rounded-lg border border-border/60 bg-background text-sm text-foreground placeholder:text-muted-foreground focus:outline-none focus:ring-2 focus:ring-primary/40 transition"
                />
              </div>

              {/* Bullet points */}
              <div className="space-y-1.5">
                <label className="text-xs font-medium text-muted-foreground">Bullet Points</label>
                <div className="space-y-2">
                  {slide.bulletPoints.map((bullet, bulletIdx) => (
                    <div key={bulletIdx} className="flex gap-2">
                      <span className="mt-2 w-1.5 h-1.5 rounded-full bg-primary flex-shrink-0" />
                      <input
                        type="text"
                        value={bullet}
                        onChange={(e) => updateBullet(slide.id, bulletIdx, e.target.value)}
                        placeholder={`Điểm ${bulletIdx + 1}...`}
                        className="flex-1 px-3 py-2 rounded-lg border border-border/60 bg-background text-sm text-foreground placeholder:text-muted-foreground focus:outline-none focus:ring-2 focus:ring-primary/40 transition"
                      />
                      {slide.bulletPoints.length > 1 && (
                        <button
                          onClick={() => removeBullet(slide.id, bulletIdx)}
                          className="text-muted-foreground hover:text-destructive transition-colors"
                          aria-label="Xóa bullet"
                        >
                          <Trash2 size={14} />
                        </button>
                      )}
                    </div>
                  ))}
                </div>
                <button
                  onClick={() => addBullet(slide.id)}
                  className="text-xs text-primary hover:text-primary/80 font-medium transition-colors mt-1"
                >
                  + Thêm bullet point
                </button>
              </div>

              {/* Narration text */}
              <div className="space-y-1.5">
                <label className="text-xs font-medium text-muted-foreground">
                  Nội dung giọng đọc (narration)
                </label>
                <textarea
                  value={slide.narrationText}
                  onChange={(e) => updateSlide(slide.id, 'narrationText', e.target.value)}
                  placeholder="Văn bản sẽ được chuyển thành giọng đọc cho slide này..."
                  rows={3}
                  className="w-full px-3 py-2 rounded-lg border border-border/60 bg-background text-sm text-foreground placeholder:text-muted-foreground focus:outline-none focus:ring-2 focus:ring-primary/40 transition resize-none"
                />
              </div>
            </div>
          ))}

          {/* Add slide button */}
          <button
            onClick={addSlide}
            className="w-full py-3 rounded-2xl border-2 border-dashed border-border/60 hover:border-primary/40 hover:bg-primary/5 text-muted-foreground hover:text-primary transition-all duration-300 flex items-center justify-center gap-2 text-sm font-medium"
          >
            <Plus size={18} />
            Thêm slide mới
          </button>

          {/* ── Quiz Section ─────────────────────────────────────────────────── */}
          <div className="space-y-4 scroll-mt-24" ref={quizSectionRef}>
            <div className={`flex items-center justify-between p-3 rounded-2xl transition-all duration-700 ${highlightQuiz ? 'bg-chart-1/15 ring-2 ring-chart-1/50 scale-[1.02] shadow-lg' : ''}`}>
              <div>
                <h2 className="text-base font-bold text-foreground flex items-center gap-2">
                  <span className="w-6 h-6 rounded-md bg-chart-1/15 text-chart-1 text-xs flex items-center justify-center font-bold">Q</span>
                  Câu hỏi trắc nghiệm
                </h2>
                <p className="text-xs text-muted-foreground mt-0.5">
                  AI đã tạo sẵn — bạn có thể chỉnh sửa hoặc thêm/xóa trước khi lưu.
                </p>
              </div>
              <span className="text-xs text-muted-foreground bg-muted px-2 py-1 rounded-full">
                {quizzes.length} câu hỏi
              </span>
            </div>

            {quizzes.length === 0 && (
              <div className="rounded-xl border border-dashed border-border/50 px-4 py-6 text-center text-sm text-muted-foreground">
                Chưa có câu hỏi nào. Upload tài liệu để AI tạo tự động, hoặc thêm thủ công.
              </div>
            )}

            {quizzes.map((quiz, quizIdx) => (
              <div
                key={quiz.id}
                className="bg-card border border-border/50 rounded-2xl p-5 space-y-4 animate-in fade-in slide-in-from-bottom-2 duration-300"
              >
                <div className="flex items-center justify-between">
                  <span className="text-xs font-bold uppercase tracking-wider text-muted-foreground">
                    Câu {quizIdx + 1}
                  </span>
                  <button
                    onClick={() => removeQuiz(quiz.id)}
                    className="text-muted-foreground hover:text-destructive transition-colors p-1 rounded"
                    aria-label="Xóa câu hỏi"
                  >
                    <Trash2 size={16} />
                  </button>
                </div>

                {/* Question text */}
                <div className="space-y-1.5">
                  <label className="text-xs font-medium text-muted-foreground">Câu hỏi</label>
                  <input
                    type="text"
                    value={quiz.questionText}
                    onChange={(e) => updateQuizField(quiz.id, 'questionText', e.target.value)}
                    placeholder="Nhập câu hỏi..."
                    className="w-full px-3 py-2 rounded-lg border border-border/60 bg-background text-sm text-foreground placeholder:text-muted-foreground focus:outline-none focus:ring-2 focus:ring-primary/40 transition"
                  />
                </div>

                {/* 4 Options */}
                <div className="space-y-2">
                  <label className="text-xs font-medium text-muted-foreground">4 đáp án</label>
                  {quiz.options.map((opt, optIdx) => {
                    const letter = ['A', 'B', 'C', 'D'][optIdx]
                    const isCorrect = quiz.correctAnswer === letter
                    return (
                      <div key={optIdx} className="flex items-center gap-2">
                        <button
                          type="button"
                          onClick={() => updateQuizField(quiz.id, 'correctAnswer', letter)}
                          title={`Đặt ${letter} là đáp án đúng`}
                          className={`w-7 h-7 rounded-full text-xs font-bold flex-shrink-0 transition-all ${
                            isCorrect
                              ? 'bg-green-500 text-white ring-2 ring-green-400 ring-offset-1'
                              : 'bg-muted text-muted-foreground hover:bg-muted/80'
                          }`}
                        >
                          {letter}
                        </button>
                        <input
                          type="text"
                          value={opt}
                          onChange={(e) => updateQuizOption(quiz.id, optIdx, e.target.value)}
                          placeholder={`Đáp án ${letter}...`}
                          className={`flex-1 px-3 py-2 rounded-lg border text-sm text-foreground placeholder:text-muted-foreground focus:outline-none focus:ring-2 focus:ring-primary/40 transition bg-background ${
                            isCorrect ? 'border-green-400/60' : 'border-border/60'
                          }`}
                        />
                      </div>
                    )
                  })}
                  <p className="text-xs text-muted-foreground pt-1">
                    💡 Bấm vào nút chữ cái để chọn đáp án đúng (đang chọn: <strong className="text-green-600">{quiz.correctAnswer}</strong>)
                  </p>
                </div>
              </div>
            ))}

            {/* Add quiz button */}
            <button
              onClick={addQuiz}
              className="w-full py-3 rounded-2xl border-2 border-dashed border-border/60 hover:border-chart-1/40 hover:bg-chart-1/5 text-muted-foreground hover:text-chart-1 transition-all duration-300 flex items-center justify-center gap-2 text-sm font-medium"
            >
              <Plus size={18} />
              Thêm câu hỏi trắc nghiệm
            </button>
          </div>

          {/* Error */}
          {error && (
            <p className="text-sm text-destructive bg-destructive/10 border border-destructive/20 rounded-xl px-4 py-3">
              {error}
            </p>
          )}

          {/* Submit */}
          <Button
            id="btn-create-lecture"
            size="lg"
            onClick={handleSubmit}
            className="w-full h-12 rounded-2xl bg-primary hover:bg-primary/90 shadow-lg shadow-primary/25 text-base font-semibold group"
          >
            <FileUp className="mr-2 h-5 w-5 group-hover:-translate-y-0.5 transition-transform" />
            Tạo video bài giảng ({slides.length} slide{slides.length > 1 ? 's' : ''}
            {quizzes.length > 0 ? ` · ${quizzes.length} câu hỏi` : ''})
          </Button>
        </div>
      )}

      {/* ── Bước 2: Đang tạo video ──────────────────────────────────────────── */}
      {(step === 'generating' || step === 'done' || step === 'failed') && (
        <div className="bg-card border border-border/50 rounded-2xl p-10 text-center space-y-6 animate-in fade-in slide-in-from-bottom-4 duration-500">
          <div className="w-20 h-20 rounded-2xl bg-primary/10 flex items-center justify-center mx-auto">
            {step === 'generating' && <Loader2 size={40} className="text-primary animate-spin" />}
            {step === 'done' && <CheckCircle2 size={40} className="text-green-500" />}
            {step === 'failed' && <XCircle size={40} className="text-destructive" />}
          </div>

          <div>
            <h2 className="text-xl font-bold text-foreground mb-2">
              {step === 'generating' && 'Đang tạo video bài giảng...'}
              {step === 'done' && 'Video đã sẵn sàng! 🎉'}
              {step === 'failed' && 'Tạo video thất bại'}
            </h2>
            <p className="text-muted-foreground text-sm max-w-sm mx-auto">
              {step === 'generating' &&
                'Remotion đang render video từ slides của bạn. Quá trình này có thể mất vài phút.'}
              {step === 'done' && 'Học sinh của bạn đã có thể xem video bài giảng và làm bài kiểm tra.'}
              {step === 'failed' &&
                (error || 'Không thể render video. Vui lòng kiểm tra video-service và thử lại.')}
            </p>
          </div>

          <div className="flex justify-center">
            <VideoStatusBadge status={videoStatus} />
          </div>

          {step === 'generating' && (
            <div className="max-w-md mx-auto space-y-2" aria-label="Tiến độ tạo video">
              <div className="h-2.5 overflow-hidden rounded-full bg-primary/10">
                <div
                  className="h-full rounded-full bg-primary transition-all duration-500"
                  style={{ width: `${Math.max(3, Math.round(videoProgress * 100))}%` }}
                />
              </div>
              <p className="text-xs text-muted-foreground">
                {videoProgress < 0.3
                  ? `Đang tạo giọng đọc và chuyển động nhân vật · ${Math.round(videoProgress * 100)}%`
                  : `Đang ghép video bài giảng · ${Math.round(videoProgress * 100)}%`}
              </p>
            </div>
          )}

          {step === 'done' && videoUrl && (
            <div className="rounded-xl overflow-hidden border border-border/50 bg-black">
              <video
                controls
                className="w-full max-h-80"
                src={videoUrl}
                aria-label={`Video bài giảng: ${lectureTitle}`}
              >
                Trình duyệt của bạn không hỗ trợ video HTML5.
              </video>
            </div>
          )}

          {(step === 'done' || step === 'failed') && (
            <Button
              variant="outline"
              onClick={handleReset}
              className="rounded-xl"
            >
              Tạo bài giảng khác
            </Button>
          )}
        </div>
      )}

      {/* Floating Button: Kéo xuống Quiz */}
      {step === 'form' && (
        <button
          onClick={handleScrollToQuiz}
          className="fixed bottom-10 right-10 w-14 h-14 bg-chart-1 text-white rounded-full shadow-[0_8px_30px_rgb(0,0,0,0.12)] hover:shadow-[0_8px_30px_rgb(0,0,0,0.2)] hover:scale-105 transition-all flex items-center justify-center z-50 group"
          title="Cuộn xuống Câu hỏi trắc nghiệm"
        >
          <HelpCircle size={26} className="group-hover:animate-bounce" />
        </button>
      )}
    </div>
  )
}
