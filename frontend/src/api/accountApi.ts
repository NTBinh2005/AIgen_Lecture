/**
 * API thông báo và cài đặt tài khoản (dùng chung mọi role).
 */
import axiosInstance from './axiosInstance'
import { fetchPage } from './pagination'
import type { AppNotification, Profile } from '@/types/student'

/** 30 thông báo mới nhất (backend phân trang, mặc định mới nhất trước). */
export async function getNotifications(): Promise<AppNotification[]> {
  return (await fetchPage<AppNotification>('/notifications', { page: 0, size: 30 })).content
}

export async function getUnreadCount(): Promise<number> {
  const res = await axiosInstance.get<{ unreadCount: number }>('/notifications/unread-count')
  return res.data.unreadCount
}

export async function markNotificationRead(id: number): Promise<void> {
  await axiosInstance.patch(`/notifications/${id}/read`)
}

export async function markAllNotificationsRead(): Promise<void> {
  await axiosInstance.patch('/notifications/read-all')
}

export async function getProfile(): Promise<Profile> {
  const res = await axiosInstance.get<Profile>('/settings/profile')
  return res.data
}

export async function updateProfile(payload: { name: string; email: string }): Promise<Profile> {
  const res = await axiosInstance.put<Profile>('/settings/profile', payload)
  return res.data
}

// export async function changePassword(payload: { currentPassword: string; newPassword: string }): Promise<void> {
//   await axiosInstance.put('/settings/password', payload)
// }

export async function changePassword(payload: { currentPassword: string; newPassword: string }): Promise<void> {
  await axiosInstance.put('/settings/password', payload)
}