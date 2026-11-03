import type { ReactNode } from 'react'
import { motion, type Variants } from 'framer-motion'
import { BarChart3, Bell, Check, ClipboardCheck, Presentation, QrCode, School, Sparkles, Video, Wand2 } from 'lucide-react'
import type { LucideIcon } from 'lucide-react'
import { cn } from '@/lib/utils'
import { LectureGenerationDemo } from './LectureGenerationDemo'

const QUIZ_OPTIONS = [
  { text: 'Glucose và khí oxy', correct: true },
  { text: 'Khí CO₂ và nước', correct: false },
  { text: 'Chỉ có nước', correct: false },
]

/** Minh hoạ màn chấm bài: trắc nghiệm tự chấm, tự luận có gợi ý điểm từ AI. */
function QuizPreview() {
  return (
    <div className="space-y-3" aria-hidden="true">
      <div className="rounded-2xl border border-border/60 bg-background/80 p-4">
        <p className="text-sm font-semibold text-foreground">Sản phẩm của quang hợp gồm những chất nào?</p>
        <ul className="mt-3 space-y-2">
          {QUIZ_OPTIONS.map(o => (
            <li
              key={o.text}
              className={cn(
                'flex items-center justify-between rounded-xl border px-3 py-2 text-sm',
                o.correct ? 'border-emerald-500/40 bg-emerald-500/10 font-medium text-foreground' : 'border-border/60 text-muted-foreground',
              )}
            >
              {o.text}
              {o.correct && <Check size={16} className="text-emerald-600 dark:text-emerald-400" />}
            </li>
          ))}
        </ul>
      </div>
      <div className="flex items-center gap-3 rounded-2xl border border-primary/30 bg-primary/5 p-3">
        <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-xl bg-primary/10 text-primary"><Sparkles size={17} /></span>
        <div className="min-w-0 flex-1">
          <p className="text-sm font-semibold text-foreground">Câu tự luận: AI gợi ý 8/10 điểm</p>
          <p className="truncate text-xs text-muted-foreground">Giáo viên xem lại và chốt điểm cuối cùng</p>
        </div>
      </div>
    </div>
  )
}

/** Cột trang trí cho ô báo cáo, không mang số liệu. */
function ReportBars() {
  return (
    <div className="hidden h-20 w-40 shrink-0 items-end gap-2 lg:flex" aria-hidden="true">
      {[45, 70, 55, 90, 65, 80].map((h, i) => (
        <span key={i} className="flex-1 rounded-t-md bg-linear-to-t from-primary/30 to-primary" style={{ height: `${h}%` }} />
      ))}
    </div>
  )
}

interface TileProps {
  icon: LucideIcon
  title: string
  text: string
  variants: Variants
  className?: string
  preview?: ReactNode
  aside?: ReactNode
}

function Tile({ icon: Icon, title, text, variants, className, preview, aside }: TileProps) {
  return (
    <motion.li
      variants={variants}
      className={cn(
        'group flex flex-col rounded-3xl border border-border/50 bg-card/70 p-6 shadow-sm backdrop-blur transition-[translate,border-color,box-shadow] duration-300 hover:-translate-y-1 hover:border-primary/40 hover:shadow-lg hover:shadow-primary/10 dark:bg-card/50',
        className,
      )}
    >
      <div className="flex items-center gap-6">
        <div className="min-w-0 flex-1">
          <span className="flex h-11 w-11 items-center justify-center rounded-xl bg-primary/10 text-primary transition-colors group-hover:bg-primary group-hover:text-primary-foreground">
            <Icon size={22} aria-hidden="true" />
          </span>
          <h3 className={cn('mt-4 font-semibold text-foreground', preview && 'text-xl font-bold')}>{title}</h3>
          <p className="mt-2 text-sm leading-relaxed text-muted-foreground">{text}</p>
        </div>
        {aside}
      </div>
      {preview && <div className="mt-6 flex-1">{preview}</div>}
    </motion.li>
  )
}

const BIG = 'sm:col-span-2 lg:row-span-2'

/**
 * Lưới tính năng: 2 ô lớn cho chức năng cốt lõi, các ô nhỏ xếp quanh.
 * Thứ tự phần tử quyết định vị trí trên lưới 3 cột; grid-flow-dense lấp chỗ trống ở lưới 2 cột.
 */
export function FeatureBento({ variants, container }: { variants: Variants; container: Variants }) {
  return (
    <motion.ul
      className="grid grid-flow-dense grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3"
      initial="hidden"
      whileInView="show"
      viewport={{ once: true, margin: '-60px' }}
      variants={container}
    >
      <Tile
        icon={Wand2}
        title="Soạn bài giảng bằng AI"
        text="Tải tài liệu lên, AI tự chia thành các slide có ý chính, lời giảng và gợi ý hoạt động cho từng phần. Hình minh hoạ đã tăng tốc, thực tế mất vài phút."
        variants={variants}
        className={BIG}
        preview={<LectureGenerationDemo />}
      />
      <Tile icon={Video} title="Video có giọng đọc" text="Dựng video bài giảng từ bộ slide với giọng đọc và trợ giảng ảo, chỉ khi giáo viên yêu cầu." variants={variants} />
      <Tile icon={School} title="Lớp học & lịch học" text="Mời học sinh bằng mã lớp, xếp lịch lặp hằng tuần, giao bài giảng và bài kiểm tra theo lớp." variants={variants} />
      <Tile icon={QrCode} title="Buổi học trực tuyến" text="Mở phòng học, điểm danh bằng mã QR, đính kèm bản trình chiếu cho học sinh xem lại." variants={variants} />
      <Tile
        icon={ClipboardCheck}
        title="Bài kiểm tra & chấm điểm"
        text="Tạo quiz thủ công hoặc bằng AI, tự chấm trắc nghiệm, AI gợi ý điểm cho câu tự luận."
        variants={variants}
        className={BIG}
        preview={<QuizPreview />}
      />
      <Tile icon={Presentation} title="Bản trình chiếu" text="Chuyển bài giảng thành bản trình chiếu theo mẫu, chỉnh sửa, lưu phiên bản và cộng tác cùng đồng nghiệp." variants={variants} />
      <Tile icon={Bell} title="Thông báo tức thì" text="Học sinh được báo ngay khi có bài mới, sắp hết hạn nộp hay có điểm; giáo viên biết khi có bài chờ chấm." variants={variants} />
      <Tile
        icon={BarChart3}
        title="Báo cáo & xuất file"
        text="Theo dõi tiến độ từng học sinh, xuất bảng điểm Excel hoặc kết quả PDF chỉ với một cú bấm."
        variants={variants}
        className="lg:col-span-2"
        aside={<ReportBars />}
      />
    </motion.ul>
  )
}
