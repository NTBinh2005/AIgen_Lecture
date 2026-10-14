import { useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ArrowLeft, CheckCircle2, Eye, Loader2, Lock, Rocket, Save, Users } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { getLecture, getLectureVersion, parseSlideContent, publishLecture, updateLecture } from '@/api/lectureApi'
import type { LectureAccessScope, LectureResponse, SlideDto } from '@/api/lectureApi'
import { SlideEditor } from '@/components/teacher/SlideEditor'
import { FormError } from '@/components/teacher/FormKit'
import { LectureStatusBadge } from '@/components/common/LectureStatusBadge'
import { ErrorState } from '@/components/student/StudentUi'
import { findInvalidSlide, newSlide, slidesToText, toSlideDtos, toSlideForms } from '@/lib/slides'
import type { SlideForm } from '@/lib/slides'
import { getErrorMessage } from '@/lib/format'
import { cn } from '@/lib/utils'

/** Sửa tiêu đề, phạm vi và slides của bài giảng (PATCH vào bản nháp; bản đã xuất bản sẽ tách nháp mới). */
export default function EditLecturePage() {
  const lectureId = Number(useParams<{ lectureId: string }>().lectureId)

  const lecture = useQuery({ queryKey: ['teacher', 'lecture', lectureId], queryFn: () => getLecture(lectureId) })
  const versionId = lecture.data?.currentVersionId
  const version = useQuery({
    queryKey: ['teacher', 'lecture', lectureId, 'version', versionId],
    queryFn: () => getLectureVersion(lectureId, versionId!),
    enabled: !!versionId,
  })

  if (lecture.isError) {
    return (
      <div className="mx-auto max-w-3xl space-y-4">
        <BackLink />
        <ErrorState message={getErrorMessage(lecture.error, 'Không tải được bài giảng.')} />
      </div>
    )
  }
  if (!lecture.data || (versionId && version.isLoading)) {
    return <div className="flex justify-center py-20"><Loader2 className="h-8 w-8 animate-spin text-muted-foreground" /></div>
  }

  return (
    <LectureForm
      key={lecture.data.currentVersionId ?? lectureId}
      lecture={lecture.data}
      initialSlides={parseSlideContent(version.data?.slideContent)}
    />
  )
}

interface LectureFormProps {
  lecture: LectureResponse
  initialSlides: SlideDto[]
}

