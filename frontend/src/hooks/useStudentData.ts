/**
 * React Query hooks cho luồng Student. Các trang chỉ gọi hook, không gọi API trực tiếp.
 */
import { useQueries, useQuery } from '@tanstack/react-query'
import { getClassDetail, getClassLectures, getMyClasses } from '@/api/classApi'
import { getClassAssignments } from '@/api/quizApi'
import { getClassLiveSessions, getClassSchedules, getMyLiveSessions, getMySchedules } from '@/api/liveApi'
import type { ClassLecture, Enrollment, StudentAssignment } from '@/types/student'

export const studentKeys = {
  classes: ['student', 'classes'] as const,
  classDetail: (id: number) => ['student', 'class', id] as const,
  classLectures: (id: number) => ['student', 'class', id, 'lectures'] as const,
  classAssignments: (id: number) => ['student', 'class', id, 'assignments'] as const,
  classLive: (id: number) => ['student', 'class', id, 'live'] as const,
  classSchedules: (id: number) => ['student', 'class', id, 'schedules'] as const,
  liveSessions: ['student', 'live'] as const,
  schedules: ['student', 'schedules'] as const,
}

/** Chỉ lớp có enrollment ACTIVE/COMPLETED (backend đã lọc). */
export function useMyClasses() {
  return useQuery({ queryKey: studentKeys.classes, queryFn: getMyClasses })
}

export function useClassDetail(classId: number) {
  return useQuery({
    queryKey: studentKeys.classDetail(classId),
    queryFn: () => getClassDetail(classId),
    enabled: Number.isFinite(classId),
  })
}

export function useClassLectures(classId: number) {
  return useQuery({
    queryKey: studentKeys.classLectures(classId),
    queryFn: () => getClassLectures(classId),
    enabled: Number.isFinite(classId),
  })
}

export function useClassAssignments(classId: number) {
  return useQuery({
    queryKey: studentKeys.classAssignments(classId),
    queryFn: () => getClassAssignments(classId),
    enabled: Number.isFinite(classId),
  })
}

export function useClassLiveSessions(classId: number) {
  return useQuery({
    queryKey: studentKeys.classLive(classId),
    queryFn: () => getClassLiveSessions(classId),
    enabled: Number.isFinite(classId),
  })
}

export function useClassSchedules(classId: number) {
  return useQuery({
    queryKey: studentKeys.classSchedules(classId),
    queryFn: () => getClassSchedules(classId),
    enabled: Number.isFinite(classId),
  })
}

export function useMyLiveSessions() {
  return useQuery({ queryKey: studentKeys.liveSessions, queryFn: getMyLiveSessions })
}

export function useMySchedules() {
  return useQuery({ queryKey: studentKeys.schedules, queryFn: getMySchedules })
}

export interface LectureWithClass extends ClassLecture {
  classId: number
  className: string
}

export interface AssignmentWithClass extends StudentAssignment {
  className: string
}

/**
 * Gom dữ liệu theo từng lớp đã ghi danh (backend chưa có API tổng hợp cho Student).
 */
function useAcrossClasses<T, R>(
  classes: Enrollment[] | undefined,
  key: (id: number) => readonly unknown[],
  fetcher: (id: number) => Promise<T[]>,
  attach: (item: T, enrollment: Enrollment) => R,
) {
  return useQueries({
    queries: (classes ?? []).map(c => ({ queryKey: key(c.classId), queryFn: () => fetcher(c.classId) })),
    combine: results => ({
      data: results.flatMap((r, i) => (r.data ?? []).map(item => attach(item, classes![i]))),
      isLoading: results.some(r => r.isLoading),
      isError: results.some(r => r.isError),
    }),
  })
}

/** Toàn bộ bài giảng được giao qua các lớp, mới giao trước. */
export function useAllClassLectures() {
  const classesQuery = useMyClasses()
  const lectures = useAcrossClasses(
    classesQuery.data,
    studentKeys.classLectures,
    getClassLectures,
    (l, c): LectureWithClass => ({ ...l, classId: c.classId, className: c.className }),
  )
  return {
    data: [...lectures.data].sort((a, b) => b.assignedAt.localeCompare(a.assignedAt)),
    isLoading: classesQuery.isLoading || lectures.isLoading,
    isError: classesQuery.isError || lectures.isError,
  }
}

/** Toàn bộ bài kiểm tra được giao qua các lớp. */
export function useAllAssignments() {
  const classesQuery = useMyClasses()
  const assignments = useAcrossClasses(
    classesQuery.data,
    studentKeys.classAssignments,
    getClassAssignments,
    (a, c): AssignmentWithClass => ({ ...a, className: c.className }),
  )
  return {
    data: assignments.data,
    isLoading: classesQuery.isLoading || assignments.isLoading,
    isError: classesQuery.isError || assignments.isError,
  }
}
