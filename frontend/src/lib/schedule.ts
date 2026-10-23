import { parseDate } from './format'
import type { ScheduleType } from '@/types/student'

export const SCHEDULE_TYPE_LABEL: Record<ScheduleType, string> = {
  LECTURE: 'Lý thuyết',
  LAB: 'Thực hành',
  EXAM: 'Kiểm tra',
  MEETING: 'Họp',
  OTHER: 'Khác',
}

const DAY_MS = 86_400_000
const WEEKDAY_CODES = ['SU', 'MO', 'TU', 'WE', 'TH', 'FR', 'SA'] as const
const WEEKDAY_LABEL: Record<string, string> = {
  MO: 'Thứ 2', TU: 'Thứ 3', WE: 'Thứ 4', TH: 'Thứ 5', FR: 'Thứ 6', SA: 'Thứ 7', SU: 'Chủ nhật',
}

/** "FREQ=WEEKLY;BYDAY=MO,WE;UNTIL=20270601" → { FREQ: 'WEEKLY', BYDAY: 'MO,WE', UNTIL: '20270601' } */
function parseRule(rule: string): Record<string, string> {
  return Object.fromEntries(rule.split(';').map(p => p.split('=') as [string, string]).filter(([k, v]) => k && v))
}

/** Đọc RRULE đơn giản thành chữ: "Hằng tuần: Thứ 2, Thứ 4". */
export function describeRecurrence(rule: string | null): string | null {
  if (!rule) return null
  const parts = parseRule(rule)
  if (parts.FREQ === 'WEEKLY') {
    const days = (parts.BYDAY ?? '').split(',').map(d => WEEKDAY_LABEL[d]).filter(Boolean)
    return days.length ? `Hằng tuần: ${days.join(', ')}` : 'Hằng tuần'
  }
  if (parts.FREQ === 'DAILY') return 'Hằng ngày'
  if (parts.FREQ === 'MONTHLY') return 'Hằng tháng'
  return rule
}

/** "20270601" hoặc "20270601T000000Z" → Date (cuối ngày đó). */
function parseUntil(until: string | undefined): number | null {
  const m = until?.match(/^(\d{4})(\d{2})(\d{2})/)
  return m ? Date.UTC(Number(m[1]), Number(m[2]) - 1, Number(m[3]), 23, 59, 59) : null
}

export interface Occurrence {
  start: Date
  end: Date
  /** Lịch không lặp (hoặc đã hết hạn lặp) và buổi cuối đã kết thúc */
  past: boolean
}

/**
 * Buổi gần nhất chưa kết thúc của một lịch cố định, theo giờ máy người dùng.
 * Hỗ trợ FREQ=DAILY | WEEKLY (BYDAY) | MONTHLY và UNTIL; quy tắc khác coi như không lặp.
 */
export function nextOccurrence(startsAt: string, endsAt: string, recurrence: string | null, now: number): Occurrence | null {
  const start = parseDate(startsAt), end = parseDate(endsAt)
  if (!start || !end) return null
  const duration = Math.max(0, end.getTime() - start.getTime())
  const rule = recurrence ? parseRule(recurrence) : null
  const single = { start, end, past: end.getTime() < now }
  if (!rule || !['DAILY', 'WEEKLY', 'MONTHLY'].includes(rule.FREQ)) return single

  const until = parseUntil(rule.UNTIL)
  const days = rule.FREQ === 'WEEKLY'
    ? (rule.BYDAY ? rule.BYDAY.split(',') : [WEEKDAY_CODES[start.getDay()]])
    : null
  // Bắt đầu dò từ hôm nay (hoặc ngày bắt đầu nếu còn ở tương lai), giữ nguyên giờ học
  const firstK = Math.max(0, Math.floor((now - start.getTime()) / DAY_MS) - 1)
  for (let k = firstK; k < firstK + 400; k++) {
    const s = new Date(start.getTime() + k * DAY_MS)
    if (until != null && s.getTime() > until) break
    const matches = rule.FREQ === 'DAILY'
      || (rule.FREQ === 'WEEKLY' && days!.includes(WEEKDAY_CODES[s.getDay()]))
      || (rule.FREQ === 'MONTHLY' && s.getDate() === start.getDate())
    if (matches && s.getTime() + duration >= now) return { start: s, end: new Date(s.getTime() + duration), past: false }
  }
  return { ...single, past: true }
}

/** 0h Thứ 2 (giờ máy người dùng) của tuần chứa thời điểm `ms`. */
export function startOfWeek(ms: number): Date {
  const d = new Date(ms)
  d.setHours(0, 0, 0, 0)
  d.setDate(d.getDate() - ((d.getDay() + 6) % 7))
  return d
}

/** Cộng `n` ngày theo lịch (giữ 0h, không lệch khi đổi giờ mùa hè). */
export function addDays(date: Date, n: number): Date {
  const d = new Date(date)
  d.setDate(d.getDate() + n)
  return d
}

/** Mọi buổi của một lịch (lặp hoặc một lần) có giờ bắt đầu trong [from, to). */
export function occurrencesBetween(startsAt: string, endsAt: string, recurrence: string | null, from: Date, to: Date): { start: Date; end: Date }[] {
  const start = parseDate(startsAt), end = parseDate(endsAt)
  if (!start || !end) return []
  const duration = Math.max(0, end.getTime() - start.getTime())
  const rule = recurrence ? parseRule(recurrence) : null
  const inRange = (d: Date) => d >= from && d < to
  if (!rule || !['DAILY', 'WEEKLY', 'MONTHLY'].includes(rule.FREQ)) return inRange(start) ? [{ start, end }] : []

  const until = parseUntil(rule.UNTIL)
  const days = rule.FREQ === 'WEEKLY' ? (rule.BYDAY ? rule.BYDAY.split(',') : [WEEKDAY_CODES[start.getDay()]]) : null
  const result: { start: Date; end: Date }[] = []
  // Dò từng ngày trong khoảng, giữ nguyên giờ học của buổi đầu
  for (let day = new Date(Math.max(from.getTime(), start.getTime() - DAY_MS)); day < to; day = addDays(day, 1)) {
    const s = new Date(day)
    s.setHours(start.getHours(), start.getMinutes(), 0, 0)
    if (s < start || !inRange(s) || (until != null && s.getTime() > until)) continue
    const matches = rule.FREQ === 'DAILY'
      || (rule.FREQ === 'WEEKLY' && days!.includes(WEEKDAY_CODES[s.getDay()]))
      || (rule.FREQ === 'MONTHLY' && s.getDate() === start.getDate())
    if (matches) result.push({ start: s, end: new Date(s.getTime() + duration) })
  }
  return result
}

/** Giờ hiện tại — tách ra hàm riêng để component không gọi Date.now() trực tiếp khi render. */
export const currentTime = () => Date.now()
