import { FileUp, BookOpen, Users, CheckCircle, Sparkles, ChevronRight, MoreVertical, Plus, CalendarDays, ClipboardList, School } from 'lucide-react'
import { Link } from 'react-router-dom'
import { Button } from '@/components/ui/button'
import { useQuery, useQueries, useMutation, useQueryClient } from '@tanstack/react-query'
import { getLectures, deleteLecture, updateLecture } from '@/api/lectureApi'
import { getMyLiveSessions } from '@/api/liveApi'
import { getClassStudents } from '@/api/teacherClassApi'
import { teacherKeys, useTeacherClasses, useTeacherQuizzes } from '@/hooks/useTeacherData'
import { formatDateTime, parseDate } from '@/lib/format'
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger } from '@/components/ui/dropdown-menu'
import { Skeleton } from '@/components/ui/skeleton'
import { LectureStatusBadge } from '@/components/common/LectureStatusBadge'
import { motion, type Variants } from 'framer-motion'
import { useAuthStore } from '@/store/authStore'

// Framer Motion variants
const containerVariants: Variants = {
  hidden: { opacity: 0 },
  show: {
    opacity: 1,
    transition: { staggerChildren: 0.08 }
  }
}

const itemVariants: Variants = {
  hidden: { opacity: 0, y: 24 },
  show: { opacity: 1, y: 0, transition: { type: 'spring', stiffness: 300, damping: 24 } }
}

