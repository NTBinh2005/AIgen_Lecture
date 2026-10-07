import { ArrowDown, ArrowUp, Check, Plus, Trash2 } from 'lucide-react'
import { Field, Select, TextArea, fieldClass } from './FormKit'
import { cn } from '@/lib/utils'
import type { QuestionType } from '@/types/student'
import { QUESTION_TYPE_LABEL, isChoiceQuestion, newQuestion } from '@/lib/quiz'
import type { QuestionForm } from '@/lib/quiz'

const LETTERS = 'ABCDEFGHIJ'

interface QuestionEditorProps {
  question: QuestionForm
  index: number
  total: number
  onChange: (q: QuestionForm) => void
  onRemove: () => void
  onMove: (dir: -1 | 1) => void
}

export function QuestionEditor({ question: q, index, total, onChange, onRemove, onMove }: QuestionEditorProps) {
  const isChoice = isChoiceQuestion(q.questionType)
  const multi = q.questionType === 'MCQ_MULTI'
  const selected = (q.correctAnswer ?? '').split(',').filter(Boolean)
  const options = q.options ?? []

  const changeType = (type: QuestionType) => {
    const fresh = newQuestion(type)
    onChange({ ...q, questionType: type, options: fresh.options, correctAnswer: '' })
  }

  const toggleCorrect = (letter: string) => {
    if (!multi) return onChange({ ...q, correctAnswer: letter })
    const next = selected.includes(letter) ? selected.filter(l => l !== letter) : [...selected, letter]
    onChange({ ...q, correctAnswer: next.sort().join(',') })
  }

  const setOption = (i: number, value: string) => onChange({ ...q, options: options.map((o, j) => (j === i ? value : o)) })

  const removeOption = (i: number) => {
    const letter = LETTERS[i]
    // Bỏ lựa chọn → dịch các chữ cái đáp án phía sau lên 1
    const remap = selected.filter(l => l !== letter).map(l => (l > letter ? LETTERS[LETTERS.indexOf(l) - 1] : l))
    onChange({ ...q, options: options.filter((_, j) => j !== i), correctAnswer: remap.join(',') })
  }

  return (
    <div className="space-y-4 rounded-2xl border border-border/50 bg-card p-4 dark:bg-card/70 sm:p-5">
      <div className="flex flex-wrap items-center gap-2">
        <span className="text-sm font-bold text-primary">Câu {index + 1}</span>
        <Select
          aria-label="Loại câu hỏi"
          value={q.questionType}
          onChange={e => changeType(e.target.value as QuestionType)}
          className="h-9 w-auto flex-1 sm:flex-none"
        >
          {(Object.keys(QUESTION_TYPE_LABEL) as QuestionType[]).map(t => <option key={t} value={t}>{QUESTION_TYPE_LABEL[t]}</option>)}
        </Select>
        <div className="ml-auto flex gap-1">
          <IconButton label="Lên" disabled={index === 0} onClick={() => onMove(-1)}><ArrowUp size={15} /></IconButton>
          <IconButton label="Xuống" disabled={index === total - 1} onClick={() => onMove(1)}><ArrowDown size={15} /></IconButton>
          <IconButton label={`Xóa câu ${index + 1}`} danger onClick={onRemove}><Trash2 size={15} /></IconButton>
        </div>
      </div>

      <TextArea
        rows={2}
        value={q.questionText}
        onChange={e => onChange({ ...q, questionText: e.target.value })}
        placeholder="Nhập nội dung câu hỏi..."
        aria-label={`Nội dung câu ${index + 1}`}
      />

      {isChoice && (
        <div className="space-y-2">
          <p className="text-xs font-medium text-muted-foreground">
            Lựa chọn — bấm vào chữ cái để đánh dấu đáp án đúng{multi ? ' (chọn nhiều)' : ''}
          </p>
          {options.map((option, i) => {
            const letter = LETTERS[i]
            const correct = selected.includes(letter)
            return (
              <div key={i} className="flex items-center gap-2">
                <button
                  type="button"
                  onClick={() => toggleCorrect(letter)}
                  aria-pressed={correct}
                  aria-label={`Đánh dấu ${letter} là đáp án đúng`}
                  className={cn(
                    'flex h-10 w-10 shrink-0 items-center justify-center text-sm font-bold transition-colors',
                    multi ? 'rounded-lg' : 'rounded-full',
                    correct ? 'bg-emerald-500 text-white' : 'bg-muted text-muted-foreground hover:bg-emerald-500/20',
                  )}
                >
                  {correct ? <Check size={16} /> : letter}
                </button>
                <input
                  value={option}
                  onChange={e => setOption(i, e.target.value)}
                  disabled={q.questionType === 'TRUE_FALSE'}
                  placeholder={`Lựa chọn ${letter}`}
                  aria-label={`Lựa chọn ${letter}`}
                  className={cn(fieldClass, 'h-10')}
                />
                {q.questionType !== 'TRUE_FALSE' && options.length > 2 && (
                  <IconButton label={`Xóa lựa chọn ${letter}`} danger onClick={() => removeOption(i)}><Trash2 size={14} /></IconButton>
                )}
              </div>
            )
          })}
          {q.questionType !== 'TRUE_FALSE' && options.length < LETTERS.length && (
            <button
              type="button"
              onClick={() => onChange({ ...q, options: [...options, ''] })}
              className="flex min-h-9 items-center gap-1 text-sm font-medium text-primary hover:underline"
            >
              <Plus size={14} aria-hidden="true" /> Thêm lựa chọn
            </button>
          )}
        </div>
      )}

      <div className="grid gap-3 sm:grid-cols-[1fr_8rem]">
        {q.questionType === 'SHORT_ANSWER' ? (
          <Field label="Đáp án mẫu" hint="So khớp không phân biệt hoa thường">
            <input value={q.correctAnswer ?? ''} onChange={e => onChange({ ...q, correctAnswer: e.target.value })} className={fieldClass} />
          </Field>
        ) : (
          <Field label="Giải thích (hiện sau khi nộp)">
            <input value={q.explanation ?? ''} onChange={e => onChange({ ...q, explanation: e.target.value })} className={fieldClass} />
          </Field>
        )}
        <Field label="Điểm">
          <input
            type="number"
            min={1}
            step={1}
            value={q.points}
            onChange={e => onChange({ ...q, points: Number(e.target.value) })}
            className={fieldClass}
          />
        </Field>
      </div>
    </div>
  )
}

interface IconButtonProps {
  label: string
  onClick: () => void
  disabled?: boolean
  danger?: boolean
  children: React.ReactNode
}

function IconButton({ label, onClick, disabled, danger, children }: IconButtonProps) {
  return (
    <button
      type="button"
      onClick={onClick}
      disabled={disabled}
      aria-label={label}
      className={cn(
        'flex h-9 w-9 items-center justify-center rounded-lg text-muted-foreground transition-colors disabled:opacity-30',
        danger ? 'hover:bg-destructive/10 hover:text-destructive' : 'hover:bg-muted hover:text-foreground',
      )}
    >
      {children}
    </button>
  )
}
