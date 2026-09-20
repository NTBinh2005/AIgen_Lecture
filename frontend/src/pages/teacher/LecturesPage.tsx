import { useState, useEffect } from 'react'
import { motion, type Variants } from 'framer-motion'
import { Plus, Search, MoreVertical, FileVideo, BookOpen, ChevronLeft, ChevronRight, Video, Trash2, Edit, Play } from 'lucide-react'
import { Link, useNavigate } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient, keepPreviousData } from '@tanstack/react-query'

import { getLectures, deleteLecture, updateLecture } from '@/api/lectureApi'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger } from '@/components/ui/dropdown-menu'
import { Skeleton } from '@/components/ui/skeleton'

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

export default function LecturesPage() {
  const queryClient = useQueryClient()
  const navigate = useNavigate()
  const [page, setPage] = useState(0)
  const [searchTerm, setSearchTerm] = useState('')
  const [debouncedSearch, setDebouncedSearch] = useState('')

  // Debounce search
  useEffect(() => {
    const timer = setTimeout(() => {
      setDebouncedSearch(searchTerm)
      setPage(0) // Reset to first page when search changes
    }, 500)
    return () => clearTimeout(timer)
  }, [searchTerm])

  const { data: lecturesPage, isLoading } = useQuery({
    queryKey: ['lectures', 'page', page, debouncedSearch],
    queryFn: () => getLectures({ page, size: 10, sort: 'createdAt,desc', title: debouncedSearch || undefined }),
    placeholderData: keepPreviousData,
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
  const totalPages = lecturesPage?.totalPages || 0

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
          <h1 className="text-3xl md:text-4xl font-bold text-foreground tracking-tight flex items-center gap-3">
            <BookOpen className="text-primary w-8 h-8" />
            Bài giảng của tôi
          </h1>
          <p className="text-sm font-medium text-muted-foreground mt-2">
            Quản lý, theo dõi và chỉnh sửa các bài giảng bạn đã tạo
          </p>
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

      {/* ── TOOLBAR (SEARCH) ── */}
      <motion.div variants={itemVariants} className="flex flex-col sm:flex-row gap-4 justify-between items-center bg-card dark:bg-card/70 border border-border/50 p-4 rounded-2xl shadow-sm dark:backdrop-blur-sm">
        <div className="relative w-full sm:max-w-md">
          <div className="absolute inset-y-0 left-0 flex items-center pl-3 pointer-events-none text-muted-foreground">
            <Search size={18} />
          </div>
          <Input
            type="text"
            className="pl-10 h-11 bg-background/50 border-border/50 focus-visible:ring-primary/20"
            placeholder="Tìm kiếm bài giảng theo tên..."
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
          />
        </div>
        <div className="text-sm font-medium text-muted-foreground whitespace-nowrap">
          Tổng cộng: <span className="text-foreground font-bold">{lecturesPage?.totalElements || 0}</span> bài giảng
        </div>
      </motion.div>

      {/* ── LECTURES LIST ── */}
      <motion.div
        variants={itemVariants}
        className="rounded-2xl border border-border/50 bg-card dark:bg-card/70 dark:backdrop-blur-sm shadow-sm overflow-hidden"
      >
        <div className="overflow-x-auto">
          <table className="w-full text-left border-collapse">
            <thead>
              <tr className="bg-muted/25 dark:bg-muted/10">
                <th className="py-4 px-6 text-xs font-semibold text-muted-foreground uppercase tracking-wider">Tên bài giảng</th>
                <th className="py-4 px-6 text-xs font-semibold text-muted-foreground uppercase tracking-wider">Trạng thái Video</th>
                <th className="py-4 px-6 text-xs font-semibold text-muted-foreground uppercase tracking-wider hidden md:table-cell">Ngày tạo</th>
                <th className="py-4 px-6 text-right text-xs font-semibold text-muted-foreground uppercase tracking-wider">Thao tác</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-border/40">
              {isLoading ? (
                Array.from({ length: 5 }).map((_, i) => (
                  <tr key={i}>
                    <td colSpan={4} className="py-4 px-6">
                      <Skeleton className="h-10 w-full rounded-lg" />
                    </td>
                  </tr>
                ))
              ) : lectures.length === 0 ? (
                <tr>
                  <td colSpan={4} className="py-16 text-center text-muted-foreground">
                    <div className="flex flex-col items-center justify-center gap-3">
                      <div className="w-16 h-16 rounded-full bg-muted/50 flex items-center justify-center">
                        <FileVideo size={32} className="text-muted-foreground/60" />
                      </div>
                      <p className="text-base font-medium">Không tìm thấy bài giảng nào.</p>
                      {searchTerm ? (
                        <p className="text-sm">Hãy thử thay đổi từ khóa tìm kiếm.</p>
                      ) : (
                        <Link to="/teacher/lectures/create">
                          <Button variant="outline" className="mt-2 h-9">
                            Tạo bài giảng đầu tiên
                          </Button>
                        </Link>
                      )}
                    </div>
                  </td>
                </tr>
              ) : (
                lectures.map((lecture) => (
                  <tr key={lecture.lectureId} className="hover:bg-muted/15 dark:hover:bg-muted/10 transition-colors group">
                    <td className="py-5 px-6">
                      <div className="flex items-center gap-3">
                        <div className={`w-10 h-10 rounded-xl flex items-center justify-center shrink-0 transition-colors ${
                          lecture.videoStatus === 'DONE' ? 'bg-primary/10 text-primary dark:bg-primary/15' : 'bg-muted text-muted-foreground'
                        }`}>
                          <Video size={18} />
                        </div>
                        <div>
                          {lecture.videoStatus === 'DONE' ? (
                            <Link to={`/teacher/lectures/${lecture.lectureId}`} className="font-semibold text-foreground hover:text-primary hover:underline transition-colors text-base line-clamp-1 block">
                              {lecture.title}
                            </Link>
                          ) : (
                            <div className="font-semibold text-foreground group-hover:text-primary transition-colors text-base line-clamp-1">{lecture.title}</div>
                          )}
                          <div className="text-xs text-muted-foreground md:hidden mt-1">{new Date(lecture.createdAt).toLocaleDateString('vi-VN')}</div>
                        </div>
                      </div>
                    </td>
                    <td className="py-5 px-6">
                      <span className={`inline-flex items-center gap-1.5 px-3 py-1.5 rounded-full text-xs font-semibold border ${
                        lecture.videoStatus === 'DONE'
                          ? 'bg-emerald-500/10 text-emerald-600 border-emerald-500/20 dark:bg-emerald-500/15 dark:text-emerald-400'
                          : lecture.videoStatus === 'FAILED'
                          ? 'bg-destructive/10 text-destructive border-destructive/20 dark:bg-destructive/15'
                          : 'bg-amber-500/10 text-amber-700 border-amber-500/20 dark:bg-amber-500/15 dark:text-amber-400'
                      }`}>
                        <span className={`w-1.5 h-1.5 rounded-full ${
                          lecture.videoStatus === 'DONE' ? 'bg-emerald-500' : lecture.videoStatus === 'FAILED' ? 'bg-destructive' : 'bg-amber-500 animate-pulse'
                        }`} />
                        {lecture.videoStatus}
                      </span>
                    </td>
                    <td className="py-5 px-6 text-sm text-muted-foreground hidden md:table-cell">
                      {new Date(lecture.createdAt).toLocaleDateString('vi-VN')}
                    </td>
                    <td className="py-5 px-6 text-right">
                      <div className="flex items-center justify-end gap-2">
                        {lecture.videoStatus === 'DONE' && (
                          <Button
                            variant="secondary"
                            size="sm"
                            className="h-8 px-3 gap-1.5 text-primary hover:text-primary bg-primary/10 hover:bg-primary/20 font-semibold"
                            onClick={() => navigate(`/teacher/lectures/${lecture.lectureId}`)}
                          >
                            <Play size={14} className="fill-current" /> <span className="hidden sm:inline">Xem video</span>
                          </Button>
                        )}
                        <DropdownMenu>
                          <DropdownMenuTrigger asChild>
                            <button
                              className="p-2.5 text-muted-foreground hover:bg-muted hover:text-foreground rounded-lg transition-colors focus:outline-none"
                              aria-label="More options"
                            >
                              <MoreVertical size={18} aria-hidden="true" />
                            </button>
                          </DropdownMenuTrigger>
                          <DropdownMenuContent align="end" className="w-40">
                            <DropdownMenuItem onClick={() => handleEdit(lecture.lectureId, lecture.title)} className="cursor-pointer gap-2">
                              <Edit size={14} /> Sửa tên
                            </DropdownMenuItem>
                          <DropdownMenuItem className="text-destructive focus:text-destructive cursor-pointer gap-2" onClick={() => handleDelete(lecture.lectureId)}>
                            <Trash2 size={14} /> Xóa
                          </DropdownMenuItem>
                        </DropdownMenuContent>
                      </DropdownMenu>
                      </div>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
        
        {/* Pagination Controls */}
        {totalPages > 1 && (
          <div className="p-4 border-t border-border/50 flex items-center justify-between bg-muted/10">
            <div className="text-sm text-muted-foreground">
              Trang <span className="font-semibold text-foreground">{page + 1}</span> / {totalPages}
            </div>
            <div className="flex gap-2">
              <Button
                variant="outline"
                size="sm"
                onClick={() => setPage(p => Math.max(0, p - 1))}
                disabled={page === 0}
                className="h-8 px-2"
              >
                <ChevronLeft size={16} />
              </Button>
              <Button
                variant="outline"
                size="sm"
                onClick={() => setPage(p => Math.min(totalPages - 1, p + 1))}
                disabled={page >= totalPages - 1}
                className="h-8 px-2"
              >
                <ChevronRight size={16} />
              </Button>
            </div>
          </div>
        )}
      </motion.div>
    </motion.div>
  )
}
