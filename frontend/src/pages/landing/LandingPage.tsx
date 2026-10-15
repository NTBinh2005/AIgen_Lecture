import type { ReactNode } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { motion, type Variants } from 'framer-motion'
import {
  ArrowRight, BarChart3, Bell, BookOpenCheck, CalendarClock, Check, ClipboardCheck, FileUp, GraduationCap,
  LayoutDashboard, PlaySquare, Presentation, QrCode, School, ShieldCheck, Sparkles, Users, Video, Wand2,
} from 'lucide-react'
import { Button } from '@/components/ui/button'
import { LoginModal } from '@/components/auth/LoginModal'
import { LandingHeader } from '@/components/landing/LandingHeader'
import { PricingSection } from '@/components/landing/PricingSection'
import { useAuthStore } from '@/store/authStore'
import type { User } from '@/store/authStore'
import { HOME_BY_ROLE } from '@/lib/roles'

// Thông tin mô tả đúng khả năng hiện có của sản phẩm (không dùng số liệu tự đặt ra)
const FACTS = [
  { value: 'PDF · DOCX · PPTX', label: 'Định dạng tài liệu' },
  { value: '5 – 20', label: 'Câu hỏi mỗi bài' },
  { value: 'Tiếng Việt / Anh', label: 'Ngôn ngữ bài giảng' },
]

const FEATURES = [
  { icon: Wand2, title: 'Soạn bài giảng bằng AI', text: 'Tải tài liệu lên, AI tự chia thành các slide có ý chính, lời giảng và gợi ý hoạt động cho từng phần.' },
  { icon: Video, title: 'Video có giọng đọc', text: 'Dựng video bài giảng từ bộ slide với giọng đọc và trợ giảng ảo, chỉ khi giáo viên yêu cầu.' },
  { icon: ClipboardCheck, title: 'Bài kiểm tra & chấm điểm', text: 'Tạo quiz thủ công hoặc bằng AI, tự chấm trắc nghiệm, AI gợi ý điểm cho câu tự luận.' },
  { icon: School, title: 'Lớp học & lịch học', text: 'Mời học sinh bằng mã lớp, xếp lịch lặp hằng tuần, giao bài giảng và bài kiểm tra theo lớp.' },
  { icon: QrCode, title: 'Buổi học trực tuyến', text: 'Mở phòng học, điểm danh bằng mã QR, đính kèm bản trình chiếu cho học sinh xem lại.' },
  { icon: Bell, title: 'Thông báo tức thì', text: 'Học sinh được báo ngay khi có bài mới, sắp hết hạn nộp hay có điểm; giáo viên biết khi có bài chờ chấm.' },
  { icon: BarChart3, title: 'Báo cáo & xuất file', text: 'Theo dõi tiến độ từng học sinh, xuất bảng điểm Excel hoặc kết quả PDF chỉ với một cú bấm.' },
  { icon: Presentation, title: 'Bản trình chiếu', text: 'Chuyển bài giảng thành bản trình chiếu theo mẫu, chỉnh sửa, lưu phiên bản và cộng tác cùng đồng nghiệp.' },
]

const STEPS = [
  { icon: FileUp, title: 'Tải tài liệu lên', text: 'Giáo án, giáo trình hay slide có sẵn — PDF, Word hoặc PowerPoint.' },
  { icon: Sparkles, title: 'AI soạn bài giảng', text: 'Nhận bộ slide, lời giảng và câu hỏi kiểm tra trong vài phút; chỉnh sửa tuỳ ý trước khi xuất bản.' },
  { icon: Users, title: 'Giao cho lớp & theo dõi', text: 'Học sinh học và làm bài trên mọi thiết bị; bạn xem kết quả và chấm bài ở một nơi.' },
]

const AUDIENCES = [
  {
    icon: BookOpenCheck,
    title: 'Giáo viên',
    points: ['Tiết kiệm thời gian soạn bài và ra đề', 'Quản lý nhiều lớp, lịch và buổi học trực tuyến', 'Chấm bài nhanh với gợi ý điểm từ AI', 'Cộng tác soạn bài cùng đồng nghiệp'],
  },
  {
    icon: GraduationCap,
    title: 'Học sinh',
    points: ['Học qua slide và video sinh động', 'Làm bài kiểm tra, xem kết quả và lời giải', 'Lịch học, buổi live và thông báo ở một chỗ', 'Dùng tốt trên điện thoại'],
  },
  {
    icon: ShieldCheck,
    title: 'Nhà trường',
    points: ['Quản lý tài khoản, lớp học và bài giảng', 'Theo dõi thống kê và nhật ký hệ thống', 'Quản lý gói, thanh toán và hoàn tiền', 'Phân quyền rõ ràng theo vai trò'],
  },
]

