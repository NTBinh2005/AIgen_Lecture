import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import { useAuthStore, type Role } from '@/store/authStore'

import LandingPage from '@/pages/landing/LandingPage'
import TeacherDashboard from '@/pages/teacher/DashboardPage'
import CreateLecturePage from '@/pages/teacher/CreateLecturePage'
import TeacherLecturesPage from '@/pages/teacher/LecturesPage'
import { TeacherLayout } from '@/components/layout/TeacherLayout'
import StudentDashboard from '@/pages/student/DashboardPage'
import StudentLecturesPage from '@/pages/student/LecturesPage'
import WatchLecturePage from '@/pages/student/WatchLecturePage'
import { StudentLayout } from '@/components/layout/StudentLayout'
import AdminDashboard from '@/pages/admin/DashboardPage'
import AdminStatisticsPage from '@/pages/admin/StatisticsPage'
import AdminSettingsPage from '@/pages/admin/SettingsPage'
import AdminUsersPage from '@/pages/admin/UsersPage'
import AiSettingsPage from '@/pages/admin/AiSettingsPage'
import SystemLogsPage from '@/pages/admin/SystemLogsPage'
import { AdminLayout } from '@/components/layout/AdminLayout'

/**
 * ProtectedRoute — guards routes by authentication status and role.
 * - Unauthenticated users → redirect to login
 * - Authenticated but wrong role → redirect to home (403-like behavior)
 */
function ProtectedRoute({ children, role }: { children: React.ReactNode; role: Role }) {
  const { isAuthenticated, user } = useAuthStore()

  if (!isAuthenticated) return <Navigate to="/?login=true" replace />
  if (user?.role !== role) return <Navigate to="/" replace />

  return <>{children}</>
}

export default function AppRouter() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<LandingPage />} />
        <Route path="/login" element={<Navigate to="/?login=true" />} />

        <Route path="/teacher/*" element={
          <ProtectedRoute role="TEACHER">
            <TeacherLayout>
              <Routes>
                <Route path="" element={<TeacherDashboard />} />
                <Route path="lectures" element={<TeacherLecturesPage />} />
                <Route path="lectures/create" element={<CreateLecturePage />} />
                <Route path="lectures/:lectureId" element={<WatchLecturePage />} />
                <Route path="analytics" element={<AdminStatisticsPage />} />
                <Route path="settings" element={<AdminSettingsPage />} />
                {/* Các sub-routes khác của teacher sẽ thêm vào đây */}
              </Routes>
            </TeacherLayout>
          </ProtectedRoute>
        } />

        <Route path="/student/*" element={
          <ProtectedRoute role="STUDENT">
            <StudentLayout>
              <Routes>
                <Route path="" element={<StudentDashboard />} />
                <Route path="lectures" element={<StudentLecturesPage />} />
                <Route path="lectures/:lectureId" element={<WatchLecturePage />} />
              </Routes>
            </StudentLayout>
          </ProtectedRoute>
        } />

        <Route path="/admin/*" element={
          <ProtectedRoute role="ADMIN">
            <AdminLayout>
              <Routes>
                <Route path="" element={<AdminDashboard />} />
                <Route path="users" element={<AdminUsersPage />} />
                <Route path="statistics" element={<AdminStatisticsPage />} />
                <Route path="ai-settings" element={<AiSettingsPage />} />
                <Route path="logs" element={<SystemLogsPage />} />
                <Route path="settings" element={<AdminSettingsPage />} />
              </Routes>
            </AdminLayout>
          </ProtectedRoute>
        } />

        <Route path="*" element={<Navigate to="/" />} />
      </Routes>
    </BrowserRouter>
  )
}