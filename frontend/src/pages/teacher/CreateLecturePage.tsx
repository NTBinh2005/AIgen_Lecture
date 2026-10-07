import { useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { FileUp, Sparkles, Loader2, CheckCircle2, UploadCloud, Save, Lock, Users, Eye } from 'lucide-react'
import { Button } from '@/components/ui/button'
import {
  createLecture, deleteLecture, getLecture, getLectureVersion, getGenerationJob,
  parseSlideContent, publishLecture, retryGenerationJob, startGenerationFromFile, updateLecture,
  type LectureAccessScope, type LectureResponse,
} from '@/api/lectureApi'
import { SlideEditor } from '@/components/teacher/SlideEditor'
import { findInvalidSlide, newSlide, slidesToText, toSlideDtos, toSlideForms } from '@/lib/slides'
import type { SlideForm } from '@/lib/slides'

// ─── Types ─────────────────────────────────────────────────────────────────────

type FormStep = 'form' | 'saving' | 'done'

type AiPhase = 'idle' | 'uploading' | 'running'

/** Bài giảng nháp do AI tạo — lưu lại vào chính bản nháp này (PATCH slides) */
interface GeneratedDraft {
  lectureId: number
}

// ─── Helper ────────────────────────────────────────────────────────────────────

const MAX_FILE_SIZE = 20 * 1024 * 1024 // BR-01
const POLL_INTERVAL_MS = 2000
const MAX_AUTO_RETRIES = 2 // Gemini hay trả 503 "high demand" → tự thử lại
const POLL_TIMEOUT_MS = 5 * 60 * 1000

const STEP_LABELS: Record<string, string> = {
  QUEUED: 'Đang xếp hàng...',
  PARSING_SOURCE: 'Đang đọc tài liệu...',
  GENERATING_LECTURE: 'AI đang thiết kế slides...',
  SAVING_DRAFT: 'Đang lưu bản nháp...',
}

const sleep = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms))

function errorMessage(err: unknown, fallback: string): string {
  const e = err as { response?: { data?: { message?: string } }; message?: string }
  return e?.response?.data?.message || e?.message || fallback
}

// ─── Main Page ─────────────────────────────────────────────────────────────────

/**
 * Trang Teacher tạo bài giảng mới.
 * - Tạo nhanh bằng AI: upload file → backend chạy generation job nền → poll tiến độ → nạp slides vào form.
 * - Soạn/sửa slides → Lưu nháp hoặc Lưu & xuất bản.
 */
