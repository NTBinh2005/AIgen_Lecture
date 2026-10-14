import { useState } from 'react'
import { useMutation } from '@tanstack/react-query'
import { Download, Eye, Loader2 } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger } from '@/components/ui/dropdown-menu'
import { FormError } from '../FormKit'
import { exportDownloadPath, getExportJob, previewAssignment, requestExport } from '@/api/teacherQuizApi'
import type { ExportType } from '@/api/teacherQuizApi'
import { downloadFromApi } from '@/lib/download'
import { getErrorMessage } from '@/lib/format'
import { QUESTION_TYPE_LABEL } from '@/lib/quiz'
import type { TeacherAssignment } from '@/types/teacher'

const LETTERS = 'ABCDEFGHIJ'
const sleep = (ms: number) => new Promise(resolve => setTimeout(resolve, ms))

const EXPORTS: Array<{ type: ExportType; label: string; file: string }> = [
  { type: 'GRADEBOOK_XLSX', label: 'Bảng điểm cả lớp (Excel)', file: 'bang-diem.xlsx' },
  { type: 'STUDENT_RESULT_XLSX', label: 'Kết quả từng học sinh (Excel)', file: 'ket-qua-hoc-sinh.xlsx' },
  { type: 'STUDENT_RESULT_PDF', label: 'Kết quả từng học sinh (PDF)', file: 'ket-qua-hoc-sinh.pdf' },
]

interface AssignmentToolsProps {
  assignment: TeacherAssignment
  title: string
}

/** Hai thao tác phụ của một bài kiểm tra đã giao: làm thử và xuất kết quả. */
export function AssignmentTools({ assignment, title }: AssignmentToolsProps) {
  const [previewOpen, setPreviewOpen] = useState(false)
  const preview = useMutation({ mutationFn: () => previewAssignment(assignment.assignmentId) })

  // POST /exports tạo job nền → poll tới COMPLETED rồi tải file
  const exportJob = useMutation({
    mutationFn: async ({ type, file }: { type: ExportType; file: string }) => {
      let job = await requestExport(type, assignment.assignmentId, assignment.classId)
      for (let i = 0; i < 40 && (job.status === 'QUEUED' || job.status === 'PROCESSING'); i++) {
        await sleep(1500)
        job = await getExportJob(job.jobId)
      }
      if (job.status !== 'COMPLETED') throw new Error(job.errorReport || 'Không tạo được file xuất.')
      // file = 'ten.ext' → 'ten-<id>.ext', đúng đuôi theo loại file backend trả về
      await downloadFromApi(exportDownloadPath(job.jobId), file.replace(/(.w+)$/, `-${assignment.assignmentId}$1`))
    },
  })

  const openPreview = () => {
    setPreviewOpen(true)
    preview.mutate()
  }

  const questions = [...(preview.data?.questions ?? [])].sort((a, b) => a.orderIndex - b.orderIndex)

  return (
    <>
      <div className="mt-2 flex gap-2">
        <Button variant="ghost" className="h-9 flex-1 rounded-lg" onClick={openPreview}>
          <Eye size={14} aria-hidden="true" /> Xem đề
        </Button>
        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <Button variant="ghost" className="h-9 flex-1 rounded-lg" disabled={exportJob.isPending}>
              {exportJob.isPending ? <Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" /> : <Download size={14} aria-hidden="true" />} Xuất kết quả
            </Button>
          </DropdownMenuTrigger>
          <DropdownMenuContent align="end" className="w-64">
            {EXPORTS.map(e => (
              <DropdownMenuItem key={e.type} className="cursor-pointer" onClick={() => exportJob.mutate({ type: e.type, file: e.file })}>
                {e.label}
              </DropdownMenuItem>
            ))}
          </DropdownMenuContent>
        </DropdownMenu>
      </div>
      {exportJob.isError && (
        <div className="mt-2">
          <FormError message={exportJob.error instanceof Error && !('response' in exportJob.error) ? exportJob.error.message : getErrorMessage(exportJob.error)} />
        </div>
      )}

      <Dialog open={previewOpen} onOpenChange={setPreviewOpen}>
        <DialogContent className="max-h-[90vh] overflow-y-auto sm:max-w-xl">
          <DialogHeader>
            <DialogTitle>{title}</DialogTitle>
            <DialogDescription>Đề bài như học sinh sẽ thấy. Bản xem thử không được lưu.</DialogDescription>
          </DialogHeader>
          {preview.isPending ? (
            <div className="flex justify-center py-8"><Loader2 className="h-6 w-6 animate-spin text-muted-foreground" /></div>
          ) : preview.isError ? (
            <FormError message={getErrorMessage(preview.error, 'Không tải được đề.')} />
          ) : (
            <ol className="space-y-3">
              {questions.map((q, i) => (
                <li key={q.questionId} className="rounded-xl border border-border/50 p-3">
                  <p className="text-xs text-muted-foreground">Câu {i + 1} · {QUESTION_TYPE_LABEL[q.questionType] ?? q.questionType} · {q.points} điểm</p>
                  <p className="mt-1 text-sm font-medium text-foreground">{q.questionText}</p>
                  {(q.options ?? []).length > 0 && (
                    <ul className="mt-2 space-y-1 text-sm text-muted-foreground">
                      {q.options!.map((o, j) => <li key={j}><span className="font-semibold text-foreground">{LETTERS[j]}.</span> {o}</li>)}
                    </ul>
                  )}
                </li>
              ))}
              {questions.length === 0 && <p className="py-6 text-center text-sm text-muted-foreground">Đề chưa có câu hỏi.</p>}
            </ol>
          )}
        </DialogContent>
      </Dialog>
    </>
  )
}
