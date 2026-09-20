import { Users, BookOpen, BrainCircuit, Activity, ArrowUpRight, ArrowDownRight, Search, MoreVertical } from 'lucide-react'
import { motion, type Variants } from 'framer-motion'

const stats = [
  {
    label: 'Tổng người dùng',
    value: '1,248',
    change: '+12%',
    isPositive: true,
    icon: Users,
    bg: 'bg-violet-500/10 dark:bg-violet-500/15',
    color: 'text-violet-500',
  },
  {
    label: 'Bài giảng đã sinh',
    value: '842',
    change: '+24%',
    isPositive: true,
    icon: BookOpen,
    bg: 'bg-primary/10 dark:bg-primary/15',
    color: 'text-primary',
  },
  {
    label: 'Chi phí LLM API',
    value: '$142.50',
    change: '-5%',
    isPositive: true,
    icon: BrainCircuit,
    bg: 'bg-emerald-500/10 dark:bg-emerald-500/15',
    color: 'text-emerald-500',
  },
  {
    label: 'Server Uptime',
    value: '99.9%',
    change: 'Ổn định',
    isPositive: true,
    icon: Activity,
    bg: 'bg-amber-500/10 dark:bg-amber-500/15',
    color: 'text-amber-500',
  },
]

const recentUsers = [
  { id: 'U-1029', name: 'Nguyễn Văn A', email: 'vana@edumind.vn', role: 'TEACHER', status: 'ACTIVE', date: 'Vừa xong' },
  { id: 'U-1028', name: 'Trần Thị B', email: 'thib@edumind.vn', role: 'STUDENT', status: 'ACTIVE', date: '5 phút trước' },
  { id: 'U-1027', name: 'Lê Hoàng C', email: 'hoangc@edumind.vn', role: 'STUDENT', status: 'INACTIVE', date: '1 giờ trước' },
  { id: 'U-1026', name: 'Phạm D', email: 'phamd@edumind.vn', role: 'TEACHER', status: 'ACTIVE', date: '2 giờ trước' },
  { id: 'U-1025', name: 'Hoàng E', email: 'hoange@edumind.vn', role: 'STUDENT', status: 'ACTIVE', date: 'Hôm qua' },
]

const containerVariants: Variants = {
  hidden: { opacity: 0 },
  show: { opacity: 1, transition: { staggerChildren: 0.08 } }
}

const itemVariants: Variants = {
  hidden: { opacity: 0, y: 20 },
  show: { opacity: 1, y: 0, transition: { type: 'spring', stiffness: 300, damping: 24 } }
}

