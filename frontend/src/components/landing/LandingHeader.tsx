import { useEffect, useRef, useState, useSyncExternalStore } from 'react'
import { Link } from 'react-router-dom'
import { AnimatePresence, motion, useMotionValueEvent, useReducedMotion, useScroll } from 'framer-motion'
import { ArrowRight, ChevronDown, LayoutDashboard, LogIn, LogOut, Menu, Settings, X } from 'lucide-react'
import { Button } from '@/components/ui/button'
import {
  DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuLabel, DropdownMenuSeparator, DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { ThemeToggle } from '@/components/common/ThemeToggle'
import { useAuthStore } from '@/store/authStore'
import { HOME_BY_ROLE, ROLE_TITLE } from '@/lib/roles'
import { cn } from '@/lib/utils'
import { useActiveSection } from '@/hooks/useActiveSection'
import { LANDING_SECTIONS } from './sections'

const SECTION_IDS = LANDING_SECTIONS.map(s => s.id)

// Nội dung trang rộng tối đa 1152px: dưới mốc này thanh dọc sẽ đè lên nội dung nên giữ dạng ngang
const DOCK_MEDIA_QUERY = '(min-width: 1340px)'
const BAR_MAX_WIDTH = 1280
const BAR_HEIGHT = 56
const DOCK_WIDTH = 56
const DOCK_MARGIN = 12
const MORPH_SECONDS = 0.6
const MORPH_TRANSITION = { duration: MORPH_SECONDS, ease: [0.65, 0, 0.35, 1] as const }
const CONTENT_HIDE_MS = 130

function subscribeDockMedia(onChange: () => void) {
  const media = window.matchMedia(DOCK_MEDIA_QUERY)
  media.addEventListener('change', onChange)
  return () => media.removeEventListener('change', onChange)
}

function readViewport() {
  return { width: document.documentElement.clientWidth, height: window.innerHeight }
}

/** Nhãn hiện bên trái khi rê chuột vào icon của thanh dọc. Phần tử cha cần class `group/tip relative`. */
function DockTip({ label }: { label: string }) {
  return (
    <span
      aria-hidden="true"
      className="pointer-events-none absolute right-full top-1/2 mr-3 -translate-y-1/2 whitespace-nowrap rounded-full bg-foreground px-3 py-1.5 text-xs font-medium text-background opacity-0 shadow-lg transition-opacity group-hover/tip:opacity-100 group-focus-visible/tip:opacity-100"
    >
      {label}
    </span>
  )
}

/**
 * Thanh điều hướng trang chủ dạng nổi: chưa đăng nhập → nút Đăng nhập; đã đăng nhập → tên người dùng + menu.
 * Từ mục "Tính năng" trở xuống, trên màn rộng, thanh biến hình thành dạng dọc ở mép phải; cuộn lên thì trở lại.
 * Trình tự biến hình: nội dung cũ mờ đi → khung đổi kích thước → nội dung mới hiện lên. Khung animate kích thước
 * thật thay vì transform scale để viền và góc bo không bị kéo giãn.
 */
export function LandingHeader({ onLogin }: { onLogin: () => void }) {
  const { user, isAuthenticated, logout } = useAuthStore()
  const [mobileOpen, setMobileOpen] = useState(false)
  const [scrolled, setScrolled] = useState(false)
  const signedIn = isAuthenticated && user != null
  const home = signedIn ? HOME_BY_ROLE[user.role] : '/'
  const active = useActiveSection(SECTION_IDS)
  const reduceMotion = useReducedMotion()

  const isWide = useSyncExternalStore(subscribeDockMedia, () => window.matchMedia(DOCK_MEDIA_QUERY).matches, () => false)
  const shouldDock = isWide && active !== null
  const [docked, setDocked] = useState(false)
  const [contentReady, setContentReady] = useState(true)
  const [viewport, setViewport] = useState(readViewport)
  const [dockHeight, setDockHeight] = useState(404)
  const contentRef = useRef<HTMLDivElement>(null)

  // Đầu trang thanh nav trong và nhẹ; cuộn xuống thì đặc hơn để chữ không lẫn vào nội dung phía sau
  const { scrollY } = useScroll()
  useMotionValueEvent(scrollY, 'change', y => setScrolled(y > 16))

  useEffect(() => {
    if (shouldDock === docked) return
    const timer = setTimeout(() => {
      setDocked(shouldDock)
      setContentReady(false)
    }, reduceMotion ? 0 : CONTENT_HIDE_MS)
    return () => clearTimeout(timer)
  }, [shouldDock, docked, reduceMotion])

  useEffect(() => {
    if (contentReady) return
    const timer = setTimeout(() => setContentReady(true), reduceMotion ? 0 : MORPH_SECONDS * 700)
    return () => clearTimeout(timer)
  }, [contentReady, reduceMotion])

  useEffect(() => {
    const onResize = () => setViewport(readViewport())
    window.addEventListener('resize', onResize)
    return () => window.removeEventListener('resize', onResize)
  }, [])

  // Chiều cao thanh dọc phụ thuộc nội dung (đã đăng nhập hay chưa) nên đo thay vì đặt cứng
  useEffect(() => {
    const content = contentRef.current
    if (!content || !docked) return
    const observer = new ResizeObserver(() => setDockHeight(content.offsetHeight + 2))
    observer.observe(content)
    return () => observer.disconnect()
  }, [docked])

  const barWidth = Math.min(viewport.width - 48, BAR_MAX_WIDTH)
  const barBox = docked
    ? { top: (viewport.height - dockHeight) / 2, left: viewport.width - DOCK_WIDTH - DOCK_MARGIN, width: DOCK_WIDTH, height: dockHeight }
    : { top: DOCK_MARGIN, left: (viewport.width - barWidth) / 2, width: barWidth, height: BAR_HEIGHT }
  const contentVisible = shouldDock === docked && contentReady

  // Không truyền behavior: index.css đã đặt cuộn mượt và tự tắt khi người dùng bật giảm chuyển động
  const scrollToTop = () => {
    setMobileOpen(false)
    window.scrollTo(0, 0)
  }

  return (
    <motion.header layoutRoot className={cn('pointer-events-none fixed inset-x-0 top-0 z-50', !isWide && 'px-3 pt-3 sm:px-6')}>
      <motion.div
        key={isWide ? 'wide' : 'narrow'}
        initial={false}
        animate={isWide ? barBox : undefined}
        transition={reduceMotion ? { duration: 0 } : MORPH_TRANSITION}
        style={{ borderRadius: BAR_HEIGHT / 2 }}
        className={cn(
          'pointer-events-auto border backdrop-blur-xl transition-[background-color,border-color,box-shadow] duration-300',
          isWide ? 'absolute' : 'mx-auto h-14 w-full max-w-7xl',
          scrolled
            ? 'border-border/70 bg-background/85 shadow-lg shadow-primary/10'
            : 'border-border/40 bg-background/50 shadow-sm',
        )}
      >
        <motion.div
          ref={contentRef}
          initial={false}
          animate={{ opacity: contentVisible ? 1 : 0 }}
          transition={{ duration: contentVisible ? 0.22 : CONTENT_HIDE_MS / 1000 }}
          className={cn(
            docked
              ? 'flex flex-col items-center gap-1 py-2'
              : 'grid h-full grid-cols-[1fr_auto] items-center gap-4 pl-4 pr-2 lg:grid-cols-[1fr_auto_1fr]',
          )}
        >
          <div className={cn(docked && 'group/tip relative mb-1')}>
            <Link to="/" onClick={scrollToTop} className="flex w-fit items-center gap-2" aria-label="EduMind, về đầu trang">
              <img src="/logo-icon.png" alt="" className="h-8 w-8 object-contain" />
              {!docked && (
                <span className="font-heading text-lg font-bold tracking-tight text-foreground">
                  Edu<span className="text-primary">Mind</span>
                </span>
              )}
            </Link>
            {docked && <DockTip label="EduMind" />}
          </div>

          <nav className={cn(docked ? 'flex flex-col items-center gap-1' : 'hidden items-center lg:flex')} aria-label="Các phần của trang">
            {LANDING_SECTIONS.map(s => (
              <a
                key={s.id}
                href={`#${s.id}`}
                aria-label={docked ? s.label : undefined}
                aria-current={active === s.id ? 'location' : undefined}
                className={cn(
                  'relative isolate rounded-full text-sm font-medium transition-colors',
                  docked ? 'group/tip flex h-10 w-10 items-center justify-center' : 'px-3.5 py-2',
                  active === s.id ? 'text-primary' : 'text-muted-foreground hover:text-foreground',
                )}
              >
                {active === s.id && (
                  <motion.span
                    layoutId="landing-nav-active"
                    className="absolute inset-0 -z-10 rounded-full bg-primary/10"
                    transition={{ type: 'spring', stiffness: 500, damping: 38 }}
                  />
                )}
                {docked ? <s.icon size={18} aria-hidden="true" /> : s.label}
                {docked && <DockTip label={s.label} />}
              </a>
            ))}
          </nav>

          <div className={cn(docked ? 'flex flex-col items-center gap-1 border-t border-border/60 pt-1' : 'flex items-center justify-end gap-1')}>
            <ThemeToggle />
            {signedIn ? (
              <DropdownMenu>
                <DropdownMenuTrigger asChild>
                  <button
                    className={cn(
                      'flex items-center gap-2 rounded-full border border-border/60 bg-background/60 transition-colors hover:bg-muted/60',
                      docked ? 'p-0.5' : 'ml-1 py-1 pl-1 pr-2',
                    )}
                    aria-label={`Tài khoản ${user.name}`}
                  >
                    <span className="flex h-8 w-8 items-center justify-center rounded-full bg-linear-to-tr from-primary to-violet-500 text-sm font-bold text-primary-foreground">
                      {user.name?.charAt(0).toUpperCase() || 'U'}
                    </span>
                    {!docked && (
                      <>
                        <span className="hidden max-w-36 truncate text-sm font-semibold text-foreground sm:block">{user.name}</span>
                        <ChevronDown size={15} className="text-muted-foreground" aria-hidden="true" />
                      </>
                    )}
                  </button>
                </DropdownMenuTrigger>
                <DropdownMenuContent align="end" side={docked ? 'left' : 'bottom'} className="w-60">
                  <DropdownMenuLabel className="font-normal">
                    <p className="truncate text-sm font-semibold text-foreground">{user.name}</p>
                    <p className="truncate text-xs text-muted-foreground">{user.email} · {ROLE_TITLE[user.role]}</p>
                  </DropdownMenuLabel>
                  <DropdownMenuSeparator />
                  <DropdownMenuItem asChild className="cursor-pointer">
                    <Link to={home}><LayoutDashboard size={15} aria-hidden="true" /> Vào trang của tôi</Link>
                  </DropdownMenuItem>
                  <DropdownMenuItem asChild className="cursor-pointer">
                    <Link to={`${home}/settings`}><Settings size={15} aria-hidden="true" /> Cài đặt tài khoản</Link>
                  </DropdownMenuItem>
                  <DropdownMenuSeparator />
                  <DropdownMenuItem className="cursor-pointer text-destructive focus:text-destructive" onClick={logout}>
                    <LogOut size={15} aria-hidden="true" /> Đăng xuất
                  </DropdownMenuItem>
                </DropdownMenuContent>
              </DropdownMenu>
            ) : docked ? (
              <>
                <Button variant="ghost" className="group/tip relative h-10 w-10 rounded-full p-0" onClick={onLogin} aria-label="Đăng nhập">
                  <LogIn size={18} aria-hidden="true" />
                  <DockTip label="Đăng nhập" />
                </Button>
                <Button className="group/tip relative h-10 w-10 rounded-full p-0" onClick={onLogin} aria-label="Bắt đầu miễn phí">
                  <ArrowRight size={18} aria-hidden="true" />
                  <DockTip label="Bắt đầu miễn phí" />
                </Button>
              </>
            ) : (
              <>
                <Button variant="ghost" className="hidden h-10 rounded-full px-4 font-semibold sm:flex" onClick={onLogin}>Đăng nhập</Button>
                {/* Màn hẹp không đủ chỗ cho cả nút menu; hero ngay bên dưới đã có nút này */}
                <Button className="group/cta hidden h-10 rounded-full pl-4 pr-3 font-semibold min-[440px]:flex" onClick={onLogin}>
                  Bắt đầu miễn phí
                  <ArrowRight size={16} className="transition-transform group-hover/cta:translate-x-0.5" aria-hidden="true" />
                </Button>
              </>
            )}
            <button
              className="flex h-10 w-10 items-center justify-center rounded-full text-muted-foreground hover:bg-muted lg:hidden"
              onClick={() => setMobileOpen(o => !o)}
              aria-label={mobileOpen ? 'Đóng menu' : 'Mở menu'}
              aria-expanded={mobileOpen}
            >
              {mobileOpen ? <X size={20} /> : <Menu size={20} />}
            </button>
          </div>
        </motion.div>
      </motion.div>

      <AnimatePresence>
        {mobileOpen && (
          <motion.nav
            initial={{ opacity: 0, y: -8 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: -8 }}
            transition={{ duration: 0.18 }}
            className="pointer-events-auto mx-auto mt-2 max-w-7xl rounded-3xl border border-border/60 bg-background/95 p-2 shadow-xl backdrop-blur-xl lg:hidden"
            aria-label="Các phần của trang"
          >
            {LANDING_SECTIONS.map(s => (
              <a
                key={s.id}
                href={`#${s.id}`}
                onClick={() => setMobileOpen(false)}
                aria-current={active === s.id ? 'location' : undefined}
                className={cn('block rounded-2xl px-4 py-3 text-sm font-medium', active === s.id ? 'bg-primary/10 text-primary' : 'text-foreground hover:bg-muted')}
              >
                {s.label}
              </a>
            ))}
            {!signedIn && (
              <button onClick={() => { setMobileOpen(false); onLogin() }} className="mt-1 block w-full rounded-2xl px-4 py-3 text-left text-sm font-semibold text-primary hover:bg-muted">
                Đăng nhập
              </button>
            )}
          </motion.nav>
        )}
      </AnimatePresence>
    </motion.header>
  )
}
