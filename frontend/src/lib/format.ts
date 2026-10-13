/** Helpers định dạng thời gian / lỗi dùng chung. */

/**
 * Backend chạy và lưu thời gian theo UTC (DemoApplication ép TimeZone UTC). Các trường LocalDateTime
 * trả về không kèm múi giờ ("2026-10-13T13:33:00") nên phải hiểu là UTC; trường Instant đã có sẵn "Z".
 */
const HAS_ZONE = /(Z|[+-]\d{2}:?\d{2})$/

export function parseDate(value: string | null | undefined): Date | null {
  if (!value) return null
  const d = new Date(HAS_ZONE.test(value) ? value : `${value}Z`)
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

const pad = (n: number) => String(n).padStart(2, '0')

/** Thời gian backend (UTC) → giá trị giờ địa phương cho <input type='datetime-local'> */
export function toDateTimeInput(value: string | null | undefined): string {
  const d = parseDate(value)
  if (!d) return ''
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`
}

/** Giờ địa phương người dùng nhập ở <input type='datetime-local'> → LocalDateTime UTC gửi backend (null nếu rỗng) */
export function fromDateTimeInput(value: string): string | null {
  if (!value) return null
  const d = new Date(value) // chuỗi không có múi giờ → trình duyệt hiểu là giờ địa phương
  return Number.isNaN(d.getTime()) ? null : d.toISOString().slice(0, 19)
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
