/** Helpers cho trình soạn quiz và giao bài kiểm tra của Teacher. */
import type { QuestionType } from '@/types/student'
import type { QuizQuestion, ResultPolicy } from '@/types/teacher'

export const QUESTION_TYPE_LABEL: Record<QuestionType, string> = {
  MCQ_SINGLE: 'Trắc nghiệm 1 đáp án',
  MCQ_MULTI: 'Trắc nghiệm nhiều đáp án',
  TRUE_FALSE: 'Đúng / Sai',
  SHORT_ANSWER: 'Trả lời ngắn',
  ESSAY: 'Tự luận',
}

export const RESULT_POLICY_LABEL: Record<ResultPolicy, string> = {
  AFTER_SUBMISSION: 'Xem ngay sau khi nộp',
  AFTER_GRADING: 'Sau khi giáo viên chấm',
  AFTER_CLOSE_AT: 'Sau khi hết hạn',
  NEVER: 'Không cho xem',
}

/** Câu hỏi trong form có key ổn định cho React */
export type QuestionForm = QuizQuestion & { key: string }

export function isChoiceQuestion(type: QuestionType) {
  return type === 'MCQ_SINGLE' || type === 'MCQ_MULTI' || type === 'TRUE_FALSE'
}

export function newQuestion(type: QuestionType = 'MCQ_SINGLE'): QuestionForm {
  return {
    key: crypto.randomUUID(),
    questionType: type,
    questionText: '',
    options: type === 'TRUE_FALSE' ? ['Đúng', 'Sai'] : isChoiceQuestion(type) ? ['', '', '', ''] : [],
    correctAnswer: '',
    points: 1,
    explanation: '',
  }
}

export function toQuestionForms(questions: QuizQuestion[]): QuestionForm[] {
  return [...questions]
    .sort((a, b) => (a.orderIndex ?? 0) - (b.orderIndex ?? 0))
    .map(q => ({ ...q, key: crypto.randomUUID(), options: q.options ?? [], correctAnswer: q.correctAnswer ?? '' }))
}

/** Chuẩn hoá câu hỏi để gửi backend (bỏ key/questionId, đánh lại orderIndex). */
export function toQuestionPayload(questions: QuestionForm[]): QuizQuestion[] {
  return questions.map((q, i) => ({
    questionType: q.questionType,
    questionText: q.questionText.trim(),
    options: isChoiceQuestion(q.questionType) ? (q.options ?? []).map(o => o.trim()) : [],
    correctAnswer: q.correctAnswer?.trim() || null,
    explanation: q.explanation?.trim() || null,
    points: q.points,
    orderIndex: i + 1,
  }))
}

/** Trả về thông báo lỗi đầu tiên, hoặc null nếu hợp lệ. */
export function validateQuestion(q: QuizQuestion, index: number): string | null {
  const label = `Câu ${index + 1}`
  if (!q.questionText.trim()) return `${label}: chưa nhập nội dung câu hỏi.`
  // Backend lưu points là Integer (0.5 bị cắt thành 0) → chỉ cho số nguyên ≥ 1
  if (!Number.isInteger(q.points) || q.points < 1) return `${label}: điểm phải là số nguyên từ 1 trở lên.`
  if (isChoiceQuestion(q.questionType)) {
    const options = q.options ?? []
    if (options.length < 2 || options.some(o => !o.trim())) return `${label}: cần ít nhất 2 lựa chọn và không để trống.`
    if (!q.correctAnswer) return `${label}: chưa chọn đáp án đúng.`
  }
  if (q.questionType === 'SHORT_ANSWER' && !q.correctAnswer?.trim()) return `${label}: cần đáp án mẫu để tự chấm.`
  return null
}
