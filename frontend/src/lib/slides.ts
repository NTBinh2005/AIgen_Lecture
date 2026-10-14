/** Helpers cho form soạn slide bài giảng (dùng chung trang tạo và trang sửa). */
import type { SlideDto } from '@/api/lectureApi'

export interface SlideForm {
  id: string
  title: string
  bulletPoints: string[]
  narrationText: string
  imagePrompt?: string
}

export function newSlide(): SlideForm {
  return { id: crypto.randomUUID(), title: '', bulletPoints: [''], narrationText: '' }
}

export function toSlideForms(slides: SlideDto[]): SlideForm[] {
  return slides.map(s => ({
    id: crypto.randomUUID(),
    title: s.title ?? '',
    bulletPoints: s.bulletPoints?.length ? s.bulletPoints : [''],
    narrationText: s.narrationText ?? '',
    imagePrompt: s.imagePrompt,
  }))
}

/** Chuẩn hoá slides để gửi backend và so sánh có bị sửa so với bản AI hay không */
export function toSlideDtos(slides: Array<Omit<SlideForm, 'id'> | SlideDto>): SlideDto[] {
  return slides.map((s) => ({
    title: s.title.trim(),
    bulletPoints: (s.bulletPoints ?? []).map((b) => b.trim()).filter(Boolean),
    narrationText: (s.narrationText ?? '').trim(),
    ...(s.imagePrompt ? { imagePrompt: s.imagePrompt } : {}),
  }))
}

/** Backend bắt buộc có content khi publish → ghép nội dung chữ từ slides */
export function slidesToText(slides: SlideDto[]): string {
  return slides
    .map((s, i) => [`Slide ${i + 1}: ${s.title}`, ...s.bulletPoints.map((b) => `- ${b}`), s.narrationText]
      .filter(Boolean)
      .join('\n'))
    .join('\n\n')
}

/** Trả về số thứ tự (1-based) của slide chưa hợp lệ, hoặc null nếu tất cả hợp lệ. */
export function findInvalidSlide(slides: SlideForm[]): number | null {
  const idx = slides.findIndex((s) =>
    !s.title.trim() || !s.narrationText.trim() || !s.bulletPoints.some((b) => b.trim()))
  return idx === -1 ? null : idx + 1
}