const FAQ = [
  { q: 'AI soạn bài giảng từ những loại tài liệu nào?', a: 'File PDF, DOCX và PPTX. Bạn chọn ngôn ngữ bài giảng là tiếng Việt, tiếng Anh, hoặc giữ ngôn ngữ của tài liệu.' },
  { q: 'Tôi có sửa được nội dung AI tạo ra không?', a: 'Có. Mọi slide, lời giảng và câu hỏi đều chỉnh sửa được trước khi xuất bản; mỗi lần sửa được lưu thành một phiên bản và có thể khôi phục.' },
  { q: 'Học sinh tham gia lớp như thế nào?', a: 'Giáo viên gửi mã lớp, học sinh nhập mã ở mục "Lớp học của tôi". Giáo viên cũng có thể thêm học sinh trực tiếp bằng email.' },
  { q: 'Thanh toán và gia hạn gói ra sao?', a: 'Thanh toán bằng QR chuyển khoản qua SePay. Gói có thời hạn và không tự gia hạn; mua thêm khi còn hạn thì thời gian được cộng dồn.' },
  { q: 'Có dùng được trên điện thoại không?', a: 'Có. Giao diện web tự co giãn theo màn hình; học sinh học bài, làm bài kiểm tra và nhận thông báo ngay trên điện thoại.' },
]

const fadeUp: Variants = {
  hidden: { opacity: 0, y: 24 },
  show: { opacity: 1, y: 0, transition: { type: 'spring', stiffness: 260, damping: 26 } },
}
const stagger: Variants = { hidden: {}, show: { transition: { staggerChildren: 0.08 } } }

function Section({ id, eyebrow, title, description, children }: { id: string; eyebrow: string; title: string; description?: string; children: ReactNode }) {
  return (
    <section id={id} className="relative z-10 scroll-mt-20 px-4 py-20 sm:px-6 md:py-28">
      <div className="mx-auto max-w-6xl">
        <motion.div className="mx-auto max-w-2xl text-center" initial="hidden" whileInView="show" viewport={{ once: true, margin: '-80px' }} variants={fadeUp}>
          <p className="text-sm font-semibold uppercase tracking-wider text-primary">{eyebrow}</p>
          <h2 className="mt-3 text-3xl font-bold tracking-tight text-foreground md:text-4xl">{title}</h2>
          {description && <p className="mt-4 text-base text-muted-foreground md:text-lg">{description}</p>}
        </motion.div>
        <div className="mt-12 md:mt-16">{children}</div>
      </div>
    </section>
  )
}

/** CTA chính: chưa đăng nhập → mở form đăng nhập; đã đăng nhập → vào trang của mình. */
function PrimaryCta({ user, onLogin }: { user: User | null; onLogin: () => void }) {
  const cls = 'h-12 rounded-xl px-7 text-base font-semibold shadow-xl shadow-primary/25'
  return user ? (
    <Button size="lg" className={cls} asChild>
      <Link to={HOME_BY_ROLE[user.role]}>
        <LayoutDashboard size={18} aria-hidden="true" /> Vào trang của tôi
      </Link>
    </Button>
  ) : (
    <Button size="lg" className={cls} onClick={onLogin}>
      Bắt đầu miễn phí <ArrowRight size={18} aria-hidden="true" />
    </Button>
  )
}