export default function CreateLecturePage() {
  const navigate = useNavigate()

  const [step, setStep] = useState<FormStep>('form')
  const [lectureTitle, setLectureTitle] = useState('')
  const [accessScope, setAccessScope] = useState<LectureAccessScope>('PRIVATE')
  const [slides, setSlides] = useState<SlideForm[]>([newSlide()])
  const [error, setError] = useState<string | null>(null)
  const [savedLecture, setSavedLecture] = useState<LectureResponse | null>(null)

  // States cho tạo bằng AI
  const [aiPhase, setAiPhase] = useState<AiPhase>('idle')
  const [aiProgress, setAiProgress] = useState(0)
  const [aiStepLabel, setAiStepLabel] = useState('')
  const [generated, setGenerated] = useState<GeneratedDraft | null>(null)
  const fileInputRef = useRef<HTMLInputElement>(null)
  const unmountedRef = useRef(false)

  useEffect(() => {
    unmountedRef.current = false
    return () => { unmountedRef.current = true }
  }, [])

  const isGeneratingLLM = aiPhase !== 'idle'

  // ── Sinh slides bằng AI từ File (generation job bất đồng bộ) ─────────────────

  /** Poll job tới khi DONE; tự retry khi FAILED (thường do Gemini quá tải tạm thời) */
  const waitForJob = async (jobId: string) => {
    const deadline = Date.now() + POLL_TIMEOUT_MS
    let retries = 0
    while (Date.now() < deadline) {
      await sleep(POLL_INTERVAL_MS)
      if (unmountedRef.current) throw new Error('cancelled')

      let job
      try {
        job = await getGenerationJob(jobId)
      } catch {
        continue // lỗi mạng tạm thời → poll lại
      }
      setAiProgress(job.progress ?? 0)
      setAiStepLabel(STEP_LABELS[job.currentStep ?? job.status] ?? 'Đang xử lý...')

      if (job.status === 'DONE') return
      if (job.status === 'CANCELLED') throw new Error('Tác vụ tạo bài giảng đã bị huỷ.')
      if (job.status === 'FAILED') {
        if (retries >= MAX_AUTO_RETRIES) {
          throw new Error(job.safeErrorMessage || 'AI không tạo được bài giảng. Vui lòng thử lại sau.')
        }
        retries++
        setAiStepLabel(`AI đang quá tải, thử lại lần ${retries}/${MAX_AUTO_RETRIES}...`)
        await sleep(3000 * retries)
        await retryGenerationJob(jobId)
      }
    }
    throw new Error('Quá thời gian chờ AI. Vui lòng thử lại.')
  }

  const handleFileUpload = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    if (!file) return

    // Xóa file đang chọn để có thể upload lại file cũ nếu muốn
    if (fileInputRef.current) {
      fileInputRef.current.value = ''
    }
    if (file.size > MAX_FILE_SIZE) {
      setError('File vượt quá 20MB. Vui lòng chọn file nhỏ hơn.')
      return
    }

    // Backend bắt buộc có title → mặc định lấy theo tên file
    const title = lectureTitle.trim() || file.name.replace(/\.[^.]+$/, '')
    if (!lectureTitle.trim()) setLectureTitle(title)

    try {
      setError(null)
      setAiPhase('uploading')
      setAiProgress(0)
      setAiStepLabel('Đang tải tài liệu lên...')

      // Upload lại file khác → bỏ bản nháp AI trước đó (chưa được lưu)
      if (generated) {
        deleteLecture(generated.lectureId).catch(() => { /* bỏ qua */ })
        setGenerated(null)
      }

      const { lectureId, jobId } = await startGenerationFromFile(file, title, accessScope)
      setAiPhase('running')
      await waitForJob(jobId)

      const lecture = await getLecture(lectureId)
      const version = lecture.currentVersionId
        ? await getLectureVersion(lectureId, lecture.currentVersionId)
        : null
      const aiSlides = parseSlideContent(version?.slideContent)
      if (aiSlides.length === 0) {
        setError('AI không sinh được slide nào từ tài liệu này.')
        return
      }

      setSlides(toSlideForms(aiSlides))
      setGenerated({ lectureId })
    } catch (err) {
      if (unmountedRef.current) return
      setError(errorMessage(err, 'Lỗi khi gọi AI. Vui lòng thử lại.'))
    } finally {
      if (!unmountedRef.current) setAiPhase('idle')
    }
  }

  // ── Lưu bài giảng ───────────────────────────────────────────────────────────

  const handleSave = async (publish: boolean) => {
    if (!lectureTitle.trim()) {
      setError('Vui lòng nhập tiêu đề bài giảng.')
      return
    }
    const invalidSlide = findInvalidSlide(slides)
    if (invalidSlide !== null) {
      setError(`Slide ${invalidSlide} cần có tiêu đề, ít nhất 1 bullet point và nội dung đọc.`)
      return
    }

    setError(null)
    setStep('saving')
    const slideDtos = toSlideDtos(slides)

    try {
      let lectureId: number
      if (generated) {
        // Ghi slides (kể cả đã sửa) vào chính bản nháp AI qua PATCH
        lectureId = generated.lectureId
        await updateLecture(lectureId, {
          title: lectureTitle.trim(),
          content: slidesToText(slideDtos),
          accessScope,
          slides: slideDtos,
        })
      } else {
        const created = await createLecture({
          title: lectureTitle.trim(),
          originalSource: slidesToText(slideDtos),
          accessScope,
          slides: slideDtos,
        })
        lectureId = created.lectureId
      }

      const result = publish ? await publishLecture(lectureId) : await getLecture(lectureId)
      setGenerated(null)
      setSavedLecture(result)
      setStep('done')
    } catch (err) {
      setError(errorMessage(err, 'Không thể lưu bài giảng. Vui lòng thử lại.'))
      setStep('form')
    }
  }

  const handleReset = () => {
    setStep('form')
    setLectureTitle('')
    setAccessScope('PRIVATE')
    setSlides([newSlide()])
    setError(null)
    setSavedLecture(null)
    setGenerated(null)
  }

  return (
    <div className="max-w-4xl mx-auto space-y-6 pb-12">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-foreground flex items-center gap-2">
            <FileUp className="text-primary" size={26} />
            Tạo bài giảng mới
          </h1>
          <p className="text-sm text-muted-foreground mt-1">
            Soạn bài giảng thủ công hoặc để AI thiết kế tự động từ tài liệu của bạn.
          </p>
        </div>
      </div>

      {/* ── Bước 1: Soạn / Nhập nội dung ────────────────────────────────────── */}
      {step === 'form' && (
        <div className="space-y-6">
          {/* Card AI Import */}
          <div className="bg-gradient-to-r from-primary/10 via-accent/10 to-primary/5 border border-primary/20 rounded-2xl p-6 space-y-4">
            <div className="flex items-start justify-between">
              <div className="flex items-center gap-2.5">
                <div className="w-9 h-9 rounded-xl bg-primary/15 text-primary flex items-center justify-center">
                  <Sparkles size={20} />
                </div>
                <div>
                  <h2 className="text-base font-bold text-foreground">Tạo nhanh bằng AI</h2>
                  <p className="text-xs text-muted-foreground">Tải lên file PDF, DOCX, PPTX để AI tự động soạn slides</p>
                </div>
              </div>
            </div>

            <div className="flex items-center gap-3">
              <input
                ref={fileInputRef}
                type="file"
                accept=".pdf,.docx,.pptx"
                className="hidden"
                onChange={handleFileUpload}
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
              <div className="space-y-2">
                <div className="flex items-center justify-between text-xs font-medium text-primary/80">
                  <span className="animate-pulse">{aiStepLabel}</span>
                  <span>{aiProgress}%</span>
                </div>
                <div className="h-2 rounded-full bg-primary/10 overflow-hidden">
                  <div
                    className="h-full bg-primary transition-all duration-500"
                    style={{ width: `${Math.max(aiProgress, 5)}%` }}
                  />
                </div>
                <p className="text-xs text-muted-foreground">
                  Thường mất 20–60 giây. Bạn có thể nhập tiêu đề và chọn phạm vi trong lúc chờ.
                </p>
              </div>
            )}
            {generated && !isGeneratingLLM && (
              <p className="text-xs text-green-600 font-medium flex items-center gap-1.5">
                <CheckCircle2 size={14} /> AI đã soạn xong {slides.length} slides — kiểm tra và chỉnh sửa bên dưới.
              </p>
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

            {/* Access scope */}
            <div className="pt-2 space-y-2">
              <span className="text-sm font-semibold text-foreground">Phạm vi truy cập</span>
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
                {([
                  { value: 'PRIVATE', icon: Lock, label: 'Riêng tư', desc: 'Chỉ bạn và cộng tác viên xem được' },
                  { value: 'CLASS', icon: Users, label: 'Lớp học', desc: 'Học sinh của lớp được giao xem được' },
                ] as const).map(({ value, icon: Icon, label, desc }) => (
                  <button
                    key={value}
                    type="button"
                    onClick={() => setAccessScope(value)}
                    className={`flex items-start gap-3 text-left px-4 py-3 rounded-xl border transition ${
                      accessScope === value
                        ? 'border-primary bg-primary/5 ring-2 ring-primary/30'
                        : 'border-border/60 hover:border-primary/40'
                    }`}
                  >
                    <Icon size={18} className={accessScope === value ? 'text-primary mt-0.5' : 'text-muted-foreground mt-0.5'} />
                    <span>
                      <span className="block text-sm font-medium text-foreground">{label}</span>
                      <span className="block text-xs text-muted-foreground">{desc}</span>
                    </span>
                  </button>
                ))}
              </div>
            </div>
          </div>

          {/* Slides */}
          <SlideEditor slides={slides} onChange={setSlides} />

          {/* Error */}
          {error && (
            <p className="text-sm text-destructive bg-destructive/10 border border-destructive/20 rounded-xl px-4 py-3">
              {error}
            </p>
          )}

          {/* Submit */}
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <Button
              id="btn-save-draft"
              size="lg"
              variant="outline"
              disabled={isGeneratingLLM}
              onClick={() => handleSave(false)}
              className="h-12 rounded-2xl text-base font-semibold"
            >
              <Save className="mr-2 h-5 w-5" />
              Lưu bản nháp
            </Button>
            <Button
              id="btn-create-lecture"
              size="lg"
              disabled={isGeneratingLLM}
              onClick={() => handleSave(true)}
              className="h-12 rounded-2xl bg-primary hover:bg-primary/90 shadow-lg shadow-primary/25 text-base font-semibold group"
            >
              <FileUp className="mr-2 h-5 w-5 group-hover:-translate-y-0.5 transition-transform" />
              Lưu & xuất bản ({slides.length} slide{slides.length > 1 ? 's' : ''})
            </Button>
          </div>
        </div>
      )}

      {/* ── Bước 2: Đang lưu / Đã lưu ──────────────────────────────────────── */}
      {(step === 'saving' || step === 'done') && (
        <div className="bg-card border border-border/50 rounded-2xl p-10 text-center space-y-6 animate-in fade-in slide-in-from-bottom-4 duration-500">
          <div className="w-20 h-20 rounded-2xl bg-primary/10 flex items-center justify-center mx-auto">
            {step === 'saving'
              ? <Loader2 size={40} className="text-primary animate-spin" />
              : <CheckCircle2 size={40} className="text-green-500" />}
          </div>

          <div className="space-y-2">
            <h2 className="text-xl font-bold text-foreground">
              {step === 'saving' && 'Đang lưu bài giảng...'}
              {step === 'done' && (savedLecture?.status === 'PUBLISHED' ? 'Đã xuất bản bài giảng! 🎉' : 'Đã lưu bản nháp')}
            </h2>
            {step === 'done' && savedLecture && (
              <div className="text-muted-foreground text-sm max-w-md mx-auto space-y-1">
                <p>
                  <strong className="text-foreground">{savedLecture.title}</strong> ·{' '}
                  {savedLecture.accessScope === 'CLASS' ? 'Phạm vi: Lớp học' : 'Phạm vi: Riêng tư'}
                </p>
                {savedLecture.status === 'PUBLISHED' && savedLecture.accessScope === 'CLASS' && (
                  <p>Học sinh sẽ xem được khi bài giảng được giao cho lớp học.</p>
                )}
                {savedLecture.status !== 'PUBLISHED' && (
                  <p>Bạn có thể xuất bản sau trong mục “Bài giảng của tôi”.</p>
                )}
                <p className="text-xs pt-2">
                  Video bài giảng chưa được tạo tự động ở phiên bản hệ thống hiện tại — học sinh xem nội dung dạng slides.
                </p>
              </div>
            )}
          </div>

          {step === 'done' && savedLecture && (
            <div className="flex flex-wrap justify-center gap-3">
              <Button
                onClick={() => navigate(`/teacher/lectures/${savedLecture.lectureId}`)}
                className="rounded-xl"
              >
                <Eye size={16} className="mr-2" /> Xem bài giảng
              </Button>
              <Button variant="outline" onClick={handleReset} className="rounded-xl">
                Tạo bài giảng khác
              </Button>
            </div>
          )}
        </div>
      )}
    </div>
  )
}
