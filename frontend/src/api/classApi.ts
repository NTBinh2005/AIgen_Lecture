/**
 * API lớp học cho Student: lớp đã ghi danh, tự đăng ký bằng mã lớp, bài giảng của lớp.
 */
import axiosInstance from './axiosInstance'
import type { ClassDetail, ClassLecture, Enrollment } from '@/types/student'

export async function getMyClasses(): Promise<Enrollment[]> {
  const res = await axiosInstance.get<Enrollment[]>('/students/me/classes')
  return res.data
}

export async function getClassDetail(classId: number): Promise<ClassDetail> {
  const res = await axiosInstance.get<ClassDetail>(`/classes/${classId}`)
  return res.data
}

export async function getClassLectures(classId: number): Promise<ClassLecture[]> {
  const res = await axiosInstance.get<ClassLecture[]>(`/classes/${classId}/lectures`)
  return res.data
}

/** ENRL-06: lớp phải đang ACTIVE, nếu không backend trả 400 kèm message. */
export async function selfEnroll(classCode: string): Promise<Enrollment> {
  const res = await axiosInstance.post<Enrollment>('/classes/self-enroll', { classCode })
  return res.data
}
