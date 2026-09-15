import { useState } from 'react'
import { motion, type Variants } from 'framer-motion'
import { Activity, Search, Filter, AlertCircle, Info, AlertTriangle, Download, RefreshCw } from 'lucide-react'
import { Button } from '@/components/ui/button'

const containerVariants: Variants = {
  hidden: { opacity: 0 },
  show: { opacity: 1, transition: { staggerChildren: 0.05 } },
}
const itemVariants: Variants = {
  hidden: { opacity: 0, y: 10 },
  show: { opacity: 1, y: 0, transition: { type: 'spring', stiffness: 300, damping: 24 } },
}

// ── Mock Data ─────────────────────────────────────────────────────────────
type LogSeverity = 'INFO' | 'WARN' | 'ERROR'

interface SystemLog {
  id: string
  timestamp: string
  service: string
  severity: LogSeverity
  message: string
}

const MOCK_LOGS: SystemLog[] = [
  { id: '1', timestamp: '2026-07-23 13:45:12', service: 'video-service', severity: 'INFO', message: 'Render job 5b3bee22-97e7 completed successfully.' },
  { id: '2', timestamp: '2026-07-23 13:42:05', service: 'backend', severity: 'ERROR', message: 'Failed to authenticate JWT token for user ID 102.' },
  { id: '3', timestamp: '2026-07-23 13:40:59', service: 'video-service', severity: 'WARN', message: 'Google TTS request taking longer than expected.' },
  { id: '4', timestamp: '2026-07-23 13:38:22', service: 'backend', severity: 'INFO', message: 'Lecture ID 45 generated successfully by Gemini.' },
  { id: '5', timestamp: '2026-07-23 13:35:10', service: 'database', severity: 'INFO', message: 'Database backup completed.' },
  { id: '6', timestamp: '2026-07-23 13:30:00', service: 'video-service', severity: 'ERROR', message: 'Timeout of 10000ms exceeded during TTS generation.' },
  { id: '7', timestamp: '2026-07-23 13:25:15', service: 'backend', severity: 'INFO', message: 'New user registered: hocsinh1@edumind.com' },
]