export default function AdminDashboard() {
  return (
    <motion.div
      className="space-y-6 max-w-7xl mx-auto"
      variants={containerVariants}
      initial="hidden"
      animate="show"
    >
      {/* ── HEADER ── */}
      <motion.div variants={itemVariants}>
        <p className="text-sm font-medium text-muted-foreground mb-1">Tổng quan hệ thống</p>
        <h1 className="text-3xl md:text-4xl font-bold tracking-tight text-foreground">
          Admin <span className="text-violet-500">Dashboard</span>
        </h1>
        <p className="text-muted-foreground mt-2">Theo dõi và quản lý mọi hoạt động trên nền tảng EduMind.</p>
      </motion.div>

      {/* ── STATS GRID ── */}
      <motion.div variants={containerVariants} className="grid grid-cols-2 lg:grid-cols-4 gap-4">
        {stats.map((stat) => (
          <motion.div
            key={stat.label}
            variants={itemVariants}
            whileHover={{ y: -4, transition: { type: 'spring', stiffness: 400, damping: 20 } }}
            className="relative overflow-hidden rounded-2xl border border-border/50 bg-card dark:bg-card/70 dark:backdrop-blur-sm p-5
              hover:border-violet-500/25 hover:shadow-lg transition-shadow duration-300 group cursor-default"
          >
            {/* Decorative glow corner */}
            <div className="absolute -right-6 -top-6 w-20 h-20 rounded-full bg-gradient-to-br from-violet-500/8 to-transparent blur-2xl
              group-hover:from-violet-500/15 transition-colors duration-500" />

            <div className="relative z-10">
              <div className="flex items-start justify-between mb-4">
                <div className={`w-11 h-11 rounded-xl flex items-center justify-center ${stat.bg} ${stat.color} group-hover:scale-110 transition-transform duration-300`}>
                  <stat.icon size={22} strokeWidth={2} aria-hidden="true" />
                </div>
                <span className={`inline-flex items-center gap-1 text-xs font-semibold px-2 py-1 rounded-full ${
                  stat.change === 'Ổn định'
                    ? 'bg-muted text-muted-foreground'
                    : stat.isPositive
                    ? 'bg-emerald-500/10 text-emerald-500'
                    : 'bg-destructive/10 text-destructive'
                }`}>
                  {stat.change !== 'Ổn định' && stat.isPositive && <ArrowUpRight size={12} />}
                  {stat.change !== 'Ổn định' && !stat.isPositive && <ArrowDownRight size={12} />}
                  {stat.change}
                </span>
              </div>
              <p className="text-xs font-medium text-muted-foreground mb-1">{stat.label}</p>
              <p className="text-2xl font-bold text-foreground">{stat.value}</p>
            </div>
          </motion.div>
        ))}
      </motion.div>

      {/* ── USERS TABLE ── */}
      <motion.div
        variants={itemVariants}
        className="rounded-2xl border border-border/50 bg-card dark:bg-card/70 dark:backdrop-blur-sm shadow-sm overflow-hidden"
      >
        <div className="p-6 border-b border-border/50 flex flex-col sm:flex-row sm:items-center justify-between gap-4">
          <div>
            <h2 className="text-lg font-bold text-foreground">Người dùng mới đăng ký</h2>
            <p className="text-sm text-muted-foreground mt-0.5">Danh sách đăng ký gần đây nhất</p>
          </div>
          <div className="relative group">
            <Search size={15} className="absolute left-3 top-1/2 -translate-y-1/2 text-muted-foreground group-focus-within:text-primary transition-colors" aria-hidden="true" />
            <input
              type="text"
              aria-label="Tìm kiếm người dùng"
              placeholder="Tìm kiếm người dùng..."
              className="h-9 pl-9 pr-4 w-full sm:w-64 bg-muted/30 border border-border/50 rounded-xl text-sm
                focus:outline-none focus:ring-2 focus:ring-primary/25 focus:border-primary/40 transition-all"
            />
          </div>
        </div>

        <div className="overflow-x-auto">
          <table className="w-full text-sm text-left">
            <thead className="text-xs text-muted-foreground uppercase bg-muted/20 dark:bg-muted/10">
              <tr>
                <th className="px-6 py-3.5 font-semibold">Mã</th>
                <th className="px-6 py-3.5 font-semibold">Họ tên & Email</th>
                <th className="px-6 py-3.5 font-semibold">Vai trò</th>
                <th className="px-6 py-3.5 font-semibold">Trạng thái</th>
                <th className="px-6 py-3.5 font-semibold hidden md:table-cell">Thời gian</th>
                <th className="px-6 py-3.5 font-semibold text-right">Thao tác</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-border/40">
              {recentUsers.map((user) => (
                <tr key={user.id} className="hover:bg-muted/10 dark:hover:bg-muted/8 transition-colors group">
                  <td className="px-6 py-4 font-mono text-xs font-medium text-muted-foreground">{user.id}</td>
                  <td className="px-6 py-4">
                    <div className="flex items-center gap-3">
                      <div className="w-8 h-8 rounded-full bg-gradient-to-br from-primary/60 to-violet-500/60 flex items-center justify-center text-white text-xs font-bold shrink-0">
                        {user.name.charAt(0)}
                      </div>
                      <div className="flex flex-col">
                        <span className="font-semibold text-foreground group-hover:text-violet-500 transition-colors">{user.name}</span>
                        <span className="text-xs text-muted-foreground">{user.email}</span>
                      </div>
                    </div>
                  </td>
                  <td className="px-6 py-4">
                    <span className={`inline-flex px-2.5 py-1 rounded-lg text-xs font-semibold border ${
                      user.role === 'TEACHER'
                        ? 'bg-primary/10 text-primary border-primary/20 dark:bg-primary/15'
                        : 'bg-emerald-500/10 text-emerald-600 border-emerald-500/20 dark:bg-emerald-500/15 dark:text-emerald-400'
                    }`}>
                      {user.role}
                    </span>
                  </td>
                  <td className="px-6 py-4">
                    <div className="flex items-center gap-2">
                      <div className={`w-2 h-2 rounded-full ${user.status === 'ACTIVE' ? 'bg-emerald-500 animate-glow-pulse' : 'bg-muted-foreground'}`} />
                      <span className={`text-xs font-medium ${user.status === 'ACTIVE' ? 'text-emerald-600 dark:text-emerald-400' : 'text-muted-foreground'}`}>
                        {user.status}
                      </span>
                    </div>
                  </td>
                  <td className="px-6 py-4 text-muted-foreground text-xs hidden md:table-cell">{user.date}</td>
                  <td className="px-6 py-4 text-right">
                    <button
                      aria-label={`Thao tác với ${user.name}`}
                      className="p-1.5 text-muted-foreground hover:text-foreground hover:bg-muted rounded-lg transition-colors"
                    >
                      <MoreVertical size={15} aria-hidden="true" />
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>

        <div className="p-4 border-t border-border/40 bg-muted/10 dark:bg-muted/5 flex justify-center">
          <button className="text-sm font-semibold text-primary hover:text-primary/80 transition-colors hover:underline underline-offset-4">
            Xem tất cả người dùng
          </button>
        </div>
      </motion.div>
    </motion.div>
  )
}