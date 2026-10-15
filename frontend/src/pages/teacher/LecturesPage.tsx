import { useState, useEffect } from 'react'
import { motion, type Variants } from 'framer-motion'
import { Plus, Search, MoreVertical, FileVideo, BookOpen, Video, Trash2, Edit, Play, Send, FilePen, Clapperboard, History, Users } from 'lucide-react'
import { Link, useNavigate } from 'react-router-dom'
import { formatDate } from '@/lib/format'
import { useQuery, useMutation, useQueryClient, keepPreviousData } from '@tanstack/react-query'
import { Pagination } from '@/components/common/Pagination'

import {
  getLectures, deleteLecture, updateLecture, publishLecture, requestVideoRender,
  getLectureCollaborators, addLectureCollaborator, removeLectureCollaborator,
} from '@/api/lectureApi'
import { LectureVersionsDialog } from '@/components/teacher/LectureVersionsDialog'
import { CollaboratorsDialog } from '@/components/teacher/CollaboratorsDialog'
import type { LectureResponse } from '@/api/lectureApi'

/** Bài đã xuất bản nhưng đã được sửa → backend tạo version nháp mới, status vẫn là PUBLISHED. */
function hasUnpublishedDraft(lecture: LectureResponse): boolean {
  // Backend trả cờ hasUnpublishedChanges, nhưng với bài chưa từng publish cờ này cũng true → chỉ xét bài PUBLISHED
  return lecture.status === 'PUBLISHED'
    && (lecture.hasUnpublishedChanges ?? (!!lecture.currentVersionId && lecture.currentVersionId !== lecture.publishedVersionId))
}
import { LectureStatusBadge } from '@/components/common/LectureStatusBadge'
import { isLectureViewable } from '@/lib/lecture'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger } from '@/components/ui/dropdown-menu'
import { Skeleton } from '@/components/ui/skeleton'
import { confirmDialog, notify, promptDialog } from '@/lib/notify'

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
  const [versionsOf, setVersionsOf] = useState<LectureResponse | null>(null)
  const [collaboratorsOf, setCollaboratorsOf] = useState<LectureResponse | null>(null)
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
    queryFn: () => getLectures({ page, size: 15, sort: 'createdAt,desc', title: debouncedSearch || undefined }),
    placeholderData: keepPreviousData,
    // Đang có video render → tự làm mới để cập nhật trạng thái
    refetchInterval: query => (query.state.data?.content.some(l => l.videoStatus === 'PROCESSING' || l.videoStatus === 'PENDING') ? 5000 : false),
  })

  const deleteMutation = useMutation({
    mutationFn: deleteLecture,
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ['lectures'] }); notify.success('Đã xoá bài giảng') },
    onError: e => notify.apiError(e, 'Không xoá được bài giảng.'),
  })

  const updateMutation = useMutation({
    mutationFn: ({ id, title }: { id: number, title: string }) => updateLecture(id, { title }),
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ['lectures'] }); notify.success('Đã đổi tên bài giảng') },
    onError: e => notify.apiError(e, 'Không đổi được tên bài giảng.'),
  })

  const renderMutation = useMutation({
    mutationFn: requestVideoRender,
    onSuccess: status => {
      queryClient.invalidateQueries({ queryKey: ['lectures'] })
      if (status.videoStatus === 'NOT_AVAILABLE') notify.warning(status.errorMessage || 'Tính năng render video hiện chưa được bật.')
      else notify.info('Đã gửi yêu cầu tạo video', 'Việc render có thể mất vài phút.')
    },
    onError: (err: { response?: { data?: { message?: string } } }) =>
      notify.error(err?.response?.data?.message || 'Không gửi được yêu cầu tạo video.'),
  })

  const handleRender = async (lecture: LectureResponse) => {
    const again = lecture.videoStatus === 'DONE' || lecture.videoStatus === 'FAILED'
    const ok = await confirmDialog(again
      ? { title: `Tạo lại video cho "${lecture.title}"?`, description: 'Video hiện tại sẽ bị thay thế.', confirmText: 'Tạo lại' }
      : { title: `Tạo video cho "${lecture.title}"?`, description: 'Video được dựng từ bộ slide hiện tại. Việc render có thể mất vài phút.', confirmText: 'Tạo video' })
    if (ok) renderMutation.mutate(lecture.lectureId)
  }

  const publishMutation = useMutation({
    mutationFn: publishLecture,
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ['lectures'] }); notify.success('Đã xuất bản bài giảng') },
    onError: async (err: { response?: { data?: { message?: string } } }, id: number) => {
      const message = err?.response?.data?.message ?? ''
      // Backend bắt buộc có nội dung văn bản trước khi xuất bản → hướng giáo viên sang trang sửa
      if (/content is required/i.test(message)) {
        if (await confirmDialog({ title: 'Chưa xuất bản được', description: 'Bài giảng chưa có nội dung văn bản. Mở trang sửa nội dung để bổ sung?', confirmText: 'Mở trang sửa' })) {
          navigate(`/teacher/lectures/${id}/edit`)
        }
        return
      }
      notify.error(message || 'Không thể xuất bản bài giảng.')
    },
  })

  const handlePublish = async (lecture: LectureResponse) => {
    const description = lecture.accessScope === 'PRIVATE'
      ? 'Bài đang ở chế độ Riêng tư: học sinh sẽ không xem được kể cả khi giao cho lớp. Đổi sang "Lớp học" ở trang Sửa nội dung nếu muốn giao cho lớp.'
      : 'Học sinh của các lớp được giao sẽ thấy phiên bản này.'
    if (await confirmDialog({ title: `Xuất bản "${lecture.title}"?`, description, confirmText: 'Xuất bản' })) {
      publishMutation.mutate(lecture.lectureId)
    }
  }

  const handleDelete = async (id: number) => {
    if (await confirmDialog({ title: 'Xoá bài giảng này?', description: 'Bài giảng sẽ bị gỡ khỏi danh sách của bạn.', confirmText: 'Xoá', destructive: true })) {
      deleteMutation.mutate(id)
    }
  }

  const handleEdit = async (id: number, currentTitle: string) => {
    const newTitle = await promptDialog({ title: 'Đổi tên bài giảng', label: 'Tên mới', defaultValue: currentTitle, confirmText: 'Lưu', required: true })
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
                <th className="py-4 px-6 text-xs font-semibold text-muted-foreground uppercase tracking-wider">Trạng thái</th>
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
                          isLectureViewable(lecture.status) ? 'bg-primary/10 text-primary dark:bg-primary/15' : 'bg-muted text-muted-foreground'
                        }`}>
                          <Video size={18} />
                        </div>
                        <div>
                          {isLectureViewable(lecture.status) ? (
                            <Link to={`/teacher/lectures/${lecture.lectureId}`} className="font-semibold text-foreground hover:text-primary hover:underline transition-colors text-base line-clamp-1 block">
                              {lecture.title}
                            </Link>
                          ) : (
                            <div className="font-semibold text-foreground group-hover:text-primary transition-colors text-base line-clamp-1">{lecture.title}</div>
                          )}
                          <div className="text-xs text-muted-foreground md:hidden mt-1">{formatDate(lecture.createdAt)}</div>
                        </div>
                      </div>
                    </td>
                    <td className="py-5 px-6">
                      <div className="flex flex-col items-start gap-1.5">
                        <LectureStatusBadge status={lecture.status} />
                        {hasUnpublishedDraft(lecture) && (
                          <span className="text-[11px] font-medium text-amber-600 dark:text-amber-400">Có bản sửa chưa xuất bản</span>
                        )}
                        {lecture.status === 'PUBLISHED' && lecture.accessScope === 'PRIVATE' && !hasUnpublishedDraft(lecture) && (
                          <span className="text-[11px] text-muted-foreground">Riêng tư — học sinh không xem được</span>
                        )}
                        {lecture.videoStatus === 'PENDING' && (
                          <span className="text-[11px] font-medium text-blue-600 dark:text-blue-400">Đang chờ render video...</span>
                        )}
                        {lecture.videoStatus === 'PROCESSING' && (
                          <span className="text-[11px] font-medium text-blue-600 dark:text-blue-400">Đang render video...</span>
                        )}
                        {lecture.videoStatus === 'FAILED' && (
                          <span className="text-[11px] font-medium text-destructive" title={lecture.videoErrorMessage ?? undefined}>Render video lỗi</span>
                        )}
                      </div>
                    </td>
                    <td className="py-5 px-6 text-sm text-muted-foreground hidden md:table-cell">
                      {formatDate(lecture.createdAt)}
                    </td>
                    <td className="py-5 px-6 text-right">
                      <div className="flex items-center justify-end gap-2">
                        {isLectureViewable(lecture.status) && (
                          <Button
                            variant="secondary"
                            size="sm"
                            className="h-8 px-3 gap-1.5 text-primary hover:text-primary bg-primary/10 hover:bg-primary/20 font-semibold"
                            onClick={() => navigate(`/teacher/lectures/${lecture.lectureId}`)}
                          >
                            <Play size={14} className="fill-current" />
                            <span className="hidden sm:inline">{lecture.videoStatus === 'DONE' ? 'Xem video' : 'Xem'}</span>
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
                          <DropdownMenuContent align="end" className="w-44">
                            {lecture.canEdit && lecture.status !== 'ARCHIVED' && (
                              <DropdownMenuItem onClick={() => navigate(`/teacher/lectures/${lecture.lectureId}/edit`)} className="cursor-pointer gap-2">
                                <FilePen size={14} /> Sửa nội dung
                              </DropdownMenuItem>
                            )}
                            {lecture.canEdit && lecture.status !== 'ARCHIVED' && lecture.videoStatus !== 'PROCESSING' && (
                              <DropdownMenuItem onClick={() => handleRender(lecture)} className="cursor-pointer gap-2">
                                <Clapperboard size={14} /> {lecture.videoStatus === 'DONE' || lecture.videoStatus === 'FAILED' ? 'Tạo lại video' : 'Tạo video'}
                              </DropdownMenuItem>
                            )}
                            <DropdownMenuItem onClick={() => setVersionsOf(lecture)} className="cursor-pointer gap-2">
                              <History size={14} /> Lịch sử phiên bản
                            </DropdownMenuItem>
                            {lecture.canEdit && (
                              <DropdownMenuItem onClick={() => setCollaboratorsOf(lecture)} className="cursor-pointer gap-2">
                                <Users size={14} /> Cộng tác viên
                              </DropdownMenuItem>
                            )}
                            <DropdownMenuItem onClick={() => handleEdit(lecture.lectureId, lecture.title)} className="cursor-pointer gap-2">
                              <Edit size={14} /> Sửa tên
                            </DropdownMenuItem>
                            {lecture.canPublish && (lecture.status === 'DRAFT' || lecture.status === 'READY' || hasUnpublishedDraft(lecture)) && (
                              <DropdownMenuItem onClick={() => handlePublish(lecture)} className="cursor-pointer gap-2">
                                <Send size={14} /> {hasUnpublishedDraft(lecture) ? 'Xuất bản bản mới' : 'Xuất bản'}
                              </DropdownMenuItem>
                            )}
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
          <div className="border-t border-border/50 bg-muted/10 p-4">
            <Pagination page={page} totalPages={totalPages} total={lecturesPage?.totalElements} pageSize={15} onChange={setPage} label="Phân trang bài giảng" />
          </div>
        )}
      </motion.div>

      <LectureVersionsDialog lecture={versionsOf} onClose={() => setVersionsOf(null)} />
      <CollaboratorsDialog
        open={collaboratorsOf != null}
        onClose={() => setCollaboratorsOf(null)}
        resourceTitle={collaboratorsOf?.title ?? ''}
        queryKey={['lecture', collaboratorsOf?.lectureId]}
        load={() => getLectureCollaborators(collaboratorsOf!.lectureId)}
        add={id => addLectureCollaborator(collaboratorsOf!.lectureId, id)}
        remove={id => removeLectureCollaborator(collaboratorsOf!.lectureId, id)}
      />
    </motion.div>
  )
}
