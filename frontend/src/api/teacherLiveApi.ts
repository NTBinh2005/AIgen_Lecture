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

/** Sửa một lịch (PATCH — chỉ gửi các trường cần đổi). */
export async function updateSchedule(scheduleId: number, payload: Partial<SchedulePayload>): Promise<Schedule> {
  const res = await axiosInstance.patch<Schedule>(`/schedules/${scheduleId}`, payload)
  return res.data
}

export async function deleteSchedule(scheduleId: number): Promise<void> {
  await axiosInstance.delete(`/schedules/${scheduleId}`)
}

// ─── Tài nguyên, ghi hình, người tham gia ─────────────────────────────────────

export interface SessionResource {
  id: number
  sessionId: number
  resourceType: 'PRESENTATION' | 'ASSET'
  resourceId: number
  title: string | null
  downloadAllowed: boolean
  createdAt: string
}

export interface Recording {
  recordingId: number
  sessionId: number
  status: string
  playbackUrl: string | null
  durationSeconds: number | null
  consentRequired: boolean
  createdAt: string
}

export interface SessionParticipant {
  participantId: number
  userId: number
  userName: string
  role: string
  muted: boolean
  joinedAt: string | null
  leftAt: string | null
}

/** Dùng chung cho giáo viên và học sinh của lớp. */
export async function getSessionResources(sessionId: number): Promise<SessionResource[]> {
  const res = await axiosInstance.get<SessionResource[]>(`/live-sessions/${sessionId}/resources`)
  return res.data
}

export async function addSessionResource(
  sessionId: number,
  payload: { resourceType: 'PRESENTATION' | 'ASSET'; resourceId: number; title: string; downloadAllowed: boolean },
): Promise<SessionResource> {
  const res = await axiosInstance.post<SessionResource>(`/live-sessions/${sessionId}/resources`, payload)
  return res.data
}

export async function removeSessionResource(sessionId: number, resourceLinkId: number): Promise<void> {
  await axiosInstance.delete(`/live-sessions/${sessionId}/resources/${resourceLinkId}`)
}

/** 404 khi buổi học chưa có bản ghi nào → trả null. */
export async function getRecording(sessionId: number): Promise<Recording | null> {
  try {
    const res = await axiosInstance.get<Recording>(`/live-sessions/${sessionId}/recording`)
    return res.data
  } catch (error) {
    if ((error as { response?: { status?: number } }).response?.status === 404) return null
    throw error
  }
}

export async function requestRecording(sessionId: number): Promise<Recording> {
  const res = await axiosInstance.post<Recording>(`/live-sessions/${sessionId}/recording/request`)
  return res.data
}

export async function getParticipants(sessionId: number): Promise<SessionParticipant[]> {
  const res = await axiosInstance.get<SessionParticipant[]>(`/live-sessions/${sessionId}/participants`)
  return res.data
}
