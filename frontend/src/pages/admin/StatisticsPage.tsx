import { motion, type Variants } from 'framer-motion'
import {
  AreaChart, Area, BarChart, Bar, PieChart, Pie, Cell,
  XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer, Legend
} from 'recharts'
import {
  Users, BookOpen, BrainCircuit, TrendingUp,
  ArrowUpRight, ArrowDownRight, Clock, Star
} from 'lucide-react'

// ── MOCK DATA ─────────────────────────────────────────────────────────────────
const userGrowthData = [
  { month: 'T1', students: 82, teachers: 12 },
  { month: 'T2', students: 120, teachers: 18 },
  { month: 'T3', students: 155, teachers: 22 },
  { month: 'T4', students: 190, teachers: 25 },
  { month: 'T5', students: 248, teachers: 31 },
  { month: 'T6', students: 310, teachers: 38 },
  { month: 'T7', students: 405, teachers: 44 },
  { month: 'T8', students: 520, teachers: 52 },
  { month: 'T9', students: 680, teachers: 60 },
  { month: 'T10', students: 820, teachers: 71 },
  { month: 'T11', students: 980, teachers: 85 },
  { month: 'T12', students: 1160, teachers: 98 },
]

const lectureData = [
  { month: 'T1', lectures: 15, aiGenerated: 10 },
  { month: 'T2', lectures: 28, aiGenerated: 22 },
  { month: 'T3', lectures: 42, aiGenerated: 38 },
  { month: 'T4', lectures: 60, aiGenerated: 55 },
  { month: 'T5', lectures: 88, aiGenerated: 82 },
  { month: 'T6', lectures: 110, aiGenerated: 105 },
  { month: 'T7', lectures: 145, aiGenerated: 140 },
  { month: 'T8', lectures: 180, aiGenerated: 172 },
  { month: 'T9', lectures: 225, aiGenerated: 218 },
  { month: 'T10', lectures: 270, aiGenerated: 264 },
  { month: 'T11', lectures: 320, aiGenerated: 312 },
  { month: 'T12', lectures: 380, aiGenerated: 370 },
]

const roleDistribution = [
  { name: 'Học sinh', value: 1160, color: '#10b981' },
  { name: 'Giáo viên', value: 98, color: '#8b5cf6' },
  { name: 'Admin', value: 5, color: '#f59e0b' },
]

const interactionData = [
  { day: 'T2', quizzes: 120, flashcards: 85, notes: 45 },
  { day: 'T3', quizzes: 185, flashcards: 120, notes: 60 },
  { day: 'T4', quizzes: 145, flashcards: 95, notes: 55 },
  { day: 'T5', quizzes: 220, flashcards: 160, notes: 72 },
  { day: 'T6', quizzes: 198, flashcards: 145, notes: 80 },
  { day: 'T7', quizzes: 260, flashcards: 190, notes: 95 },
  { day: 'CN', quizzes: 170, flashcards: 110, notes: 50 },
]

const topLectures = [
  { title: 'Giải tích nâng cao', teacher: 'Nguyễn Văn A', views: 842, rating: 4.9 },
  { title: 'Lập trình Python cơ bản', teacher: 'Trần Thị B', views: 761, rating: 4.8 },
  { title: 'Vật lý lượng tử', teacher: 'Lê Hoàng C', views: 695, rating: 4.7 },
  { title: 'Tiếng Anh giao tiếp B2', teacher: 'Phạm D', views: 620, rating: 4.7 },
  { title: 'Machine Learning cơ bản', teacher: 'Hoàng E', views: 580, rating: 4.6 },
]