export default function LandingPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const isLoginOpen = searchParams.get('login') === 'true'
  const user = useAuthStore(s => (s.isAuthenticated ? s.user : null))

  const setLoginOpen = (open: boolean) => {
    const next = new URLSearchParams(searchParams)
    if (open) next.set('login', 'true')
    else next.delete('login')
    setSearchParams(next)
  }
  const openLogin = () => setLoginOpen(true)

  return (
    <div className="min-h-screen overflow-x-hidden bg-background text-foreground selection:bg-primary/30">
      <LoginModal isOpen={isLoginOpen && !user} onOpenChange={setLoginOpen} />

      {/* Nền chuyển động */}
      <div className="pointer-events-none fixed inset-0 z-0 overflow-hidden" aria-hidden="true">
        <div className="animate-blob absolute left-[-10%] top-[-10%] h-125 w-125 rounded-full bg-primary/10 blur-[120px] dark:bg-primary/15" />
        <div className="animate-blob-delay-2 absolute right-[-10%] top-[20%] h-100 w-100 rounded-full bg-indigo-500/10 blur-[100px] dark:bg-indigo-500/15" />
        <div className="animate-blob-delay-4 absolute bottom-[-10%] left-[20%] h-150 w-150 rounded-full bg-violet-500/10 blur-[120px] dark:bg-violet-500/15" />
      </div>

      <LandingHeader onLogin={openLogin} />

      {/* ── Hero ── */}
      <motion.section
        className="relative z-10 mx-auto grid w-full max-w-6xl grid-cols-1 items-center gap-12 px-4 pb-16 pt-28 sm:px-6 lg:min-h-screen lg:grid-cols-[1.15fr_0.85fr] lg:pb-10 lg:pt-24"
        variants={stagger}
        initial="hidden"
        animate="show"
      >
        <div className="space-y-8">
          <motion.div variants={fadeUp} className="inline-flex items-center gap-2 rounded-full border border-primary/20 bg-primary/10 px-4 py-1.5 text-sm font-medium text-primary">
            <Wand2 size={16} aria-hidden="true" /> Nền tảng bài giảng tương tác bằng AI
          </motion.div>
          <motion.h1 variants={fadeUp} className="text-4xl font-bold tracking-tight text-foreground md:text-6xl lg:text-[4rem] lg:leading-[1.12]">
            Biến tài liệu thành <br className="hidden sm:block" />
            <span className="text-gradient-primary">bài giảng sống động.</span>
          </motion.h1>
          <motion.p variants={fadeUp} className="max-w-xl text-lg leading-relaxed text-muted-foreground md:text-xl">
            {user
              ? `Chào mừng trở lại, ${user.name}. Tiếp tục với lớp học, bài giảng và bài kiểm tra của bạn.`
              : 'Giáo viên tải tài liệu lên, AI soạn slide, lời giảng và câu hỏi kiểm tra. Học sinh học, làm bài và nhận kết quả ngay trên mọi thiết bị.'}
          </motion.p>
          <motion.div variants={fadeUp} className="flex flex-wrap items-center gap-3">
            <PrimaryCta user={user} onLogin={openLogin} />
            <Button variant="outline" size="lg" className="h-12 rounded-xl bg-background/50 px-7 text-base font-semibold backdrop-blur" asChild>
              <a href="#cach-hoat-dong">Xem cách hoạt động</a>
            </Button>
          </motion.div>
          <motion.dl variants={fadeUp} className="grid grid-cols-1 gap-6 border-t border-border/50 pt-8 sm:grid-cols-3">
            {FACTS.map(f => (
              <div key={f.label}>
                <dt className="text-[11px] font-bold uppercase tracking-wider text-muted-foreground">{f.label}</dt>
                <dd className="mt-1.5 text-xl font-bold text-foreground">{f.value}</dd>
              </div>
            ))}
          </motion.dl>
        </div>

        {/* Minh hoạ: một bài giảng đang được tạo */}
        <motion.div variants={fadeUp} className="relative mx-auto w-full max-w-md">
          <div className="absolute inset-0 rounded-[2.5rem] bg-linear-to-tr from-primary/15 to-indigo-500/15 blur-2xl" aria-hidden="true" />
          <div className="relative rounded-[2rem] border border-border/50 bg-card/70 p-6 shadow-2xl backdrop-blur-2xl sm:p-7">
            <div className="mb-5 flex items-center justify-between border-b border-border/40 pb-4">
              <div className="flex items-center gap-2" aria-hidden="true">
                <span className="h-3 w-3 rounded-full bg-destructive/70" />
                <span className="h-3 w-3 rounded-full bg-amber-500/70" />
                <span className="h-3 w-3 rounded-full bg-emerald-500/70" />
              </div>
              <span className="rounded-full border border-primary/20 bg-primary/10 px-3 py-1 text-xs font-semibold text-primary">Quang hợp.pdf</span>
            </div>
            <div className="space-y-3">
              {[
                { tag: 'Slide 1 · Mở đầu', text: 'Cây xanh "nấu ăn" bằng ánh sáng như thế nào?', icon: PlaySquare, tone: 'text-indigo-500' },
                { tag: 'Slide 2 · Giảng giải', text: 'Pha sáng và pha tối diễn ra ở lục lạp', icon: Sparkles, tone: 'text-violet-500' },
                { tag: 'Câu hỏi · Trắc nghiệm', text: 'Sản phẩm của quang hợp gồm những chất nào?', icon: ClipboardCheck, tone: 'text-blue-500' },
                { tag: 'Buổi học · Thứ 2, 08:00', text: 'Giao cho lớp 10A1 · 32 học sinh', icon: CalendarClock, tone: 'text-emerald-500' },
              ].map((c, i) => (
                <motion.div
                  key={c.tag}
                  initial={{ opacity: 0, x: 24 }}
                  animate={{ opacity: 1, x: 0 }}
                  transition={{ delay: 0.5 + i * 0.15 }}
                  className="rounded-2xl border border-border/50 bg-background/80 p-4 shadow-sm"
                >
                  <div className="mb-1.5 flex items-center justify-between">
                    <p className={`text-[11px] font-bold uppercase tracking-wider ${c.tone}`}>{c.tag}</p>
                    <c.icon size={14} className={c.tone} aria-hidden="true" />
                  </div>
                  <p className="text-sm font-semibold text-foreground">{c.text}</p>
                </motion.div>
              ))}
            </div>
          </div>
        </motion.div>
      </motion.section>

      {/* ── Tính năng ── */}
      <Section id="tinh-nang" eyebrow="Tính năng" title="Mọi thứ cho một lớp học hiện đại" description="Từ soạn bài, giảng dạy tới kiểm tra và theo dõi kết quả — trên cùng một nền tảng.">
        <motion.ul className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4" initial="hidden" whileInView="show" viewport={{ once: true, margin: '-60px' }} variants={stagger}>
          {FEATURES.map(f => (
            <motion.li key={f.title} variants={fadeUp} className="group rounded-2xl border border-border/50 bg-card/70 p-6 backdrop-blur transition-all hover:-translate-y-1 hover:border-primary/40 hover:shadow-lg dark:bg-card/50">
              <span className="flex h-11 w-11 items-center justify-center rounded-xl bg-primary/10 text-primary transition-colors group-hover:bg-primary group-hover:text-primary-foreground">
                <f.icon size={22} aria-hidden="true" />
              </span>
              <h3 className="mt-4 font-semibold text-foreground">{f.title}</h3>
              <p className="mt-2 text-sm leading-relaxed text-muted-foreground">{f.text}</p>
            </motion.li>
          ))}
        </motion.ul>
      </Section>

      {/* ── Cách hoạt động ── */}
      <Section id="cach-hoat-dong" eyebrow="Cách hoạt động" title="Ba bước từ tài liệu tới lớp học">
        <motion.ol className="grid gap-6 md:grid-cols-3" initial="hidden" whileInView="show" viewport={{ once: true, margin: '-60px' }} variants={stagger}>
          {STEPS.map((s, i) => (
            <motion.li key={s.title} variants={fadeUp} className="relative rounded-3xl border border-border/50 bg-card/70 p-7 backdrop-blur dark:bg-card/50">
              <span className="absolute right-6 top-5 text-5xl font-black text-primary/10" aria-hidden="true">{i + 1}</span>
              <span className="flex h-12 w-12 items-center justify-center rounded-2xl bg-linear-to-br from-primary to-violet-500 text-primary-foreground shadow-lg shadow-primary/25">
                <s.icon size={22} aria-hidden="true" />
              </span>
              <h3 className="mt-5 text-lg font-semibold text-foreground">{s.title}</h3>
              <p className="mt-2 text-sm leading-relaxed text-muted-foreground">{s.text}</p>
            </motion.li>
          ))}
        </motion.ol>
      </Section>

      {/* ── Dành cho ai ── */}
      <Section id="danh-cho-ai" eyebrow="Dành cho ai" title="Một nền tảng, ba vai trò">
        <motion.ul className="grid gap-6 md:grid-cols-3" initial="hidden" whileInView="show" viewport={{ once: true, margin: '-60px' }} variants={stagger}>
          {AUDIENCES.map(a => (
            <motion.li key={a.title} variants={fadeUp} className="rounded-3xl border border-border/50 bg-card/70 p-7 backdrop-blur dark:bg-card/50">
              <span className="flex h-11 w-11 items-center justify-center rounded-xl bg-primary/10 text-primary"><a.icon size={22} aria-hidden="true" /></span>
              <h3 className="mt-4 text-lg font-semibold text-foreground">{a.title}</h3>
              <ul className="mt-4 space-y-2.5">
                {a.points.map(p => (
                  <li key={p} className="flex items-start gap-2.5 text-sm text-foreground">
                    <Check size={16} className="mt-0.5 shrink-0 text-emerald-600 dark:text-emerald-400" aria-hidden="true" />{p}
                  </li>
                ))}
              </ul>
            </motion.li>
          ))}
        </motion.ul>
      </Section>

      {/* ── Bảng giá ── */}
      <Section id="bang-gia" eyebrow="Bảng giá" title="Chọn gói phù hợp" description="Giá hiển thị là giá đang áp dụng trên hệ thống.">
        <PricingSection onLogin={openLogin} />
      </Section>

      {/* ── Hỏi đáp ── */}
      <Section id="hoi-dap" eyebrow="Hỏi đáp" title="Câu hỏi thường gặp">
        <div className="mx-auto max-w-3xl divide-y divide-border/60 rounded-3xl border border-border/50 bg-card/70 backdrop-blur dark:bg-card/50">
          {FAQ.map(item => (
            <details key={item.q} className="group px-6 py-5">
              <summary className="flex cursor-pointer list-none items-center justify-between gap-4 font-medium text-foreground">
                {item.q}
                <span className="text-xl leading-none text-muted-foreground transition-transform group-open:rotate-45" aria-hidden="true">+</span>
              </summary>
              <p className="mt-3 text-sm leading-relaxed text-muted-foreground">{item.a}</p>
            </details>
          ))}
        </div>
      </Section>

      {/* ── Kêu gọi hành động ── */}
      <section className="relative z-10 px-4 pb-24 sm:px-6">
        <div className="mx-auto max-w-6xl overflow-hidden rounded-[2rem] bg-linear-to-br from-primary to-violet-600 px-6 py-14 text-center text-primary-foreground shadow-2xl shadow-primary/20 sm:px-12">
          <h2 className="text-3xl font-bold tracking-tight md:text-4xl">
            {user ? 'Sẵn sàng cho buổi học tiếp theo?' : 'Sẵn sàng cho lớp học đầu tiên?'}
          </h2>
          <p className="mx-auto mt-4 max-w-xl text-base text-primary-foreground/85">
            {user ? 'Mở trang của bạn để xem lớp học, bài giảng và thông báo mới.' : 'Tạo tài khoản miễn phí và soạn bài giảng đầu tiên chỉ trong vài phút.'}
          </p>
          <div className="mt-8 flex justify-center">
            {user ? (
              <Button size="lg" variant="secondary" className="h-12 rounded-xl px-7 text-base font-semibold" asChild>
                <Link to={HOME_BY_ROLE[user.role]}><LayoutDashboard size={18} aria-hidden="true" /> Vào trang của tôi</Link>
              </Button>
            ) : (
              <Button size="lg" variant="secondary" className="h-12 rounded-xl px-7 text-base font-semibold" onClick={openLogin}>
                Bắt đầu miễn phí <ArrowRight size={18} aria-hidden="true" />
              </Button>
            )}
          </div>
        </div>
      </section>

      {/* ── Chân trang ── */}
      <footer className="relative z-10 border-t border-border/50 bg-background/60 backdrop-blur">
        <div className="mx-auto flex max-w-6xl flex-col gap-6 px-4 py-10 sm:px-6 md:flex-row md:items-center md:justify-between">
          <div className="flex items-center gap-2.5">
            <span className="flex h-8 w-8 items-center justify-center rounded-xl bg-primary text-primary-foreground"><Sparkles size={16} aria-hidden="true" /></span>
            <span className="font-bold text-foreground">Edu<span className="text-primary">Mind</span></span>
            <span className="text-sm text-muted-foreground">· Bài giảng tương tác bằng AI</span>
          </div>
          <nav className="flex flex-wrap gap-x-5 gap-y-2 text-sm text-muted-foreground" aria-label="Liên kết cuối trang">
            <a href="#tinh-nang" className="hover:text-foreground">Tính năng</a>
            <a href="#bang-gia" className="hover:text-foreground">Bảng giá</a>
            <a href="#hoi-dap" className="hover:text-foreground">Hỏi đáp</a>
          </nav>
          <p className="text-sm text-muted-foreground">© {new Date().getFullYear()} EduMind</p>
        </div>
      </footer>
    </div>
  )
}
