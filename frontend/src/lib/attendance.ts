/** Link mã QR điểm danh: học sinh quét bằng camera điện thoại → mở trang Lịch học & Live và tự điểm danh. */
export function attendanceLink(sessionId: number, code: string): string {
  const url = new URL('/student/schedule', window.location.origin)
  url.searchParams.set('attend', String(sessionId))
  url.searchParams.set('code', code)
  return url.toString()
}

/** Hiển thị mã dài thành từng cụm 4 ký tự cho dễ đọc / gõ: e269 07a6 c508 … */
export function formatAttendanceCode(code: string): string {
  return code.replace(/(.{4})(?=.)/g, '$1 ')
}

/** Bỏ khoảng trắng / gạch nối học sinh gõ kèm khi nhập mã theo cụm. */
export function normalizeAttendanceCode(input: string): string {
  return input.replace(/[\s-]/g, '').toLowerCase()
}