const kpiCards = [
  {
    label: 'Tổng người dùng',
    value: '1,263',
    change: '+12.4%',
    isPositive: true,
    icon: Users,
    bg: 'bg-violet-500/10 dark:bg-violet-500/15',
    color: 'text-violet-500',
    sub: 'so với tháng trước',
  },
  {
    label: 'Bài giảng AI',
    value: '842',
    change: '+28.5%',
    isPositive: true,
    icon: BookOpen,
    bg: 'bg-primary/10 dark:bg-primary/15',
    color: 'text-primary',
    sub: 'tổng bài giảng đã tạo',
  },
  {
    label: 'Chi phí LLM',
    value: '$142.50',
    change: '-5.2%',
    isPositive: true,
    icon: BrainCircuit,
    bg: 'bg-emerald-500/10 dark:bg-emerald-500/15',
    color: 'text-emerald-500',
    sub: 'tiết kiệm so với tháng trước',
  },
  {
    label: 'Lượt tương tác',
    value: '24,680',
    change: '+18.7%',
    isPositive: true,
    icon: TrendingUp,
    bg: 'bg-amber-500/10 dark:bg-amber-500/15',
    color: 'text-amber-500',
    sub: 'quiz + flashcard + ghi chú',
  },
]

// ── ANIMATION VARIANTS ────────────────────────────────────────────────────────
const containerVariants: Variants = {
  hidden: { opacity: 0 },
  show: { opacity: 1, transition: { staggerChildren: 0.07 } },
}
const itemVariants: Variants = {
  hidden: { opacity: 0, y: 20 },
  show: { opacity: 1, y: 0, transition: { type: 'spring', stiffness: 300, damping: 24 } },
}

// ── CUSTOM TOOLTIP ────────────────────────────────────────────────────────────
function ChartTooltip({ active, payload, label }: {
  active?: boolean
  payload?: Array<{ name: string; value: number; color: string }>
  label?: string
}) {
  if (!active || !payload?.length) return null
  return (
    <div className="rounded-xl border border-border/50 bg-card dark:bg-card/90 backdrop-blur-lg shadow-xl p-3 text-sm">
      <p className="font-semibold text-foreground mb-2">{label}</p>
      {payload.map((p) => (
        <div key={p.name} className="flex items-center gap-2">
          <div className="w-2 h-2 rounded-full" style={{ background: p.color }} />
          <span className="text-muted-foreground">{p.name}:</span>
          <span className="font-semibold text-foreground">{p.value.toLocaleString()}</span>
        </div>
      ))}
    </div>
  )
}