export default function SystemLogsPage() {
  const [search, setSearch] = useState('')
  const [severityFilter, setSeverityFilter] = useState<'ALL' | LogSeverity>('ALL')
  const [isRefreshing, setIsRefreshing] = useState(false)

  const handleRefresh = () => {
    setIsRefreshing(true)
    setTimeout(() => setIsRefreshing(false), 800)
  }

  const filteredLogs = MOCK_LOGS.filter((log) => {
    const matchSearch = log.message.toLowerCase().includes(search.toLowerCase()) || log.service.toLowerCase().includes(search.toLowerCase())
    const matchSeverity = severityFilter === 'ALL' || log.severity === severityFilter
    return matchSearch && matchSeverity
  })

  return (
    <motion.div
      className="space-y-6 max-w-7xl mx-auto pb-10"
      variants={containerVariants}
      initial="hidden"
      animate="show"
    >
      {/* ── HEADER ── */}
      <motion.div variants={itemVariants} className="flex flex-col md:flex-row justify-between items-start md:items-end gap-4">
        <div>
          <p className="text-sm font-medium text-muted-foreground mb-1">Quản trị hệ thống</p>
          <h1 className="text-3xl md:text-4xl font-bold tracking-tight text-foreground flex items-center gap-3">
            Nhật ký <span className="text-blue-500">Hệ thống</span>
            <Activity className="text-blue-500" size={32} />
          </h1>
          <p className="text-muted-foreground mt-2">Theo dõi và gỡ lỗi các hoạt động từ các dịch vụ trong hệ thống.</p>
        </div>
        <div className="flex gap-3">
          <Button variant="outline" className="rounded-xl h-11 px-4" onClick={handleRefresh} disabled={isRefreshing}>
            <RefreshCw className={`mr-2 h-4 w-4 ${isRefreshing ? 'animate-spin' : ''}`} />
            Làm mới
          </Button>
          <Button className="bg-blue-600 hover:bg-blue-700 text-white rounded-xl h-11 px-4">
            <Download className="mr-2 h-4 w-4" />
            Xuất file CSV
          </Button>
        </div>
      </motion.div>

      {/* ── KPI CARDS ── */}
      <motion.div variants={containerVariants} className="grid grid-cols-1 md:grid-cols-3 gap-4">
        <motion.div variants={itemVariants} className="rounded-2xl border border-border/50 bg-card p-5 flex items-center gap-4">
          <div className="w-12 h-12 rounded-full bg-emerald-500/10 flex items-center justify-center text-emerald-500 shrink-0">
            <Info size={24} />
          </div>
          <div>
            <p className="text-sm text-muted-foreground">Log Thông tin (INFO)</p>
            <p className="text-2xl font-bold text-foreground">1,240</p>
          </div>
        </motion.div>
        <motion.div variants={itemVariants} className="rounded-2xl border border-border/50 bg-card p-5 flex items-center gap-4">
          <div className="w-12 h-12 rounded-full bg-amber-500/10 flex items-center justify-center text-amber-500 shrink-0">
            <AlertTriangle size={24} />
          </div>
          <div>
            <p className="text-sm text-muted-foreground">Log Cảnh báo (WARN)</p>
            <p className="text-2xl font-bold text-foreground">35</p>
          </div>
        </motion.div>
        <motion.div variants={itemVariants} className="rounded-2xl border border-border/50 bg-card p-5 flex items-center gap-4">
          <div className="w-12 h-12 rounded-full bg-red-500/10 flex items-center justify-center text-red-500 shrink-0">
            <AlertCircle size={24} />
          </div>
          <div>
            <p className="text-sm text-muted-foreground">Log Lỗi (ERROR)</p>
            <p className="text-2xl font-bold text-foreground text-red-500">12</p>
          </div>
        </motion.div>
      </motion.div>

      {/* ── TABLE ── */}
      <motion.div variants={itemVariants} className="rounded-2xl border border-border/50 bg-card/60 backdrop-blur-xl shadow-sm overflow-hidden">
        {/* Toolbar */}
        <div className="p-5 border-b border-border/50 flex flex-col sm:flex-row gap-3">
          <div className="relative flex-1 max-w-md">
            <Search size={15} className="absolute left-3 top-1/2 -translate-y-1/2 text-muted-foreground" />
            <input
              type="text"
              placeholder="Tìm kiếm nội dung log..."
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              className="w-full h-9 pl-9 pr-4 bg-muted/30 border border-border/50 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-blue-500/40 transition-all"
            />
          </div>
          <div className="flex gap-2 items-center shrink-0">
            <Filter size={14} className="text-muted-foreground shrink-0" />
            <select
              value={severityFilter}
              onChange={(e) => setSeverityFilter(e.target.value as 'ALL' | LogSeverity)}
              className="h-9 px-3 rounded-xl border border-border/50 bg-muted/30 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500/40"
            >
              <option value="ALL">Tất cả mức độ</option>
              <option value="INFO">INFO</option>
              <option value="WARN">WARN</option>
              <option value="ERROR">ERROR</option>
            </select>
          </div>
        </div>

        {/* Logs */}
        <div className="overflow-x-auto">
          <table className="w-full text-sm text-left">
            <thead className="text-xs text-muted-foreground uppercase bg-muted/20">
              <tr>
                <th className="px-5 py-3.5 font-semibold">Thời gian</th>
                <th className="px-5 py-3.5 font-semibold">Mức độ</th>
                <th className="px-5 py-3.5 font-semibold">Dịch vụ</th>
                <th className="px-5 py-3.5 font-semibold">Nội dung</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-border/40">
              {filteredLogs.length === 0 ? (
                <tr>
                  <td colSpan={4} className="py-16 text-center text-muted-foreground text-sm">
                    Không tìm thấy log nào.
                  </td>
                </tr>
              ) : (
                filteredLogs.map((log) => (
                  <tr key={log.id} className="hover:bg-muted/10 transition-colors">
                    <td className="px-5 py-3 text-muted-foreground whitespace-nowrap font-mono text-xs">
                      {log.timestamp}
                    </td>
                    <td className="px-5 py-3">
                      <span className={`inline-flex items-center gap-1.5 px-2.5 py-1 rounded-md text-[10px] font-bold uppercase tracking-wider
                        ${log.severity === 'INFO' ? 'bg-emerald-500/10 text-emerald-600' : 
                          log.severity === 'WARN' ? 'bg-amber-500/10 text-amber-600' : 
                          'bg-red-500/10 text-red-600'}`}
                      >
                        {log.severity}
                      </span>
                    </td>
                    <td className="px-5 py-3 font-medium text-foreground">
                      {log.service}
                    </td>
                    <td className="px-5 py-3 font-mono text-xs text-foreground/80 truncate max-w-lg">
                      {log.message}
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
