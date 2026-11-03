import { useEffect, useRef, useState } from 'react'
import { motion, useInView, useReducedMotion } from 'framer-motion'
import { CheckCircle2, ClipboardCheck, FileText, Loader2, PlaySquare, Sparkles } from 'lucide-react'
import { cn } from '@/lib/utils'

const OUTPUTS = [
  { icon: PlaySquare, tag: 'Slide 1 · Mở đầu', text: 'Cây xanh "nấu ăn" bằng ánh sáng như thế nào?' },
  { icon: Sparkles, tag: 'Slide 2 · Giảng giải', text: 'Pha sáng và pha tối diễn ra ở lục lạp' },
  { icon: ClipboardCheck, tag: 'Câu hỏi · Trắc nghiệm', text: 'Sản phẩm của quang hợp gồm những chất nào?' },
]

// Thời gian dừng ở mỗi bước (ms): tải file → AI xử lý → từng kết quả hiện ra → giữ kết quả rồi lặp lại
const STEP_MS = [900, 1800, 500, 500, 3200]
const PROCESSING_STEP = 1
const FINAL_STEP = STEP_MS.length - 1

/** Minh hoạ tăng tốc quy trình soạn bài giảng. Chỉ chạy khi đang trong màn hình; giảm chuyển động thì hiện luôn kết quả. */
export function LectureGenerationDemo() {
  const ref = useRef<HTMLDivElement>(null)
  const inView = useInView(ref, { margin: '-40px' })
  const reduceMotion = useReducedMotion()
  const [step, setStep] = useState(0)
  const current = reduceMotion ? FINAL_STEP : step

  useEffect(() => {
    if (!inView || reduceMotion) return
    const timer = setTimeout(() => setStep(s => (s >= FINAL_STEP ? 0 : s + 1)), STEP_MS[step])
    return () => clearTimeout(timer)
  }, [step, inView, reduceMotion])

  const processing = current === PROCESSING_STEP
  const done = current > PROCESSING_STEP

  return (
    <div ref={ref} className="space-y-3" aria-hidden="true">
      <div className="rounded-2xl border border-border/60 bg-background/80 p-3">
        <div className="flex items-center gap-3">
          <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-red-500/10 text-red-600 dark:text-red-400">
            <FileText size={20} />
          </span>
          <div className="min-w-0 flex-1">
            <p className="truncate text-sm font-semibold text-foreground">Quang hợp.pdf</p>
            <p className={cn('flex items-center gap-1.5 text-xs', done ? 'text-emerald-600 dark:text-emerald-400' : 'text-muted-foreground')}>
              {done && <CheckCircle2 size={13} />}
              {processing && <Loader2 size={13} className="animate-spin" />}
              {done ? 'Đã soạn xong bài giảng' : processing ? 'AI đang soạn bài giảng...' : 'Đã tải lên'}
            </p>
          </div>
        </div>
        <div className="mt-3 h-1.5 overflow-hidden rounded-full bg-muted">
          <motion.div
            className="h-full rounded-full bg-linear-to-r from-primary to-violet-500"
            initial={false}
            animate={{ width: done ? '100%' : processing ? '100%' : '8%' }}
            transition={processing ? { duration: STEP_MS[PROCESSING_STEP] / 1000, ease: 'easeInOut' } : { duration: 0.2 }}
          />
        </div>
      </div>

      {OUTPUTS.map((o, i) => {
        const visible = current >= PROCESSING_STEP + 1 + i
        return (
          // Ô giữ chỗ cố định để chiều cao thẻ không đổi khi kết quả hiện ra
          <div key={o.tag} className="rounded-2xl border border-dashed border-border/60">
            <motion.div
              initial={false}
              animate={{ opacity: visible ? 1 : 0, y: visible ? 0 : 8 }}
              transition={{ duration: 0.3 }}
              className="m-[-1px] rounded-2xl border border-border/60 bg-background/80 p-3"
            >
              <p className="flex items-center gap-1.5 text-[11px] font-bold uppercase tracking-wider text-primary">
                <o.icon size={13} /> {o.tag}
              </p>
              <p className="mt-1 truncate text-sm font-semibold text-foreground">{o.text}</p>
            </motion.div>
          </div>
        )
      })}
    </div>
  )
}
