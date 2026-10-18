import { Link, useParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { ArrowLeft, MonitorPlay } from 'lucide-react'
import { getStudentPresentation, renderStudentPresentation } from '@/api/presentationApi'
import { PresentationPlayer } from '@/components/teacher/PresentationPlayerDialog'
import { ErrorState } from '@/components/student/StudentUi'
import { formatDate, getErrorMessage, getErrorStatus } from '@/lib/format'

/** Học sinh xem bản trình chiếu được giao cho lớp (phiên bản đã xuất bản), giống trang xem bài giảng. */
export default function WatchPresentationPage() {
  const { presentationId = '' } = useParams<{ presentationId: string }>()
  const info = useQuery({
    queryKey: ['student', 'presentations', presentationId],
    queryFn: () => getStudentPresentation(presentationId),
    enabled: !!presentationId,
    retry: false,
  })

  if (info.isError) {
    const status = getErrorStatus(info.error)
    return (
      <div className="mx-auto max-w-3xl space-y-4">
        <BackLink />
        <ErrorState message={status === 403 || status === 404
          ? getErrorMessage(info.error, 'Bản trình chiếu chưa được giao cho lớp bạn đang học.')
          : getErrorMessage(info.error, 'Không tải được bản trình chiếu.')} />
      </div>
    )
  }

  const p = info.data
  return (
    <div className="mx-auto max-w-6xl space-y-4">
      <BackLink />
      <div className="flex items-start gap-3">
        <div className="flex h-11 w-11 shrink-0 items-center justify-center rounded-xl bg-violet-500/10 text-violet-600 dark:text-violet-400">
          <MonitorPlay size={20} aria-hidden="true" />
        </div>
        <div className="min-w-0">
          {p ? (
            <>
              <h1 className="text-2xl font-bold tracking-tight text-foreground sm:text-3xl">{p.title}</h1>
              <p className="mt-1 text-sm text-muted-foreground">{p.slideCount} slide · Xuất bản {formatDate(p.publishedAt)}</p>
            </>
          ) : (
            <div className="space-y-2">
              <div className="h-8 w-64 animate-pulse rounded-lg bg-muted" />
              <div className="h-4 w-40 animate-pulse rounded-lg bg-muted" />
            </div>
          )}
        </div>
      </div>
      {p && (
        <PresentationPlayer
          queryKey={['student', 'presentations', presentationId, 'render']}
          load={() => renderStudentPresentation(presentationId)}
          title={p.title}
        />
      )}
    </div>
  )
}

function BackLink() {
  return (
    <Link to="/student/classes" className="inline-flex min-h-10 items-center gap-1.5 text-sm font-medium text-muted-foreground hover:text-foreground">
      <ArrowLeft size={16} aria-hidden="true" /> Lớp học của tôi
    </Link>
  )
}
