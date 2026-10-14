import { Plus, Trash2 } from 'lucide-react'
import { newSlide } from '@/lib/slides'
import type { SlideForm } from '@/lib/slides'

interface SlideEditorProps {
  slides: SlideForm[]
  onChange: (slides: SlideForm[]) => void
}

const inputClass =
  'w-full px-3 py-2 rounded-lg border border-border/60 bg-background text-sm text-foreground placeholder:text-muted-foreground focus:outline-none focus:ring-2 focus:ring-primary/40 transition'

/** Danh sách slide có thể sửa: tiêu đề, bullet points, nội dung giọng đọc. */
export function SlideEditor({ slides, onChange }: SlideEditorProps) {
  const update = (id: string, patch: Partial<SlideForm>) =>
    onChange(slides.map((s) => (s.id === id ? { ...s, ...patch } : s)))

  const updateBullets = (slide: SlideForm, bulletPoints: string[]) => update(slide.id, { bulletPoints })

  return (
    <>
      {slides.map((slide, slideIdx) => (
        <div
          key={slide.id}
          className="bg-card border border-border/50 rounded-2xl p-4 sm:p-6 space-y-4 relative animate-in fade-in slide-in-from-bottom-2 duration-300"
        >
          <div className="flex items-center justify-between">
            <span className="text-xs font-bold uppercase tracking-wider text-muted-foreground">
              Slide {slideIdx + 1}
            </span>
            {slides.length > 1 && (
              <button
                type="button"
                onClick={() => onChange(slides.filter((s) => s.id !== slide.id))}
                className="flex h-9 w-9 items-center justify-center rounded-lg text-muted-foreground hover:text-destructive hover:bg-destructive/10 transition-colors"
                aria-label={`Xóa slide ${slideIdx + 1}`}
              >
                <Trash2 size={16} />
              </button>
            )}
          </div>

          <label className="block space-y-1.5">
            <span className="text-xs font-medium text-muted-foreground">Tiêu đề slide</span>
            <input
              type="text"
              value={slide.title}
              onChange={(e) => update(slide.id, { title: e.target.value })}
              placeholder="VD: Giới thiệu về Cây nhị phân"
              className={inputClass}
            />
          </label>

          <div className="space-y-1.5">
            <span className="text-xs font-medium text-muted-foreground">Bullet Points</span>
            <div className="space-y-2">
              {slide.bulletPoints.map((bullet, bulletIdx) => (
                <div key={bulletIdx} className="flex items-center gap-2">
                  <span className="w-1.5 h-1.5 rounded-full bg-primary flex-shrink-0" />
                  <input
                    type="text"
                    value={bullet}
                    onChange={(e) =>
                      updateBullets(slide, slide.bulletPoints.map((b, i) => (i === bulletIdx ? e.target.value : b)))}
                    placeholder={`Điểm ${bulletIdx + 1}...`}
                    aria-label={`Slide ${slideIdx + 1}, điểm ${bulletIdx + 1}`}
                    className={`flex-1 ${inputClass}`}
                  />
                  {slide.bulletPoints.length > 1 && (
                    <button
                      type="button"
                      onClick={() => updateBullets(slide, slide.bulletPoints.filter((_, i) => i !== bulletIdx))}
                      className="flex h-8 w-8 items-center justify-center rounded-lg text-muted-foreground hover:text-destructive transition-colors"
                      aria-label="Xóa bullet"
                    >
                      <Trash2 size={14} />
                    </button>
                  )}
                </div>
              ))}
            </div>
            <button
              type="button"
              onClick={() => updateBullets(slide, [...slide.bulletPoints, ''])}
              className="text-xs text-primary hover:text-primary/80 font-medium transition-colors mt-1 min-h-8"
            >
              + Thêm bullet point
            </button>
          </div>

          <label className="block space-y-1.5">
            <span className="text-xs font-medium text-muted-foreground">Nội dung giọng đọc (narration)</span>
            <textarea
              value={slide.narrationText}
              onChange={(e) => update(slide.id, { narrationText: e.target.value })}
              placeholder="Văn bản sẽ được chuyển thành giọng đọc cho slide này..."
              rows={3}
              className={`${inputClass} resize-y`}
            />
          </label>
        </div>
      ))}

      <button
        type="button"
        onClick={() => onChange([...slides, newSlide()])}
        className="w-full py-3 rounded-2xl border-2 border-dashed border-border/60 hover:border-primary/40 hover:bg-primary/5 text-muted-foreground hover:text-primary transition-all duration-300 flex items-center justify-center gap-2 text-sm font-medium"
      >
        <Plus size={18} />
        Thêm slide mới
      </button>
    </>
  )
}
