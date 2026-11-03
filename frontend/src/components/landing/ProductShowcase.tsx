import { useRef } from 'react'
import { motion, useReducedMotion, useScroll, useTransform } from 'framer-motion'

const ALT = 'Bảng điều khiển học sinh của EduMind với bài kiểm tra cần làm, lịch học và bài giảng mới'

function Screenshot({ theme }: { theme: 'light' | 'dark' }) {
  // Ảnh của chế độ còn lại đang display:none nên loading="lazy" sẽ không tải thừa
  return (
    <picture className={theme === 'light' ? 'block dark:hidden' : 'hidden dark:block'}>
      <source media="(max-width: 639px)" srcSet={`/landing/dashboard-mobile-${theme}.webp`} width={780} height={1560} />
      <img src={`/landing/dashboard-desktop-${theme}.webp`} alt={ALT} width={2160} height={1350} loading="lazy" className="h-auto w-full" />
    </picture>
  )
}

/** Ảnh giao diện thật trong khung trình duyệt: nghiêng nhẹ rồi thẳng dần khi cuộn. Ảnh chụp từ dữ liệu demo. */
export function ProductShowcase() {
  const ref = useRef<HTMLDivElement>(null)
  const reduceMotion = useReducedMotion()
  const { scrollYProgress } = useScroll({ target: ref, offset: ['start end', 'start 0.25'] })
  const rotateX = useTransform(scrollYProgress, [0, 1], [16, 0])
  const scale = useTransform(scrollYProgress, [0, 1], [0.94, 1])

  return (
    <div ref={ref} className="relative mx-auto w-full max-w-6xl perspective-[1600px]">
      <div className="absolute inset-x-[8%] -top-8 bottom-16 rounded-full bg-linear-to-tr from-primary/30 via-violet-500/25 to-indigo-500/30 blur-3xl" aria-hidden="true" />
      <motion.div
        style={reduceMotion ? undefined : { rotateX, scale, transformOrigin: 'center top' }}
        // Phần cuối khung mờ dần để nối sang mục tiếp theo, không để lộ mép dưới
        className="relative overflow-hidden rounded-2xl border border-border bg-card shadow-2xl shadow-primary/15 mask-[linear-gradient(to_bottom,black_72%,transparent)] sm:rounded-3xl"
      >
        <div className="flex h-9 items-center gap-1.5 border-b border-border/60 bg-muted/60 px-4 sm:h-11" aria-hidden="true">
          <span className="h-2.5 w-2.5 rounded-full bg-red-400" />
          <span className="h-2.5 w-2.5 rounded-full bg-amber-400" />
          <span className="h-2.5 w-2.5 rounded-full bg-emerald-400" />
        </div>
        <Screenshot theme="light" />
        <Screenshot theme="dark" />
      </motion.div>
    </div>
  )
}
