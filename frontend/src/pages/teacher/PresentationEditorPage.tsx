import { useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  Archive, ArrowDown, ArrowLeft, ArrowUp, Download, History, Loader2, Plus, Rocket, RotateCcw, Save, Trash2, Users,
} from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import {
  addPresentationCollaborator, archivePresentation, exportContentPath, exportPresentation, getPresentation,
  getPresentationCollaborators, getPresentationVersions, publishPresentation, removePresentationCollaborator,
  restorePresentationVersion, updatePresentation,
} from '@/api/presentationApi'
import { CollaboratorsDialog } from '@/components/teacher/CollaboratorsDialog'
import { PresentationStatusPill } from '@/components/teacher/PresentationStatusPill'
import { FormError, TextArea, fieldClass } from '@/components/teacher/FormKit'
import { ErrorState, Pill } from '@/components/student/StudentUi'
import { downloadFromApi } from '@/lib/download'
import { formatDateTime, getErrorMessage } from '@/lib/format'
import type { Presentation, PresentationSlide } from '@/types/presentation'
import { confirmDialog, notify } from '@/lib/notify'

const DEFAULT_LAYOUT = 'TITLE_AND_CONTENT'

interface SlideForm {
  key: string
  title: string
  /** Mỗi dòng là một ý (contentBlock) */
  content: string
  speakerNotes: string
  layout: string
}

const toForms = (slides: PresentationSlide[]): SlideForm[] =>
  [...slides].sort((a, b) => a.order - b.order).map(s => ({
    key: crypto.randomUUID(),
    title: s.title,
    content: s.contentBlocks.join('\n'),
    speakerNotes: s.speakerNotes ?? '',
    layout: s.layout || DEFAULT_LAYOUT,
  }))

const newSlide = (): SlideForm => ({ key: crypto.randomUUID(), title: '', content: '', speakerNotes: '', layout: DEFAULT_LAYOUT })

export default function PresentationEditorPage() {
  const { presentationId = '' } = useParams<{ presentationId: string }>()
  const query = useQuery({
    queryKey: ['presentations', 'detail', presentationId],
    queryFn: () => getPresentation(presentationId),
    refetchInterval: q => (q.state.data?.status === 'GENERATING' ? 4000 : false),
  })

  if (query.isError) {
    return <div className="mx-auto max-w-3xl space-y-4"><BackLink /><ErrorState message={getErrorMessage(query.error, 'Không tải được bản trình chiếu.')} /></div>
  }
  if (!query.data) return <div className="flex justify-center py-20"><Loader2 className="h-8 w-8 animate-spin text-muted-foreground" /></div>

  if (query.data.status === 'GENERATING') {
    return (
      <div className="mx-auto max-w-3xl space-y-4">
        <BackLink />
        <div className="rounded-2xl border border-border/50 bg-card p-10 text-center dark:bg-card/70" role="status">
          <Loader2 className="mx-auto mb-3 h-8 w-8 animate-spin text-primary" aria-hidden="true" />
          <p className="font-semibold text-foreground">AI đang soạn slide cho "{query.data.title}"</p>
          <p className="mt-1 text-sm text-muted-foreground">Trang sẽ tự cập nhật khi xong.</p>
        </div>
      </div>
    )
  }

  // key: khởi tạo lại form khi phiên bản hiện tại thay đổi (lưu, khôi phục)
  return <Editor key={query.data.currentVersionId ?? 'empty'} presentation={query.data} />
}

