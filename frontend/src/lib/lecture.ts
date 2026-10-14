import type { LectureStatus } from '@/api/lectureApi'

/** Bài giảng có nội dung để xem (không còn đang tạo / lỗi) */
export function isLectureViewable(status: LectureStatus | undefined): boolean {
  return status === 'DRAFT' || status === 'READY' || status === 'PUBLISHED'
}
