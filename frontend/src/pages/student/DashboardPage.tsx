import { Link } from 'react-router-dom'
import { PlaySquare, BookOpen, Sparkles, Clock, TrendingUp, RefreshCw, ArrowRight } from 'lucide-react'
import { useQuery } from '@tanstack/react-query'
import { getStudentLectures } from '@/api/lectureApi'
import { motion, type Variants } from 'framer-motion'
import { useAuthStore } from '@/store/authStore'

const containerVariants: Variants = {
  hidden: { opacity: 0 },
  show: { opacity: 1, transition: { staggerChildren: 0.08 } }
}

const itemVariants: Variants = {
  hidden: { opacity: 0, y: 24 },
  show: { opacity: 1, y: 0, transition: { type: 'spring', stiffness: 300, damping: 24 } }
}

export default function StudentDashboard() {
  const { user } = useAuthStore()
  const { data: lecturesPage, isLoading } = useQuery({
    queryKey: ['studentLectures'],
    queryFn: () => getStudentLectures({ size: 3 }),
  })

  const lectures = lecturesPage?.content || []
  const firstName = user?.name?.split(' ').pop() || 'bạn'

  return (
    <motion.div
      className="space-y-6 max-w-5xl mx-auto"
      variants={containerVariants}
      initial="hidden"
      animate="show"
    >
      {/* ── HEADER ── */}
      <motion.div variants={itemVariants}>
        <p className="text-sm font-medium text-muted-foreground mb-1">Hôm nay, {new Date().toLocaleDateString('vi-VN', { weekday: 'long', day: 'numeric', month: 'long' })}</p>
        <h1 className="text-3xl md:text-4xl font-bold tracking-tight">
          Chào <span className="text-primary">{firstName}</span> 👋
        </h1>
        <p className="text-muted-foreground mt-2 text-base">Tiếp tục hành trình học tập của bạn hôm nay.</p>
      </motion.div>

      {/* ── STATS BENTO ── */}
      <motion.div variants={containerVariants} className="grid grid-cols-1 sm:grid-cols-3 gap-4">
        {[
          {
            icon: BookOpen,
            label: 'Khóa học đang học',
            value: '3',
            color: 'text-primary',
            bg: 'bg-primary/10 dark:bg-primary/15',
            sub: 'Đang tiến hành',
            subColor: 'text-primary',
          },
          {
            icon: PlaySquare,
            label: 'Video đã xem',
            value: '12',
            color: 'text-violet-500',
            bg: 'bg-violet-500/10 dark:bg-violet-500/15',
            sub: '+3 tuần này',
            subColor: 'text-emerald-500',
          },
          {
            icon: TrendingUp,
            label: 'Điểm trung bình',
            value: '8.5',
            color: 'text-emerald-500',
            bg: 'bg-emerald-500/10 dark:bg-emerald-500/15',
            sub: 'Xếp hạng tốt',
            subColor: 'text-emerald-500',
          },
        ].map((stat) => (
          <motion.div
            key={stat.label}
            variants={itemVariants}
            whileHover={{ y: -4, transition: { type: 'spring', stiffness: 400, damping: 20 } }}
            className="rounded-2xl border border-border/50 bg-card dark:bg-card/70 dark:backdrop-blur-sm p-6 group hover:border-primary/25 hover:shadow-lg transition-shadow duration-300 cursor-default"
          >
            <div className={`w-12 h-12 rounded-xl flex items-center justify-center ${stat.bg} ${stat.color} mb-4 group-hover:scale-110 transition-transform duration-300`}>
              <stat.icon size={24} aria-hidden="true" />
            </div>
            <p className="text-sm font-medium text-muted-foreground mb-1">{stat.label}</p>
            <p className="text-3xl font-bold text-foreground">{stat.value}</p>
            <p className={`text-xs font-medium mt-2 ${stat.subColor}`}>{stat.sub}</p>
          </motion.div>
        ))}
      </motion.div>

      {/* ── RECENT LECTURES ── */}
      <motion.div variants={itemVariants}>
        <div className="flex items-center justify-between mb-5">
          <div>
            <h2 className="text-xl font-bold text-foreground">Bài giảng mới nhất</h2>
            <p className="text-sm text-muted-foreground mt-0.5">Khám phá các bài giảng vừa được tạo</p>
          </div>
          <Link
            to="/student/lectures"
            className="flex items-center gap-1.5 text-sm font-semibold text-primary hover:text-primary/80 transition-colors group/link"
          >
            Xem tất cả
            <ArrowRight size={15} className="group-hover/link:translate-x-0.5 transition-transform" />
          </Link>
        </div>

        {isLoading ? (
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-5">
            {Array.from({ length: 3 }).map((_, i) => (
              <div key={i} className="rounded-2xl border border-border/50 bg-card overflow-hidden">
                <div className="aspect-video bg-muted animate-pulse" />
                <div className="p-5 space-y-3">
                  <div className="h-4 bg-muted rounded-lg animate-pulse w-3/4" />
                  <div className="h-3 bg-muted rounded-lg animate-pulse w-1/2" />
                </div>
              </div>
            ))}
          </div>
        ) : lectures.length === 0 ? (
          <div className="text-center py-16 rounded-2xl border border-dashed border-border bg-card/50">
            <BookOpen className="w-12 h-12 text-muted-foreground/40 mx-auto mb-4" />
            <p className="text-muted-foreground font-medium">Chưa có bài giảng nào được tạo.</p>
            <p className="text-sm text-muted-foreground/70 mt-1">Giáo viên sẽ sớm đăng bài lên đây!</p>
          </div>
        ) : (
          <motion.div
            className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-5"
            variants={containerVariants}
          >
            {lectures.map((lecture) => (
              <motion.div key={lecture.lectureId} variants={itemVariants}>
                <Link to={`/student/lectures/${lecture.lectureId}`} className="group block h-full">
                  <div className="h-full rounded-2xl border border-border/50 bg-card dark:bg-card/70 dark:backdrop-blur-sm overflow-hidden
                    hover:border-primary/35 hover:shadow-xl hover:shadow-primary/8
                    dark:hover:shadow-primary/15 transition-all duration-300 bento-card">

                    {/* Thumbnail */}
                    <div className="aspect-video bg-muted relative overflow-hidden">
                      <div className="absolute inset-0 bg-gradient-to-br from-primary/20 via-violet-500/15 to-indigo-500/20
                        flex items-center justify-center group-hover:scale-105 transition-transform duration-500">
                        <div className="w-14 h-14 rounded-2xl bg-background/70 dark:bg-background/50 backdrop-blur-sm border border-border/50
                          flex items-center justify-center shadow-lg group-hover:scale-110 transition-transform duration-300">
                          <PlaySquare size={28} className="text-primary ml-0.5" />
                        </div>
                      </div>

                      {/* Status badge */}
                      {lecture.videoStatus === 'DONE' ? (
                        <div className="absolute top-3 left-3 bg-emerald-500/90 backdrop-blur-sm text-white text-xs font-semibold px-2.5 py-1 rounded-full flex items-center gap-1.5 shadow-md">
                          <Sparkles size={11} />
                          Sẵn sàng
                        </div>
                      ) : (
                        <div className="absolute top-3 left-3 bg-amber-500/90 backdrop-blur-sm text-white text-xs font-semibold px-2.5 py-1 rounded-full flex items-center gap-1.5 shadow-md">
                          <RefreshCw size={11} className="animate-spin" />
                          Đang tạo...
                        </div>
                      )}

                      {/* Date badge */}
                      <div className="absolute bottom-3 right-3 bg-black/60 backdrop-blur-sm text-white text-xs font-medium px-2 py-1 rounded-lg flex items-center gap-1">
                        <Clock size={11} />
                        {new Date(lecture.createdAt).toLocaleDateString('vi-VN')}
                      </div>
                    </div>

                    {/* Card body */}
                    <div className="p-5">
                      <h3 className="font-bold text-base text-foreground mb-1 group-hover:text-primary transition-colors line-clamp-2 leading-snug">
                        {lecture.title}
                      </h3>
                      <p className="text-sm text-muted-foreground">
                        GV: {lecture.teacherName}
                      </p>

                      {/* Progress bar */}
                      <div className="mt-4">
                        <div className="flex justify-between items-center mb-1.5">
                          <p className="text-xs font-medium text-muted-foreground">Tiến độ</p>
                          <p className="text-xs font-semibold text-muted-foreground">0%</p>
                        </div>
                        <div className="w-full bg-muted/60 rounded-full h-1.5 overflow-hidden">
                          <div className="bg-gradient-to-r from-primary to-violet-500 h-1.5 rounded-full" style={{ width: '0%' }} />
                        </div>
                      </div>
                    </div>
                  </div>
                </Link>
              </motion.div>
            ))}
          </motion.div>
        )}
      </motion.div>
    </motion.div>
  )
}