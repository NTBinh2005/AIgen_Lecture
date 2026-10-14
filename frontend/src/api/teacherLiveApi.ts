/**
 * API buổi học live, điểm danh và lịch học cho Teacher.
 * (Đọc danh sách buổi học / lịch theo lớp dùng chung liveApi.ts)
 */
import axiosInstance from './axiosInstance'
import type { LiveSession, Schedule } from '@/types/student'
import type { AttendanceSummary, LiveSessionPayload, QrToken, SchedulePayload } from '@/types/teacher'

/** Backend chặn trùng lịch và endsAt <= startsAt (trả 400 kèm message). */
export async function createLiveSession(payload: LiveSessionPayload): Promise<LiveSession> {
  const res = await axiosInstance.post<LiveSession>('/live-sessions', payload)
  return res.data
}

export async function updateLiveSession(sessionId: number, payload: Partial<LiveSessionPayload>): Promise<LiveSession> {
  const res = await axiosInstance.patch<LiveSession>(`/live-sessions/${sessionId}`, payload)
  return res.data
}

export type LiveTransition = 'open' | 'live' | 'end'

/** SCHEDULED → open → OPEN → live → LIVE → end → ENDED */
export async function transitionLiveSession(sessionId: number, action: LiveTransition): Promise<LiveSession> {
  const res = await axiosInstance.post<LiveSession>(`/live-sessions/${sessionId}/${action}`)
  return res.data
}

export async function cancelLiveSession(sessionId: number, cancelReason: string): Promise<LiveSession> {
  const res = await axiosInstance.post<LiveSession>(`/live-sessions/${sessionId}/cancel`, { cancelReason })
  return res.data
}

/** Mã QR điểm danh, hết hạn sau ~10 phút. */
export async function generateQr(sessionId: number): Promise<QrToken> {
  const res = await axiosInstance.post<QrToken>(`/live-sessions/${sessionId}/qr/generate`)
  return res.data
}

export async function getAttendance(sessionId: number): Promise<AttendanceSummary[]> {
  const res = await axiosInstance.get<AttendanceSummary[]>(`/live-sessions/${sessionId}/attendance`)
  return res.data
}

export async function markAttendance(sessionId: number, studentId: number): Promise<void> {
  await axiosInstance.post(`/live-sessions/${sessionId}/attendance/manual`, { studentId })
}

// ─── Lịch học ─────────────────────────────────────────────────────────────────

export async function createSchedule(payload: SchedulePayload): Promise<Schedule> {
  const res = await axiosInstance.post<Schedule>('/schedules', payload)
  return res.data
}

export async function deleteSchedule(scheduleId: number): Promise<void> {
  await axiosInstance.delete(`/schedules/${scheduleId}`)
}
