/**
 * API buổi học live và lịch học cho Student.
 */
import axiosInstance from './axiosInstance'
import type { JoinToken, LiveSession, Schedule } from '@/types/student'

export async function getMyLiveSessions(): Promise<LiveSession[]> {
  const res = await axiosInstance.get<LiveSession[]>('/live-sessions/my')
  return res.data
}

export async function getClassLiveSessions(classId: number): Promise<LiveSession[]> {
  const res = await axiosInstance.get<LiveSession[]>(`/live-sessions/class/${classId}`)
  return res.data
}

/** Chỉ được khi buổi học OPEN/LIVE, nếu không backend trả 403. */
export async function joinLiveSession(sessionId: number): Promise<JoinToken> {
  const res = await axiosInstance.post<JoinToken>(`/live-sessions/${sessionId}/join`)
  return res.data
}

/** Điểm danh bằng mã QR giáo viên hiển thị (204 khi thành công). */
export async function scanAttendanceQr(sessionId: number, code: string): Promise<void> {
  await axiosInstance.post(`/live-sessions/${sessionId}/qr/scan`, { code })
}

export async function getMySchedules(): Promise<Schedule[]> {
  const res = await axiosInstance.get<Schedule[]>('/schedules')
  return res.data
}

export async function getClassSchedules(classId: number): Promise<Schedule[]> {
  const res = await axiosInstance.get<Schedule[]>(`/schedules/class/${classId}`)
  return res.data
}