// ── MAIN COMPONENT ─────────────────────────────────────────────────────────────
export default function StatisticsPage() {
  return (
    <motion.div
      className="space-y-6 max-w-7xl mx-auto"
      variants={containerVariants}
      initial="hidden"
      animate="show"
    >
      {/* ── HEADER ── */}
      <motion.div variants={itemVariants}>
        <p className="text-sm font-medium text-muted-foreground mb-1">Bảng điều khiển</p>
        <h1 className="text-3xl md:text-4xl font-bold tracking-tight text-foreground">
          Thống kê &amp; <span className="text-violet-500">Phân tích</span>
        </h1>
        <p className="text-muted-foreground mt-2">
          Tổng quan hiệu suất hệ thống EduMind — cập nhật thời gian thực.
        </p>
      </motion.div>

      {/* ── KPI CARDS ── */}
      <motion.div variants={containerVariants} className="grid grid-cols-2 lg:grid-cols-4 gap-4">
        {kpiCards.map((card) => (
          <motion.div
            key={card.label}
            variants={itemVariants}
            whileHover={{ y: -4, transition: { type: 'spring', stiffness: 400, damping: 20 } }}
            className="relative overflow-hidden rounded-2xl border border-border/50 bg-card dark:bg-card/70
              dark:backdrop-blur-sm p-5 hover:border-violet-500/25 hover:shadow-lg transition-shadow
              duration-300 group cursor-default"
          >
            <div className="absolute -right-6 -top-6 w-20 h-20 rounded-full bg-gradient-to-br
              from-violet-500/8 to-transparent blur-2xl group-hover:from-violet-500/15 transition-colors duration-500" />
            <div className="relative z-10">
              <div className="flex items-start justify-between mb-4">
                <div className={`w-11 h-11 rounded-xl flex items-center justify-center ${card.bg} ${card.color}
                  group-hover:scale-110 transition-transform duration-300`}>
                  <card.icon size={22} strokeWidth={2} aria-hidden="true" />
                </div>
                <span className={`inline-flex items-center gap-1 text-xs font-semibold px-2 py-1 rounded-full ${
                  card.isPositive
                    ? 'bg-emerald-500/10 text-emerald-500'
                    : 'bg-destructive/10 text-destructive'
                }`}>
                  {card.isPositive ? <ArrowUpRight size={12} /> : <ArrowDownRight size={12} />}
                  {card.change}
                </span>
              </div>
              <p className="text-xs font-medium text-muted-foreground mb-1">{card.label}</p>
              <p className="text-2xl font-bold text-foreground">{card.value}</p>
              <p className="text-xs text-muted-foreground mt-1">{card.sub}</p>
            </div>
          </motion.div>
        ))}
      </motion.div>

      {/* ── ROW 1: USER GROWTH + ROLE DISTRIBUTION ── */}
      <motion.div variants={containerVariants} className="grid grid-cols-1 lg:grid-cols-3 gap-4">
        {/* User Growth Chart */}
        <motion.div
          variants={itemVariants}
          className="lg:col-span-2 rounded-2xl border border-border/50 bg-card dark:bg-card/70
            dark:backdrop-blur-sm p-6 shadow-sm"
        >
          <div className="mb-5">
            <h2 className="text-base font-bold text-foreground">Tăng trưởng người dùng</h2>
            <p className="text-xs text-muted-foreground mt-0.5">Biến động học sinh &amp; giáo viên theo tháng</p>
          </div>
          <ResponsiveContainer width="100%" height={240}>
            <AreaChart data={userGrowthData} margin={{ top: 5, right: 5, left: -20, bottom: 0 }}>
              <defs>
                <linearGradient id="gradStudents" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="5%" stopColor="#8b5cf6" stopOpacity={0.25} />
                  <stop offset="95%" stopColor="#8b5cf6" stopOpacity={0} />
                </linearGradient>
                <linearGradient id="gradTeachers" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="5%" stopColor="#10b981" stopOpacity={0.25} />
                  <stop offset="95%" stopColor="#10b981" stopOpacity={0} />
                </linearGradient>
              </defs>
              <CartesianGrid strokeDasharray="3 3" stroke="var(--border)" opacity={0.5} />
              <XAxis dataKey="month" tick={{ fontSize: 11, fill: 'var(--muted-foreground)' }} tickLine={false} axisLine={false} />
              <YAxis tick={{ fontSize: 11, fill: 'var(--muted-foreground)' }} tickLine={false} axisLine={false} />
              <Tooltip content={<ChartTooltip />} />
              <Legend
                wrapperStyle={{ fontSize: 12, paddingTop: 12 }}
                formatter={(v) => <span style={{ color: 'var(--muted-foreground)' }}>{v}</span>}
              />
              <Area
                type="monotone"
                dataKey="students"
                name="Học sinh"
                stroke="#8b5cf6"
                strokeWidth={2}
                fill="url(#gradStudents)"
                dot={false}
                activeDot={{ r: 5, fill: '#8b5cf6' }}
              />
              <Area
                type="monotone"
                dataKey="teachers"
                name="Giáo viên"
                stroke="#10b981"
                strokeWidth={2}
                fill="url(#gradTeachers)"
                dot={false}
                activeDot={{ r: 5, fill: '#10b981' }}
              />
            </AreaChart>
          </ResponsiveContainer>
        </motion.div>

        {/* Role Distribution Pie */}
        <motion.div
          variants={itemVariants}
          className="rounded-2xl border border-border/50 bg-card dark:bg-card/70
            dark:backdrop-blur-sm p-6 shadow-sm flex flex-col"
        >
          <div className="mb-5">
            <h2 className="text-base font-bold text-foreground">Phân bố vai trò</h2>
            <p className="text-xs text-muted-foreground mt-0.5">Tỉ lệ user theo vai trò</p>
          </div>
          <div className="flex-1 flex flex-col items-center justify-center">
            <ResponsiveContainer width="100%" height={180}>
              <PieChart>
                <Pie
                  data={roleDistribution}
                  cx="50%"
                  cy="50%"
                  innerRadius={50}
                  outerRadius={80}
                  paddingAngle={4}
                  dataKey="value"
                >
                  {roleDistribution.map((entry, index) => (
                    <Cell key={index} fill={entry.color} stroke="transparent" />
                  ))}
                </Pie>
                <Tooltip
                  formatter={(value: any) => [Number(value).toLocaleString(), 'Người dùng']}
                  contentStyle={{
                    borderRadius: '12px',
                    border: '1px solid var(--border)',
                    background: 'var(--card)',
                    color: 'var(--foreground)',
                    fontSize: 12,
                  }}
                />
              </PieChart>
            </ResponsiveContainer>
            <div className="w-full space-y-2 mt-2">
              {roleDistribution.map((item) => (
                <div key={item.name} className="flex items-center justify-between text-sm">
                  <div className="flex items-center gap-2">
                    <div className="w-2.5 h-2.5 rounded-full" style={{ background: item.color }} />
                    <span className="text-muted-foreground">{item.name}</span>
                  </div>
                  <span className="font-semibold text-foreground">{item.value.toLocaleString()}</span>
                </div>
              ))}
            </div>
          </div>
        </motion.div>
      </motion.div>

      {/* ── ROW 2: LECTURE CHART + INTERACTION CHART ── */}
      <motion.div variants={containerVariants} className="grid grid-cols-1 lg:grid-cols-2 gap-4">
        {/* Lecture Growth */}
        <motion.div
          variants={itemVariants}
          className="rounded-2xl border border-border/50 bg-card dark:bg-card/70
            dark:backdrop-blur-sm p-6 shadow-sm"
        >
          <div className="mb-5">
            <h2 className="text-base font-bold text-foreground">Bài giảng được tạo</h2>
            <p className="text-xs text-muted-foreground mt-0.5">Tổng vs AI-generated theo tháng</p>
          </div>
          <ResponsiveContainer width="100%" height={220}>
            <BarChart data={lectureData} margin={{ top: 5, right: 5, left: -20, bottom: 0 }} barGap={4}>
              <CartesianGrid strokeDasharray="3 3" stroke="var(--border)" opacity={0.5} vertical={false} />
              <XAxis dataKey="month" tick={{ fontSize: 11, fill: 'var(--muted-foreground)' }} tickLine={false} axisLine={false} />
              <YAxis tick={{ fontSize: 11, fill: 'var(--muted-foreground)' }} tickLine={false} axisLine={false} />
              <Tooltip content={<ChartTooltip />} />
              <Legend
                wrapperStyle={{ fontSize: 12, paddingTop: 12 }}
                formatter={(v) => <span style={{ color: 'var(--muted-foreground)' }}>{v}</span>}
              />
              <Bar dataKey="lectures" name="Tổng bài giảng" fill="#8b5cf6" radius={[6, 6, 0, 0]} opacity={0.5} />
              <Bar dataKey="aiGenerated" name="AI-Generated" fill="#8b5cf6" radius={[6, 6, 0, 0]} />
            </BarChart>
          </ResponsiveContainer>
        </motion.div>

        {/* Weekly Interactions */}
        <motion.div
          variants={itemVariants}
          className="rounded-2xl border border-border/50 bg-card dark:bg-card/70
            dark:backdrop-blur-sm p-6 shadow-sm"
        >
          <div className="mb-5">
            <h2 className="text-base font-bold text-foreground">Tương tác trong tuần</h2>
            <p className="text-xs text-muted-foreground mt-0.5">Quiz, Flashcard &amp; Ghi chú theo ngày</p>
          </div>
          <ResponsiveContainer width="100%" height={220}>
            <BarChart data={interactionData} margin={{ top: 5, right: 5, left: -20, bottom: 0 }} barGap={2}>
              <CartesianGrid strokeDasharray="3 3" stroke="var(--border)" opacity={0.5} vertical={false} />
              <XAxis dataKey="day" tick={{ fontSize: 11, fill: 'var(--muted-foreground)' }} tickLine={false} axisLine={false} />
              <YAxis tick={{ fontSize: 11, fill: 'var(--muted-foreground)' }} tickLine={false} axisLine={false} />
              <Tooltip content={<ChartTooltip />} />
              <Legend
                wrapperStyle={{ fontSize: 12, paddingTop: 12 }}
                formatter={(v) => <span style={{ color: 'var(--muted-foreground)' }}>{v}</span>}
              />
              <Bar dataKey="quizzes" name="Quiz" fill="#8b5cf6" radius={[4, 4, 0, 0]} />
              <Bar dataKey="flashcards" name="Flashcard" fill="#10b981" radius={[4, 4, 0, 0]} />
              <Bar dataKey="notes" name="Ghi chú" fill="#f59e0b" radius={[4, 4, 0, 0]} />
            </BarChart>
          </ResponsiveContainer>
        </motion.div>
      </motion.div>

      {/* ── ROW 3: TOP LECTURES TABLE ── */}
      <motion.div
        variants={itemVariants}
        className="rounded-2xl border border-border/50 bg-card dark:bg-card/70
          dark:backdrop-blur-sm shadow-sm overflow-hidden"
      >
        <div className="p-6 border-b border-border/50">
          <h2 className="text-base font-bold text-foreground">Top bài giảng nổi bật</h2>
          <p className="text-xs text-muted-foreground mt-0.5">5 bài giảng được xem nhiều nhất tháng này</p>
        </div>
        <div className="overflow-x-auto">
          <table className="w-full text-sm text-left">
            <thead className="text-xs text-muted-foreground uppercase bg-muted/20 dark:bg-muted/10">
              <tr>
                <th className="px-6 py-3.5 font-semibold">#</th>
                <th className="px-6 py-3.5 font-semibold">Bài giảng</th>
                <th className="px-6 py-3.5 font-semibold">Giáo viên</th>
                <th className="px-6 py-3.5 font-semibold">
                  <div className="flex items-center gap-1"><Clock size={13} /> Lượt xem</div>
                </th>
                <th className="px-6 py-3.5 font-semibold">
                  <div className="flex items-center gap-1"><Star size={13} /> Đánh giá</div>
                </th>
              </tr>
            </thead>
            <tbody className="divide-y divide-border/40">
              {topLectures.map((lecture, idx) => (
                <tr key={idx} className="hover:bg-muted/10 dark:hover:bg-muted/8 transition-colors group">
                  <td className="px-6 py-4">
                    <span className={`w-6 h-6 rounded-full flex items-center justify-center text-xs font-bold
                      ${idx === 0 ? 'bg-amber-500/20 text-amber-500'
                        : idx === 1 ? 'bg-muted text-muted-foreground'
                        : 'bg-muted/50 text-muted-foreground'}`}>
                      {idx + 1}
                    </span>
                  </td>
                  <td className="px-6 py-4">
                    <span className="font-semibold text-foreground group-hover:text-violet-500 transition-colors">
                      {lecture.title}
                    </span>
                  </td>
                  <td className="px-6 py-4 text-muted-foreground">{lecture.teacher}</td>
                  <td className="px-6 py-4 font-semibold text-foreground">{lecture.views.toLocaleString()}</td>
                  <td className="px-6 py-4">
                    <div className="flex items-center gap-1 text-amber-500 font-semibold">
                      <Star size={13} fill="currentColor" />
                      {lecture.rating}
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </motion.div>
    </motion.div>
  )
}
