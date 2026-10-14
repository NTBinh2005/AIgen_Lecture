/**
 * React Query hooks cho luồng Teacher.
 */
import { useMemo } from 'react'
import { useQueries, useQuery } from '@tanstack/react-query'
import { getLectures } from '@/api/lectureApi'
import { getTeacherClasses, getClassStudents } from '@/api/teacherClassApi'
import { getAssignmentProgress, getClassAssignmentsForTeacher, getQuizVersions, getQuizzes } from '@/api/teacherQuizApi'
import { getClassLectures } from '@/api/classApi'
import { getClassLiveSessions, getClassSchedules } from '@/api/liveApi'
import { getClassDetail } from '@/api/classApi'
import type { QuizDetail, QuizVersion } from '@/types/teacher'

export const teacherKeys = {
  all: ['teacher'] as const,
  classes: ['teacher', 'classes'] as const,
  classDetail: (id: number) => ['teacher', 'class', id] as const,
  students: (id: number) => ['teacher', 'class', id, 'students'] as const,
  classLectures: (id: number) => ['teacher', 'class', id, 'lectures'] as const,
  assignments: (id: number) => ['teacher', 'class', id, 'assignments'] as const,
  live: (id: number) => ['teacher', 'class', id, 'live'] as const,
  schedules: (id: number) => ['teacher', 'class', id, 'schedules'] as const,
  progress: (assignmentId: number) => ['teacher', 'progress', assignmentId] as const,
  quizzes: ['teacher', 'quizzes'] as const,
  quizVersions: (quizId: number) => ['teacher', 'quiz', quizId, 'versions'] as const,
  lectures: ['teacher', 'lectures'] as const,
}

export function useTeacherClasses() {
  return useQuery({ queryKey: teacherKeys.classes, queryFn: getTeacherClasses })
}

export function useTeacherClass(classId: number) {
  const enabled = Number.isFinite(classId)
  return {
    detail: useQuery({ queryKey: teacherKeys.classDetail(classId), queryFn: () => getClassDetail(classId), enabled }),
    students: useQuery({ queryKey: teacherKeys.students(classId), queryFn: () => getClassStudents(classId), enabled }),
    lectures: useQuery({ queryKey: teacherKeys.classLectures(classId), queryFn: () => getClassLectures(classId), enabled }),
    assignments: useQuery({ queryKey: teacherKeys.assignments(classId), queryFn: () => getClassAssignmentsForTeacher(classId), enabled }),
    live: useQuery({ queryKey: teacherKeys.live(classId), queryFn: () => getClassLiveSessions(classId), enabled }),
    schedules: useQuery({ queryKey: teacherKeys.schedules(classId), queryFn: () => getClassSchedules(classId), enabled }),
  }
}

export function useAssignmentProgress(assignmentId: number | null) {
  return useQuery({
    queryKey: teacherKeys.progress(assignmentId ?? -1),
    queryFn: () => getAssignmentProgress(assignmentId!),
    enabled: assignmentId != null,
  })
}

/** Toàn bộ quiz của giáo viên (backend phân trang — lấy 200 bản ghi đầu là đủ cho UI). */
export function useTeacherQuizzes() {
  return useQuery({ queryKey: teacherKeys.quizzes, queryFn: () => getQuizzes({ size: 200 }) })
}

/** Bài giảng của giáo viên (tối đa 200) — dùng để chọn khi giao cho lớp. */
export function useTeacherLectures() {
  return useQuery({ queryKey: teacherKeys.lectures, queryFn: () => getLectures({ size: 200, sort: 'createdAt,desc' }) })
}

export interface PublishedQuizVersion extends QuizVersion {
  quizTitle: string
}

/**
 * Map versionId → quiz đã publish. Backend không trả tên quiz trong assignment của giáo viên
 * (FIX.md #20) nên phải tự ghép từ danh sách quiz + versions.
 */
export function useQuizVersionIndex() {
  const quizzes = useTeacherQuizzes()
  const published = (quizzes.data?.content ?? []).filter((q: QuizDetail) => q.status !== 'DRAFT' && q.status !== 'REVIEWED')
  const versions = useQueries({
    queries: published.map(q => ({ queryKey: teacherKeys.quizVersions(q.quizId), queryFn: () => getQuizVersions(q.quizId) })),
    combine: results => ({
      data: results.flatMap((r, i) => (r.data ?? []).map(v => ({ ...v, quizTitle: published[i].title }))),
      isLoading: results.some(r => r.isLoading),
    }),
  })
  const byVersionId = useMemo(
    () => new Map<number, PublishedQuizVersion>(versions.data.map(v => [v.versionId, v])),
    [versions.data],
  )
  return {
    versions: versions.data as PublishedQuizVersion[],
    byVersionId,
    quizzes: quizzes.data?.content ?? [],
    isLoading: quizzes.isLoading || versions.isLoading,
  }
}
