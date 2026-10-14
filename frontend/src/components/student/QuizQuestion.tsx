import { Check } from 'lucide-react'
import { cn } from '@/lib/utils'
import type { AttemptQuestion } from '@/types/student'

const LETTERS = 'ABCDEFGHIJ'

/** Đáp án lưu dạng chữ cái: "B" (một đáp án) hoặc "B,D" (nhiều đáp án). */
function parseSelected(response: string | null | undefined): string[] {
  return (response ?? '').split(',').map(s => s.trim()).filter(Boolean)
}

interface QuizQuestionProps {
  question: AttemptQuestion
  index: number
  value: string
  onChange: (value: string) => void
  disabled?: boolean
}

export function QuizQuestion({ question: q, index, value, onChange, disabled }: QuizQuestionProps) {
  const multi = q.questionType === 'MCQ_MULTI'
  const isText = q.questionType === 'SHORT_ANSWER' || q.questionType === 'ESSAY'
  const selected = parseSelected(value)

  const toggle = (letter: string) => {
    if (!multi) return onChange(letter)
    const next = selected.includes(letter) ? selected.filter(l => l !== letter) : [...selected, letter]
    onChange(next.sort().join(','))
  }

  return (
    <fieldset disabled={disabled} className="space-y-5">
      <legend className="w-full">
        <div className="mb-2 flex flex-wrap items-center gap-2 text-sm text-muted-foreground">
          <span className="font-semibold text-primary">Câu {index + 1}</span>
          <span aria-hidden="true">·</span>
          <span>{q.points} điểm</span>
          {multi && <span className="rounded-full bg-amber-500/10 px-2 py-0.5 text-xs font-medium text-amber-700 dark:text-amber-400">Chọn nhiều đáp án</span>}
        </div>
        <p className="text-lg font-semibold leading-relaxed text-foreground sm:text-xl">{q.questionText}</p>
      </legend>

      {isText ? (
        q.questionType === 'ESSAY' ? (
          <textarea
            value={value}
            onChange={e => onChange(e.target.value)}
            rows={8}
            placeholder="Nhập bài làm của bạn..."
            aria-label={`Trả lời câu ${index + 1}`}
            className="w-full resize-y rounded-xl border border-input bg-background p-4 text-base outline-none focus:border-primary focus:ring-4 focus:ring-primary/10 dark:bg-input/30"
          />
        ) : (
          <input
            value={value}
            onChange={e => onChange(e.target.value)}
            placeholder="Nhập câu trả lời..."
            aria-label={`Trả lời câu ${index + 1}`}
            className="h-12 w-full rounded-xl border border-input bg-background px-4 text-base outline-none focus:border-primary focus:ring-4 focus:ring-primary/10 dark:bg-input/30"
          />
        )
      ) : (
        <div className="grid gap-3" role={multi ? 'group' : 'radiogroup'}>
          {(q.options ?? []).map((option, i) => {
            const letter = LETTERS[i]
            const active = selected.includes(letter)
            return (
              <button
                key={letter}
                type="button"
                role={multi ? 'checkbox' : 'radio'}
                aria-checked={active}
                onClick={() => toggle(letter)}
                className={cn(
                  'flex min-h-14 w-full items-center gap-4 rounded-xl border-2 px-4 py-3 text-left transition-all',
                  active
                    ? 'border-primary bg-primary/5 shadow-sm'
                    : 'border-border bg-card hover:border-primary/40 hover:bg-muted/40 dark:bg-card/60',
                )}
              >
                <span
                  className={cn(
                    'flex h-8 w-8 shrink-0 items-center justify-center text-sm font-bold transition-colors',
                    multi ? 'rounded-lg' : 'rounded-full',
                    active ? 'bg-primary text-primary-foreground' : 'bg-muted text-muted-foreground',
                  )}
                >
                  {active && multi ? <Check size={16} aria-hidden="true" /> : letter}
                </span>
                <span className="text-base text-foreground">{option}</span>
              </button>
            )
          })}
        </div>
      )}
    </fieldset>
  )
}
