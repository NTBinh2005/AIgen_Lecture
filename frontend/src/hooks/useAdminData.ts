/** React Query hooks cho luồng Admin. */
import { useQuery } from '@tanstack/react-query'
import { getAllClasses, getAllEnrollments, getAuditLogs, getStatisticsOverview, getUsers } from '@/api/adminApi'
import { getQuizzes } from '@/api/teacherQuizApi'
import type { AuditAction } from '@/types/admin'

export const adminKeys = {
  all: ['admin'] as const,
  users: ['admin', 'users'] as const,
  classes: ['admin', 'classes'] as const,
  enrollments: ['admin', 'enrollments'] as const,
  overview: ['admin', 'overview'] as const,
  quizzes: ['admin', 'quizzes'] as const,
  audit: (f: object) => ['admin', 'audit', f] as const,
}

export const useAdminUsers = () => useQuery({ queryKey: adminKeys.users, queryFn: getUsers })
export const useAdminClasses = () => useQuery({ queryKey: adminKeys.classes, queryFn: getAllClasses })
export const useAdminEnrollments = () => useQuery({ queryKey: adminKeys.enrollments, queryFn: getAllEnrollments })
export const useStatisticsOverview = () => useQuery({ queryKey: adminKeys.overview, queryFn: getStatisticsOverview })
/** ADMIN nhận toàn bộ quiz của mọi giáo viên */
export const useAdminQuizzes = () => useQuery({ queryKey: adminKeys.quizzes, queryFn: () => getQuizzes({ size: 500 }) })

export function useAuditLogs(filters: { userId?: number; action?: AuditAction; resourceType?: string }) {
  return useQuery({ queryKey: adminKeys.audit(filters), queryFn: () => getAuditLogs(filters) })
}