function LectureForm({ lecture: l, initialSlides }: LectureFormProps) {
  const queryClient = useQueryClient()
  const [title, setTitle] = useState(l.title)
  const [scope, setScope] = useState<LectureAccessScope>(l.accessScope)
  const [slides, setSlides] = useState<SlideForm[]>(() => (initialSlides.length ? toSlideForms(initialSlides) : [newSlide()]))
  const [error, setError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)

  const save = useMutation({
    mutationFn: async ({ publish }: { publish: boolean }) => {
      const dtos = toSlideDtos(slides)
      await updateLecture(l.lectureId, { title: title.trim(), accessScope: scope, slides: dtos, content: slidesToText(dtos) })
      if (publish) await publishLecture(l.lectureId)
      return publish
    },
    onMutate: () => { setError(null); setNotice(null) },
    onSuccess: publish => {
      queryClient.invalidateQueries({ queryKey: ['teacher'] })
      queryClient.invalidateQueries({ queryKey: ['lectures'] })
      setNotice(publish ? 'Đã lưu và xuất bản.' : 'Đã lưu bản nháp.')
    },
    onError: err => setError(getErrorMessage(err)),
  })

  const submit = (publish: boolean) => {
    if (!title.trim()) { setError('Vui lòng nhập tiêu đề bài giảng.'); return }
    const invalid = findInvalidSlide(slides)
    if (invalid !== null) { setError(`Slide ${invalid} cần có tiêu đề, ít nhất 1 bullet point và nội dung đọc.`); return }
    save.mutate({ publish })
  }

  const archived = l.status === 'ARCHIVED'

  return (
    <div className="mx-auto max-w-3xl space-y-6 pb-24">
      <BackLink />
      <div className="flex flex-wrap items-center gap-3">
        <h1 className="text-2xl font-bold tracking-tight text-foreground sm:text-3xl">Sửa bài giảng</h1>
        <LectureStatusBadge status={l.status} />
      </div>
      {l.status === 'PUBLISHED' && (
        <p className="rounded-xl bg-primary/5 p-3 text-sm text-muted-foreground">
          Bài giảng đang xuất bản. Khi lưu, hệ thống tạo bản nháp mới; học sinh vẫn thấy bản cũ cho tới khi bạn xuất bản lại.
        </p>
      )}

      <fieldset disabled={archived || save.isPending} className="space-y-4">
        <Input value={title} onChange={e => setTitle(e.target.value)} aria-label="Tiêu đề bài giảng" className="h-12 text-base font-semibold" />
        <div className="grid grid-cols-1 gap-3 sm:grid-cols-2" role="radiogroup" aria-label="Phạm vi truy cập">
          {([
            { value: 'PRIVATE', icon: Lock, label: 'Riêng tư', desc: 'Chỉ bạn và cộng tác viên' },
            { value: 'CLASS', icon: Users, label: 'Lớp học', desc: 'Giao được cho lớp' },
          ] as const).map(o => (
            <button
              key={o.value}
              type="button"
              role="radio"
              aria-checked={scope === o.value}
              onClick={() => setScope(o.value)}
              className={cn(
                'flex items-center gap-3 rounded-xl border-2 p-3 text-left transition-colors',
                scope === o.value ? 'border-primary bg-primary/5' : 'border-border hover:border-primary/40',
              )}
            >
              <o.icon size={18} className="text-primary" aria-hidden="true" />
              <span>
                <span className="block text-sm font-semibold text-foreground">{o.label}</span>
                <span className="text-xs text-muted-foreground">{o.desc}</span>
              </span>
            </button>
          ))}
        </div>
        <SlideEditor slides={slides} onChange={setSlides} />
      </fieldset>

      <FormError message={error} />

      {!archived && (
        <div className="fixed inset-x-0 bottom-0 z-20 border-t border-border/50 bg-background/90 backdrop-blur-xl lg:left-72">
          <div className="mx-auto flex max-w-3xl items-center gap-2 p-3 sm:px-4">
            {notice ? (
              <p className="flex items-center gap-1.5 text-sm text-emerald-600 dark:text-emerald-400" role="status">
                <CheckCircle2 size={15} aria-hidden="true" />{notice}
              </p>
            ) : (
              <Link to={`/teacher/lectures/${l.lectureId}`} className="hidden items-center gap-1.5 text-sm text-muted-foreground hover:text-foreground sm:flex">
                <Eye size={15} aria-hidden="true" /> Xem trước
              </Link>
            )}
            <Button variant="outline" className="ml-auto h-10 rounded-xl" disabled={save.isPending} onClick={() => submit(false)}>
              {save.isPending && !save.variables?.publish ? <Loader2 className="h-4 w-4 animate-spin" /> : <Save size={15} aria-hidden="true" />} Lưu nháp
            </Button>
            <Button className="h-10 rounded-xl" disabled={save.isPending} onClick={() => submit(true)}>
              {save.isPending && save.variables?.publish ? <Loader2 className="h-4 w-4 animate-spin" /> : <Rocket size={15} aria-hidden="true" />} Lưu & xuất bản
            </Button>
          </div>
        </div>
      )}
    </div>
  )
}

function BackLink() {
  return (
    <Link to="/teacher/lectures" className="inline-flex min-h-10 items-center gap-1.5 text-sm font-medium text-muted-foreground hover:text-foreground">
      <ArrowLeft size={16} aria-hidden="true" /> Bài giảng của tôi
    </Link>
  )
}
