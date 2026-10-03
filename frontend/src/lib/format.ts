/** Helpers định dạng thời gian / lỗi dùng chung cho các trang Student. */

/** Backend trả LocalDateTime không timezone → parse như giờ local. */
export function parseDate(value: string | null | undefined): Date | null {
  if (!value) return null
  const d = new Date(value)
  return Number.isNaN(d.getTime()) ? null : d
}

export function formatDate(value: string | null | undefined): string {
  const d = parseDate(value)
  return d ? d.toLocaleDateString('vi-VN', { day: '2-digit', month: '2-digit', year: 'numeric' }) : '—'
}

export function formatDateTime(value: string | null | undefined): string {
  const d = parseDate(value)
  return d
    ? d.toLocaleString('vi-VN', { hour: '2-digit', minute: '2-digit', day: '2-digit', month: '2-digit', year: 'numeric' })
    : '—'
}

export function formatTime(value: string | null | undefined): string {
  const d = parseDate(value)
  return d ? d.toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' }) : '—'
}

/** "Thứ Hai, 06/10" */
export function formatWeekday(value: string | null | undefined): string {
  const d = parseDate(value)
  return d ? d.toLocaleDateString('vi-VN', { weekday: 'long', day: '2-digit', month: '2-digit' }) : '—'
}

/** LocalDateTime backend ("2026-10-01T08:00:00") → giá trị cho <input type="datetime-local"> */
export function toDateTimeInput(value: string | null | undefined): string {
  return value ? value.slice(0, 16) : ''
}

/** Giá trị <input type="datetime-local"> → LocalDateTime gửi backend (null nếu rỗng) */
export function fromDateTimeInput(value: string): string | null {
  return value ? (value.length === 16 ? `${value}:00` : value) : null
}

/** Lấy message lỗi từ ApiError của backend, fallback khi không có. */
export function getErrorMessage(error: unknown, fallback = 'Có lỗi xảy ra, vui lòng thử lại.'): string {
  if (typeof error === 'object' && error !== null && 'response' in error) {
    const response = (error as { response?: { data?: { message?: unknown } } }).response
    const message = response?.data?.message
    if (typeof message === 'string' && message.trim()) return message
  }
  return fallback
}

export function getErrorStatus(error: unknown): number | undefined {
  if (typeof error === 'object' && error !== null && 'response' in error) {
    return (error as { response?: { status?: number } }).response?.status
  }
  return undefined
}
