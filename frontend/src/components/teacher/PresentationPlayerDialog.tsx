import { useRef } from 'react'
import { useQuery } from '@tanstack/react-query'
import { ChevronLeft, ChevronRight, Loader2, Maximize } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogTitle } from '@/components/ui/dialog'
import { ErrorState } from '@/components/student/StudentUi'
import { getErrorMessage } from '@/lib/format'
import { cn } from '@/lib/utils'

/**
 * Chèn vào HTML của slide-ai: bật sẵn chế độ trình chiếu (như bấm phím P) và nhận lệnh
 * chuyển slide từ các nút bên ngoài qua postMessage. show/fit/i là biến toàn cục của template.
 */
const PLAYER_SCRIPT = `<script>(function(){
document.body.classList.add('present');show(0);fit();
addEventListener('message',function(e){var d=e.data||{};if(d.type==='slide-nav'&&typeof d.step==='number'){show(i+d.step);}});
})();</script>`

function withPlayer(html: string): string {
  const end = html.toLowerCase().lastIndexOf('</body>')
  return end === -1 ? html + PLAYER_SCRIPT : html.slice(0, end) + PLAYER_SCRIPT + html.slice(end)
}

interface PresentationPlayerProps {
  /** Khoá cache react-query của HTML */
  queryKey: readonly unknown[]
  /** Lấy HTML trình chiếu (giáo viên: phiên bản đã lưu; học sinh: phiên bản đã xuất bản) */
  load: () => Promise<string>
  title: string
  enabled?: boolean
  /** Khung chứa slide; mặc định cao theo tỉ lệ 16:9 */
  frameClassName?: string
}

/**
 * Trình chiếu HTML do slide-ai-service dựng. HTML nằm trong iframe srcDoc có sandbox
 * (chỉ cho chạy script điều khiển slide, không cùng origin nên không đọc được token đăng nhập).
 */
export function PresentationPlayer({ queryKey, load, title, enabled = true, frameClassName }: PresentationPlayerProps) {
  const frame = useRef<HTMLIFrameElement>(null)
  const html = useQuery({
    queryKey,
    queryFn: async () => withPlayer(await load()),
    enabled,
    staleTime: Infinity,
    retry: false,
  })

  // iframe sandbox có origin "null" nên targetOrigin phải là '*'; dữ liệu gửi đi không nhạy cảm.
  const nav = (step: number) => frame.current?.contentWindow?.postMessage({ type: 'slide-nav', step }, '*')

  return (
    <div className="flex min-h-0 flex-1 flex-col gap-2">
      {html.data && (
        <div className="flex items-center justify-end gap-1">
          <p className="mr-auto text-xs text-muted-foreground">Dùng phím ← → hoặc các nút để chuyển slide.</p>
          <Button variant="outline" size="icon" className="h-9 w-9 rounded-lg" aria-label="Slide trước" onClick={() => nav(-1)}><ChevronLeft size={16} /></Button>
          <Button variant="outline" size="icon" className="h-9 w-9 rounded-lg" aria-label="Slide sau" onClick={() => nav(1)}><ChevronRight size={16} /></Button>
          <Button variant="outline" size="icon" className="h-9 w-9 rounded-lg" aria-label="Toàn màn hình" onClick={() => frame.current?.requestFullscreen?.()}><Maximize size={16} /></Button>
        </div>
      )}
      <div className={cn('overflow-hidden rounded-xl border border-border/50 bg-muted/30', frameClassName ?? 'aspect-video w-full')}>
        {html.isLoading ? (
          <div className="flex h-full items-center justify-center" role="status">
            <Loader2 className="h-8 w-8 animate-spin text-muted-foreground" aria-label="Đang dựng bản trình chiếu" />
          </div>
        ) : html.isError ? (
          <div className="p-6"><ErrorState message={getErrorMessage(html.error, 'Không dựng được bản trình chiếu.')} /></div>
        ) : html.data ? (
          <iframe
            ref={frame}
            title={`Trình chiếu: ${title}`}
            srcDoc={html.data}
            sandbox="allow-scripts"
            allow="fullscreen"
            onLoad={() => frame.current?.focus()}
            className="h-full w-full border-0 bg-white"
          />
        ) : null}
      </div>
    </div>
  )
}

interface PresentationPlayerDialogProps {
  queryKey: readonly unknown[]
  load: () => Promise<string>
  title: string
  description?: string
  open: boolean
  onClose: () => void
}

export function PresentationPlayerDialog({ queryKey, load, title, description, open, onClose }: PresentationPlayerDialogProps) {
  return (
    <Dialog open={open} onOpenChange={next => { if (!next) onClose() }}>
      <DialogContent className="flex h-[92vh] w-[96vw] max-w-[96vw] flex-col gap-3 p-3 sm:max-w-[96vw] sm:p-4">
        <div className="min-w-0 pr-10">
          <DialogTitle className="truncate">{title}</DialogTitle>
          <DialogDescription>{description ?? 'Phiên bản đã lưu gần nhất.'}</DialogDescription>
        </div>
        <PresentationPlayer queryKey={queryKey} load={load} title={title} enabled={open} frameClassName="min-h-0 flex-1" />
      </DialogContent>
    </Dialog>
  )
}
