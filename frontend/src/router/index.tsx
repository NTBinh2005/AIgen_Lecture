import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import { useAuthStore, type Role } from '@/store/authStore'

import LandingPage from '@/pages/landing/LandingPage'
import TeacherDashboard from '@/pages/teacher/DashboardPage'
import CreateLecturePage from '@/pages/teacher/CreateLecturePage'
import TeacherLecturesPage from '@/pages/teacher/LecturesPage'
import EditLecturePage from '@/pages/teacher/EditLecturePage'
import TeacherClassesPage from '@/pages/teacher/ClassesPage'
import TeacherClassDetailPage from '@/pages/teacher/ClassDetailPage'
import TeacherQuizzesPage from '@/pages/teacher/QuizzesPage'
import QuizEditorPage from '@/pages/teacher/QuizEditorPage'
import TeacherSettingsPage from '@/pages/teacher/SettingsPage'
import { TeacherLayout } from '@/components/layout/TeacherLayout'
import StudentDashboard from '@/pages/student/DashboardPage'
import StudentLecturesPage from '@/pages/student/LecturesPage'
import WatchLecturePage from '@/pages/student/WatchLecturePage'
import StudentClassesPage from '@/pages/student/ClassesPage'
import StudentClassDetailPage from '@/pages/student/ClassDetailPage'
import QuizAttemptPage from '@/pages/student/QuizAttemptPage'
import StudentSchedulePage from '@/pages/student/SchedulePage'
import StudentSettingsPage from '@/pages/student/SettingsPage'
import { StudentLayout } from '@/components/layout/StudentLayout'
import AdminDashboard from '@/pages/admin/DashboardPage'
import AdminStatisticsPage from '@/pages/admin/StatisticsPage'
import AdminSettingsPage from '@/pages/admin/SettingsPage'
import AdminUsersPage from '@/pages/admin/UsersPage'
import AdminClassesPage from '@/pages/admin/ClassesPage'
import AdminQuizzesPage from '@/pages/admin/QuizzesPage'
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
                <Route path="lectures/:lectureId/edit" element={<EditLecturePage />} />
                <Route path="classes" element={<TeacherClassesPage />} />
                <Route path="classes/:classId" element={<TeacherClassDetailPage />} />
                <Route path="quizzes" element={<TeacherQuizzesPage />} />
                <Route path="quizzes/new" element={<QuizEditorPage />} />
                <Route path="quizzes/:quizId" element={<QuizEditorPage />} />
                <Route path="settings" element={<TeacherSettingsPage />} />
                {/* Trang thống kê cũ dùng số liệu giả của Admin → đưa về Tổng quan */}
                <Route path="analytics" element={<Navigate to="/teacher" replace />} />
                <Route path="*" element={<Navigate to="/teacher" replace />} />
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
                <Route path="classes" element={<StudentClassesPage />} />
                <Route path="classes/:classId" element={<StudentClassDetailPage />} />
                <Route path="quizzes/:assignmentId" element={<QuizAttemptPage />} />
                <Route path="schedule" element={<StudentSchedulePage />} />
                <Route path="settings" element={<StudentSettingsPage />} />
                {/* Link cũ trong sidebar */}
                <Route path="courses" element={<Navigate to="/student/classes" replace />} />
                <Route path="*" element={<Navigate to="/student" replace />} />
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
                <Route path="classes" element={<AdminClassesPage />} />
                <Route path="classes/:classId" element={<TeacherClassDetailPage backHref="/admin/classes" />} />
                <Route path="quizzes" element={<AdminQuizzesPage />} />
                <Route path="logs" element={<SystemLogsPage />} />
                <Route path="settings" element={<AdminSettingsPage />} />
                {/* Cấu hình AI chưa có API backend → đưa về Tổng quan */}
                <Route path="ai-settings" element={<Navigate to="/admin" replace />} />
                <Route path="*" element={<Navigate to="/admin" replace />} />
              </Routes>
            </AdminLayout>
          </ProtectedRoute>
        } />

        <Route path="*" element={<Navigate to="/" />} />
      </Routes>
    </BrowserRouter>
  )
}