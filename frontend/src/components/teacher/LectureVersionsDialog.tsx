import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { History, Loader2 } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { FormError } from './FormKit'
import { Pill } from '@/components/student/StudentUi'
import { getLectureVersions, parseSlideContent, restoreLectureVersion } from '@/api/lectureApi'
import type { LectureResponse } from '@/api/lectureApi'
import { formatDateTime, getErrorMessage } from '@/lib/format'
import { confirmDialog, notify } from '@/lib/notify'

const STATUS: Record<string, { label: string; tone: 'amber' | 'emerald' | 'muted' }> = {
  DRAFT: { label: 'Nháp', tone: 'amber' },
  PUBLISHED: { label: 'Đang xuất bản', tone: 'emerald' },
  SUPERSEDED: { label: 'Bản cũ', tone: 'muted' },
}

interface LectureVersionsDialogProps {
  lecture: LectureResponse | null
  onClose: () => void
}

/** Lịch sử phiên bản của bài giảng; khôi phục một phiên bản cũ thành bản nháp mới. */
export function LectureVersionsDialog({ lecture, onClose }: LectureVersionsDialogProps) {
  const versions = useQuery({
    queryKey: ['lectures', 'versions', lecture?.lectureId, lecture?.currentVersionId],
    queryFn: () => getLectureVersions(lecture!.lectureId),
    enabled: lecture != null,
  })
  const sorted = [...(versions.data ?? [])].sort((a, b) => b.versionNumber - a.versionNumber)
  const queryClient = useQueryClient()
  const restore = useMutation({
    mutationFn: (versionId: string) => restoreLectureVersion(lecture!.lectureId, versionId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['lectures'] })
      notify.success('Đã khôi phục thành bản nháp mới', 'Bản đang xuất bản giữ nguyên cho tới khi bạn xuất bản lại.')
      onClose()
    },
    onError: e => notify.apiError(e, 'Không khôi phục được phiên bản.'),
  })
  const askRestore = async (versionNumber: number, versionId: string) => {
    const ok = await confirmDialog({
      title: `Khôi phục phiên bản ${versionNumber}?`,
      description: 'Nội dung phiên bản này được chép thành một bản nháp mới. Học sinh vẫn thấy bản đang xuất bản cho tới khi bạn xuất bản lại.',
      confirmText: 'Khôi phục',
    })
    if (ok) restore.mutate(versionId)
  }

  return (
    <Dialog open={lecture != null} onOpenChange={open => { if (!open) onClose() }}>
      <DialogContent className="max-h-[85vh] overflow-y-auto sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>Lịch sử phiên bản</DialogTitle>
          <DialogDescription>{lecture?.title}</DialogDescription>
        </DialogHeader>
        {versions.isLoading ? (
          <div className="flex justify-center py-6"><Loader2 className="h-5 w-5 animate-spin text-muted-foreground" /></div>
        ) : versions.isError ? (
          <FormError message={getErrorMessage(versions.error)} />
        ) : (
          <ul className="divide-y divide-border/50 rounded-xl border border-border/50">
            {sorted.map(v => {
              const s = STATUS[v.status] ?? { label: v.status, tone: 'muted' as const }
              return (
                <li key={v.versionId} className="flex items-center gap-3 p-3">
                  <div className="min-w-0 flex-1">
                    <p className="truncate text-sm font-medium text-foreground">
                      Phiên bản {v.versionNumber}: {v.title}
                    </p>
                    <p className="text-xs text-muted-foreground">
                      {parseSlideContent(v.slideContent).length} slide{v.aiGenerated ? ' · AI tạo' : ''}
                      {v.createdAt ? ` · ${formatDateTime(v.createdAt)}` : ''}
                    </p>
                  </div>
                  {v.versionId === lecture?.currentVersionId && v.status !== 'PUBLISHED' && <Pill tone="blue">Hiện tại</Pill>}
                  <Pill tone={s.tone}>{s.label}</Pill>
                  {v.versionId !== lecture?.currentVersionId && lecture?.canEdit && (
                    <Button
                      variant="ghost"
                      className="h-8 shrink-0 rounded-lg px-2 text-xs"
                      disabled={restore.isPending}
                      onClick={() => askRestore(v.versionNumber, v.versionId)}
                      title="Khôi phục thành bản nháp"
                    >
                      <History size={14} aria-hidden="true" /> Khôi phục
                    </Button>
                  )}
                </li>
              )
            })}
          </ul>
        )}
      </DialogContent>
    </Dialog>
  )
}