function Editor({ presentation: p }: { presentation: Presentation }) {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [title, setTitle] = useState(p.title)
  const [slides, setSlides] = useState<SlideForm[]>(() => (p.currentVersion?.slides.length ? toForms(p.currentVersion.slides) : [newSlide()]))
  const [error, setError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)
  const [dialog, setDialog] = useState<'versions' | 'collaborators' | null>(null)

  const editable = p.canEdit && p.status !== 'ARCHIVED'
  const refresh = () => queryClient.invalidateQueries({ queryKey: ['presentations'] })
  const fail = (err: unknown) => setError(getErrorMessage(err))

  const save = useMutation({
    mutationFn: async ({ publish }: { publish: boolean }) => {
      const payload: PresentationSlide[] = slides.map((s, i) => ({
        title: s.title.trim(),
        contentBlocks: s.content.split('\n').map(l => l.trim()).filter(Boolean),
        speakerNotes: s.speakerNotes.trim() || null,
        layout: s.layout,
        order: i,
      }))
      await updatePresentation(p.presentationId, title.trim(), payload)
      if (publish) await publishPresentation(p.presentationId)
      return publish
    },
    onMutate: () => { setError(null); setNotice(null) },
    onSuccess: publish => { setNotice(publish ? 'Đã lưu và xuất bản.' : 'Đã lưu phiên bản mới.'); refresh() },
    onError: fail,
  })

  const exportPptx = useMutation({
    mutationFn: async () => {
      if (!p.currentVersionId) throw new Error('Chưa có phiên bản để xuất.')
      const exp = await exportPresentation(p.presentationId, p.currentVersionId)
      await downloadFromApi(exportContentPath(p.presentationId, exp.exportId), exp.fileName)
    },
    onMutate: () => { setError(null); setNotice(null) },
    onSuccess: () => setNotice('Đã tải file PowerPoint của phiên bản đã lưu gần nhất.'),
    onError: fail,
  })

  const archive = useMutation({
    mutationFn: () => archivePresentation(p.presentationId),
    onSuccess: () => { refresh(); navigate('/teacher/presentations'); notify.success('Đã lưu trữ bản trình chiếu') },
    onError: fail,
  })

  const submit = (publish: boolean) => {
    if (!title.trim()) { setError('Vui lòng nhập tiêu đề.'); return }
    const bad = slides.findIndex(s => !s.title.trim())
    if (bad !== -1) { setError(`Slide ${bad + 1} chưa có tiêu đề.`); return }
    save.mutate({ publish })
  }

  const update = (i: number, patch: Partial<SlideForm>) => setSlides(prev => prev.map((s, j) => (j === i ? { ...s, ...patch } : s)))
  const move = (i: number, dir: -1 | 1) => setSlides(prev => {
    const next = [...prev]
    ;[next[i], next[i + dir]] = [next[i + dir], next[i]]
    return next
  })

  const busy = save.isPending || exportPptx.isPending || archive.isPending

  return (
    <div className="mx-auto max-w-3xl space-y-6 pb-24">
      <BackLink />

      <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
        <div>
          <div className="flex flex-wrap items-center gap-2">
            <h1 className="text-2xl font-bold tracking-tight text-foreground sm:text-3xl">Bản trình chiếu</h1>
            <PresentationStatusPill status={p.status} />
          </div>
          <p className="mt-1 text-sm text-muted-foreground">Phiên bản {p.currentVersionNumber} · cập nhật {formatDateTime(p.updatedAt)}</p>
        </div>
        <div className="flex flex-wrap gap-2">
          <Button variant="outline" className="h-10 rounded-xl" onClick={() => setDialog('versions')}><History size={15} aria-hidden="true" /> Phiên bản</Button>
          <Button variant="outline" className="h-10 rounded-xl" onClick={() => setDialog('collaborators')}><Users size={15} aria-hidden="true" /> Cộng tác</Button>
          {p.canExport && (
            <Button variant="outline" className="h-10 rounded-xl" disabled={busy || !p.currentVersionId} onClick={() => exportPptx.mutate()}>
              {exportPptx.isPending ? <Loader2 className="h-4 w-4 animate-spin" /> : <Download size={15} aria-hidden="true" />} PowerPoint
            </Button>
          )}
          {p.canArchive && p.status !== 'ARCHIVED' && (
            <Button variant="ghost" className="h-10 rounded-xl text-destructive hover:bg-destructive/10" disabled={busy} onClick={async () => { if (await confirmDialog({ title: 'Lưu trữ bản trình chiếu này?', confirmText: 'Lưu trữ', destructive: true })) archive.mutate() }}>
              <Archive size={15} aria-hidden="true" /> Lưu trữ
            </Button>
          )}
        </div>
      </div>

      {p.status === 'FAILED' && <FormError message="AI không tạo được slide cho bản này. Bạn có thể tự soạn slide bên dưới rồi lưu." />}

      <fieldset disabled={!editable || busy} className="space-y-4">
        <Input value={title} onChange={e => setTitle(e.target.value)} aria-label="Tiêu đề bản trình chiếu" className="h-12 text-base font-semibold" />

        {slides.map((s, i) => (
          <div key={s.key} className="space-y-3 rounded-2xl border border-border/50 bg-card p-4 dark:bg-card/70 sm:p-5">
            <div className="flex items-center gap-2">
              <span className="text-xs font-bold uppercase tracking-wider text-muted-foreground">Slide {i + 1}</span>
              <div className="ml-auto flex gap-1">
                <IconBtn label="Lên" disabled={i === 0} onClick={() => move(i, -1)}><ArrowUp size={15} /></IconBtn>
                <IconBtn label="Xuống" disabled={i === slides.length - 1} onClick={() => move(i, 1)}><ArrowDown size={15} /></IconBtn>
                {slides.length > 1 && <IconBtn label={`Xoá slide ${i + 1}`} danger onClick={() => setSlides(prev => prev.filter((_, j) => j !== i))}><Trash2 size={15} /></IconBtn>}
              </div>
            </div>
            <input value={s.title} onChange={e => update(i, { title: e.target.value })} placeholder="Tiêu đề slide" aria-label={`Tiêu đề slide ${i + 1}`} maxLength={255} className={fieldClass} />
            <TextArea rows={4} value={s.content} onChange={e => update(i, { content: e.target.value })} placeholder="Nội dung — mỗi dòng là một ý" aria-label={`Nội dung slide ${i + 1}`} />
            <TextArea rows={2} value={s.speakerNotes} onChange={e => update(i, { speakerNotes: e.target.value })} placeholder="Ghi chú cho người thuyết trình (không hiện trên slide)" aria-label={`Ghi chú slide ${i + 1}`} />
          </div>
        ))}

        <button
          type="button"
          onClick={() => setSlides(prev => [...prev, newSlide()])}
          className="flex w-full items-center justify-center gap-2 rounded-2xl border-2 border-dashed border-border/60 py-3 text-sm font-medium text-muted-foreground transition-all hover:border-primary/40 hover:bg-primary/5 hover:text-primary"
        >
          <Plus size={18} aria-hidden="true" /> Thêm slide
        </button>
      </fieldset>

      <FormError message={error} />

      {editable && (
        <div className="fixed inset-x-0 bottom-0 z-20 border-t border-border/50 bg-background/90 backdrop-blur-xl lg:left-72">
          <div className="mx-auto flex max-w-3xl items-center gap-2 p-3 sm:px-4">
            <p className="hidden text-sm text-muted-foreground sm:block" role="status">{notice ?? `${slides.length} slide`}</p>
            <Button variant="outline" className="ml-auto h-10 rounded-xl" disabled={busy} onClick={() => submit(false)}>
              {save.isPending && !save.variables?.publish ? <Loader2 className="h-4 w-4 animate-spin" /> : <Save size={15} aria-hidden="true" />} Lưu
            </Button>
            {p.canPublish && (
              <Button className="h-10 rounded-xl" disabled={busy} onClick={() => submit(true)}>
                {save.isPending && save.variables?.publish ? <Loader2 className="h-4 w-4 animate-spin" /> : <Rocket size={15} aria-hidden="true" />} Lưu & xuất bản
              </Button>
            )}
          </div>
        </div>
      )}

      <VersionsDialog presentation={p} open={dialog === 'versions'} onClose={() => setDialog(null)} onRestored={refresh} />
      <CollaboratorsDialog
        open={dialog === 'collaborators'}
        onClose={() => setDialog(null)}
        resourceTitle={p.title}
        queryKey={['presentation', p.presentationId]}
        load={() => getPresentationCollaborators(p.presentationId)}
        add={id => addPresentationCollaborator(p.presentationId, id)}
        remove={id => removePresentationCollaborator(p.presentationId, id)}
      />
    </div>
  )
}