export default function TeacherDashboard() {
  const queryClient = useQueryClient()
  const { user } = useAuthStore()

  const { data: lecturesPage, isLoading } = useQuery({
    queryKey: ['lectures', 'dashboard'],
    queryFn: () => getLectures({ page: 0, size: 5, sort: 'createdAt,desc' })
  })

  const deleteMutation = useMutation({
    mutationFn: deleteLecture,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['lectures'] })
  })

  const updateMutation = useMutation({
    mutationFn: ({ id, title }: { id: number, title: string }) => updateLecture(id, { title }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['lectures'] })
  })

  const handleDelete = (id: number) => {
    if (window.confirm('Bạn có chắc chắn muốn xóa bài giảng này?')) {
      deleteMutation.mutate(id)
    }
  }

  const handleEdit = (id: number, currentTitle: string) => {
    const newTitle = window.prompt('Nhập tên mới cho bài giảng:', currentTitle)
    if (newTitle && newTitle.trim() !== '' && newTitle !== currentTitle) {
      updateMutation.mutate({ id, title: newTitle.trim() })
    }
  }

  const lectures = lecturesPage?.content || []

  // ── Số liệu thật cho các ô thống kê ──
  const classes = useTeacherClasses()
  const quizzes = useTeacherQuizzes()
  const live = useQuery({ queryKey: ['teacher', 'live', 'my'], queryFn: getMyLiveSessions })
  const activeClasses = (classes.data ?? []).filter(c => c.status === 'ACTIVE')
  const studentCounts = useQueries({
    queries: activeClasses.map(c => ({ queryKey: teacherKeys.students(c.classId), queryFn: () => getClassStudents(c.classId) })),
    combine: results => ({
      // Một học sinh có thể học nhiều lớp → đếm theo studentId duy nhất
      total: new Set(results.flatMap(r => (r.data ?? []).filter(e => e.status === 'ACTIVE').map(e => e.studentId))).size,
      isLoading: results.some(r => r.isLoading),
    }),
  })
  const quizList = quizzes.data?.content ?? []
  const draftQuizzes = quizzes.isLoading ? '—' : quizList.filter(q => q.status === 'DRAFT' || q.status === 'REVIEWED').length
  const now = new Date()
  const nextSession = (live.data ?? [])
    .filter(s => s.status === 'LIVE' || s.status === 'OPEN' || (s.status === 'SCHEDULED' && (parseDate(s.endsAt) ?? now) >= now))
    .sort((a, b) => a.startsAt.localeCompare(b.startsAt))[0]

  const stats = [
    { title: 'Tổng số bài giảng', value: isLoading ? '—' : lecturesPage?.totalElements ?? 0, icon: BookOpen, sub: 'Đã tạo', color: 'text-primary', bg: 'bg-primary/10 dark:bg-primary/15', to: '/teacher/lectures' },
    { title: 'Lớp đang hoạt động', value: classes.isLoading ? '—' : activeClasses.length, icon: School, sub: `${classes.data?.length ?? 0} lớp tất cả`, color: 'text-chart-1', bg: 'bg-chart-1/10 dark:bg-chart-1/15', to: '/teacher/classes' },
    { title: 'Học sinh đang học', value: classes.isLoading || studentCounts.isLoading ? '—' : studentCounts.total, icon: Users, sub: 'Trong các lớp đang hoạt động', color: 'text-emerald-500', bg: 'bg-emerald-500/10 dark:bg-emerald-500/15', to: '/teacher/classes' },
    { title: 'Quiz đã xuất bản', value: quizzes.isLoading ? '—' : quizList.filter(q => q.status === 'PUBLISHED').length, icon: CheckCircle, sub: 'Sẵn sàng giao cho lớp', color: 'text-amber-500', bg: 'bg-amber-500/10 dark:bg-amber-500/15', to: '/teacher/quizzes' },
  ]

  const firstName = user?.name?.split(' ').pop() || 'Thầy/Cô'

  return (
    <motion.div
      className="space-y-6 max-w-6xl mx-auto"
      variants={containerVariants}
      initial="hidden"
      animate="show"
    >

      {/* ── HEADER ── */}
      <motion.div variants={itemVariants} className="flex flex-col sm:flex-row sm:items-end justify-between gap-4">
        <div>
          <p className="text-sm font-medium text-muted-foreground mb-1">Chào mừng trở lại 👋</p>
          <h1 className="text-3xl md:text-4xl font-bold text-foreground tracking-tight">
            Xin chào, <span className="text-primary">{firstName}</span>
          </h1>
        </div>
        <Link to="/teacher/lectures/create">
          <motion.div whileHover={{ scale: 1.03 }} whileTap={{ scale: 0.97 }}>
            <Button className="flex items-center gap-2 h-10 px-5 shadow-lg shadow-primary/20 hover:shadow-primary/30 transition-shadow">
              <Plus size={16} aria-hidden="true" />
              Tạo bài giảng mới
            </Button>
          </motion.div>
        </Link>
      </motion.div>

      {/* ── BENTO HERO + STATS ── */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-4">

        {/* Hero CTA — chiếm 2/3 trên desktop */}
        <motion.div
          variants={itemVariants}
          className="lg:col-span-2 relative overflow-hidden rounded-2xl border border-primary/20 dark:border-primary/15
            bg-gradient-to-br from-primary/8 via-primary/4 to-background
            dark:from-primary/12 dark:via-primary/6 dark:to-background
            p-7 sm:p-9 group"
        >
          {/* Background sparkle icon */}
          <div className="absolute top-0 right-0 p-8 opacity-[0.07] pointer-events-none" aria-hidden="true">
            <Sparkles className="w-56 h-56 text-primary animate-glow-pulse" />
          </div>

          {/* Dark glow ring */}
          <div className="absolute inset-0 rounded-2xl opacity-0 dark:opacity-100 pointer-events-none
            bg-gradient-to-br from-primary/5 via-transparent to-violet-500/5" />

          <div className="relative z-10">
            <div className="inline-flex items-center gap-2 px-3 py-1.5 rounded-full bg-primary/10 dark:bg-primary/15 text-primary text-xs font-semibold mb-5 border border-primary/20">
              <Sparkles size={12} aria-hidden="true" />
              AI Lecture Generation
            </div>
            <h2 className="text-2xl sm:text-3xl font-bold text-foreground mb-3 leading-tight">
              Tạo bài giảng tương tác<br className="hidden sm:block" />
              <span className="text-primary"> chỉ trong 15 giây</span>
            </h2>
            <p className="text-muted-foreground text-base mb-7 max-w-lg leading-relaxed">
              Tải lên tài liệu PDF hoặc Word — AI sẽ tự động trích xuất nội dung và sinh ra Slide kèm câu hỏi trắc nghiệm thông minh.
            </p>
            <Link to="/teacher/lectures/create">
              <motion.div whileHover={{ scale: 1.02 }} whileTap={{ scale: 0.98 }} className="inline-block">
                <Button size="lg" className="h-12 px-7 rounded-xl shadow-xl shadow-primary/25 hover:shadow-primary/35 transition-shadow font-semibold group/btn">
                  <FileUp className="mr-2 h-4 w-4 group-hover/btn:-translate-y-0.5 transition-transform" aria-hidden="true" />
                  Tải lên tài liệu (PDF / DOCX)
                </Button>
              </motion.div>
            </Link>
          </div>
        </motion.div>

        {/* Quick Stats mini-card — 1/3 */}
        <motion.div variants={itemVariants} className="flex flex-col gap-3">
          <Link to="/teacher/classes" className="rounded-2xl border border-border/50 bg-card dark:bg-card/70 dark:backdrop-blur-sm p-5 flex-1 flex flex-col justify-between group hover:border-primary/30 transition-all duration-300 bento-card">
            <div className="flex items-center justify-between mb-2">
              <p className="text-xs font-semibold text-muted-foreground uppercase tracking-wider">Buổi học sắp tới</p>
              <CalendarDays size={16} className="text-amber-500" />
            </div>
            {nextSession ? (
              <div>
                <p className="font-semibold text-foreground line-clamp-1">{nextSession.title}</p>
                <p className="text-sm text-muted-foreground">{nextSession.className} · {formatDateTime(nextSession.startsAt)}</p>
              </div>
            ) : (
              <p className="text-sm text-muted-foreground">Chưa có buổi học nào sắp tới</p>
            )}
          </Link>
          <Link to="/teacher/quizzes" className="rounded-2xl border border-border/50 bg-card dark:bg-card/70 dark:backdrop-blur-sm p-5 flex-1 flex flex-col justify-between group hover:border-primary/30 transition-all duration-300 bento-card">
            <div className="flex items-center justify-between mb-2">
              <p className="text-xs font-semibold text-muted-foreground uppercase tracking-wider">Quiz nháp</p>
              <ClipboardList size={16} className="text-primary" />
            </div>
            <p className="text-4xl font-bold text-foreground">{draftQuizzes}</p>
            <p className="text-sm text-muted-foreground mt-1">Chưa xuất bản</p>
          </Link>
        </motion.div>
      </div>

      {/* ── STATS GRID (4 ô) ── */}
      <motion.div variants={containerVariants} className="grid grid-cols-2 lg:grid-cols-4 gap-4">
        {stats.map((stat) => (
          <motion.div key={stat.title} variants={itemVariants}>
            <Link
              to={stat.to}
              className="block h-full rounded-2xl border border-border/50 bg-card dark:bg-card/70 dark:backdrop-blur-sm p-5 hover:border-primary/25 hover:shadow-lg transition-shadow duration-300 group"
            >
              <div className={`w-11 h-11 rounded-xl flex items-center justify-center ${stat.bg} ${stat.color} mb-4 group-hover:scale-110 transition-transform duration-300`}>
                <stat.icon size={22} strokeWidth={2} aria-hidden="true" />
              </div>
              <p className="text-xs font-medium text-muted-foreground mb-1 leading-tight">{stat.title}</p>
              <h3 className="text-2xl font-bold text-foreground tracking-tight">{stat.value}</h3>
              <p className="text-xs font-medium mt-2 text-muted-foreground">{stat.sub}</p>
            </Link>
          </motion.div>
        ))}
      </motion.div>

      {/* ── RECENT LECTURES TABLE ── */}
      <motion.div
        variants={itemVariants}
        className="rounded-2xl border border-border/50 bg-card dark:bg-card/70 dark:backdrop-blur-sm shadow-sm overflow-hidden"
      >
        <div className="p-6 border-b border-border/50 flex flex-col sm:flex-row sm:items-center justify-between gap-4">
          <div>
            <h2 className="text-lg font-bold text-foreground">Bài giảng gần đây</h2>
            <p className="text-sm text-muted-foreground mt-0.5">Quản lý và chỉnh sửa các bài giảng đã tạo</p>
          </div>
          <Link
            to="/teacher/lectures"
            className="text-sm font-semibold text-primary hover:text-primary/80 flex items-center gap-1 transition-colors group/link"
          >
            Xem tất cả
            <ChevronRight size={16} className="group-hover/link:translate-x-0.5 transition-transform" aria-hidden="true" />
          </Link>
        </div>

        <div className="overflow-x-auto">
          <table className="w-full text-left border-collapse">
            <thead>
              <tr className="bg-muted/25 dark:bg-muted/10">
                <th className="py-3.5 px-6 text-xs font-semibold text-muted-foreground uppercase tracking-wider">Tên bài giảng</th>
                <th className="py-3.5 px-6 text-xs font-semibold text-muted-foreground uppercase tracking-wider">Trạng thái</th>
                <th className="py-3.5 px-6 text-xs font-semibold text-muted-foreground uppercase tracking-wider hidden md:table-cell">Ngày tạo</th>
                <th className="py-3.5 px-6 text-right text-xs font-semibold text-muted-foreground uppercase tracking-wider">Thao tác</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-border/40">
              {isLoading ? (
                Array.from({ length: 3 }).map((_, i) => (
                  <tr key={i}>
                    <td colSpan={4} className="py-3 px-6">
                      <Skeleton className="h-8 w-full rounded-lg" />
                    </td>
                  </tr>
                ))
              ) : lectures.length === 0 ? (
                <tr>
                  <td colSpan={4} className="py-12 text-center text-muted-foreground text-sm">
                    Chưa có bài giảng nào.{' '}
                    <Link to="/teacher/lectures/create" className="text-primary font-semibold hover:underline">
                      Tạo ngay!
                    </Link>
                  </td>
                </tr>
              ) : (
                lectures.map((lecture) => (
                  <tr key={lecture.lectureId} className="hover:bg-muted/15 dark:hover:bg-muted/10 transition-colors group">
                    <td className="py-4 px-6">
                      <div className="font-semibold text-foreground group-hover:text-primary transition-colors">{lecture.title}</div>
                      <div className="text-xs text-muted-foreground md:hidden mt-1">{new Date(lecture.createdAt).toLocaleDateString('vi-VN')}</div>
                    </td>
                    <td className="py-4 px-6">
                      <LectureStatusBadge status={lecture.status} className="px-2.5 py-1 font-medium" />
                    </td>
                    <td className="py-4 px-6 text-sm text-muted-foreground hidden md:table-cell">
                      {new Date(lecture.createdAt).toLocaleDateString('vi-VN')}
                    </td>
                    <td className="py-4 px-6 text-right">
                      <DropdownMenu>
                        <DropdownMenuTrigger asChild>
                          <button
                            className="p-2 text-muted-foreground hover:bg-muted rounded-lg transition-colors opacity-0 group-hover:opacity-100 focus:opacity-100"
                            aria-label="More options"
                          >
                            <MoreVertical size={16} aria-hidden="true" />
                          </button>
                        </DropdownMenuTrigger>
                        <DropdownMenuContent align="end">
                          <DropdownMenuItem onClick={() => handleEdit(lecture.lectureId, lecture.title)}>Sửa tên</DropdownMenuItem>
                          <DropdownMenuItem className="text-destructive focus:text-destructive" onClick={() => handleDelete(lecture.lectureId)}>Xóa</DropdownMenuItem>
                        </DropdownMenuContent>
                      </DropdownMenu>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </motion.div>
    </motion.div>
  )
}