interface VersionsDialogProps {
  presentation: Presentation
  open: boolean
  onClose: () => void
  onRestored: () => void
}

function VersionsDialog({ presentation: p, open, onClose, onRestored }: VersionsDialogProps) {
  const versions = useQuery({
    queryKey: ['presentations', 'versions', p.presentationId, p.currentVersionId],
    queryFn: () => getPresentationVersions(p.presentationId),
    enabled: open,
  })
  const restore = useMutation({
    mutationFn: (versionId: string) => restorePresentationVersion(p.presentationId, versionId),
    onSuccess: () => { onRestored(); onClose() },
  })
  const sorted = [...(versions.data ?? [])].sort((a, b) => b.versionNumber - a.versionNumber)

  return (
    <Dialog open={open} onOpenChange={next => { if (!next) { restore.reset(); onClose() } }}>
      <DialogContent className="max-h-[85vh] overflow-y-auto sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>Lịch sử phiên bản</DialogTitle>
          <DialogDescription>Khôi phục sẽ tạo một phiên bản nháp mới từ phiên bản đã chọn.</DialogDescription>
        </DialogHeader>
        <FormError message={restore.isError ? getErrorMessage(restore.error) : null} />
        {versions.isLoading ? (
          <div className="flex justify-center py-6"><Loader2 className="h-5 w-5 animate-spin text-muted-foreground" /></div>
        ) : (
          <ul className="divide-y divide-border/50 rounded-xl border border-border/50">
            {sorted.map(v => (
              <li key={v.versionId} className="flex items-center gap-3 p-3">
                <div className="min-w-0 flex-1">
                  <p className="text-sm font-medium text-foreground">
                    Phiên bản {v.versionNumber} <span className="font-normal text-muted-foreground">· {v.slides.length} slide</span>
                  </p>
                  <p className="text-xs text-muted-foreground">{formatDateTime(v.createdAt)}</p>
                </div>
                {v.versionId === p.currentVersionId ? <Pill tone="blue">Hiện tại</Pill>
                  : v.versionId === p.publishedVersionId ? <Pill tone="emerald">Đã xuất bản</Pill> : null}
                {v.versionId !== p.currentVersionId && p.canEdit && (
                  <Button size="sm" variant="outline" className="h-9 rounded-lg" disabled={restore.isPending} onClick={() => restore.mutate(v.versionId)}>
                    <RotateCcw size={14} aria-hidden="true" /> Khôi phục
                  </Button>
                )}
              </li>
            ))}
          </ul>
        )}
      </DialogContent>
    </Dialog>
  )
}

interface IconBtnProps {
  label: string
  onClick: () => void
  disabled?: boolean
  danger?: boolean
  children: React.ReactNode
}

function IconBtn({ label, onClick, disabled, danger, children }: IconBtnProps) {
  return (
    <button
      type="button"
      onClick={onClick}
      disabled={disabled}
      aria-label={label}
      className={`flex h-9 w-9 items-center justify-center rounded-lg text-muted-foreground transition-colors disabled:opacity-30 ${danger ? 'hover:bg-destructive/10 hover:text-destructive' : 'hover:bg-muted hover:text-foreground'}`}
    >
      {children}
    </button>
  )
}

function BackLink() {
  return (
    <Link to="/teacher/presentations" className="inline-flex min-h-10 items-center gap-1.5 text-sm font-medium text-muted-foreground hover:text-foreground">
      <ArrowLeft size={16} aria-hidden="true" /> Bản trình chiếu
    </Link>
  )
}
