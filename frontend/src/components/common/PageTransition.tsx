import { useLayoutEffect, useRef } from 'react'
import type { ReactNode } from 'react'
import { useLocation } from 'react-router-dom'

/**
 * Hiệu ứng mờ dần khi đổi trang.
 * - Không đổi key theo URL: QuizEditorPage dùng chung cho /new và /:id, remount sẽ mất dữ liệu đang nhập.
 * - Chỉ animate opacity: transform trên khung trang sẽ làm lệch các thanh fixed/sticky bên trong.
 */
export function PageTransition({ children }: { children: ReactNode }) {
  const { pathname } = useLocation()
  const ref = useRef<HTMLDivElement>(null)

  useLayoutEffect(() => {
    ref.current?.animate([{ opacity: 0 }, { opacity: 1 }], { duration: 240, easing: 'cubic-bezier(0.22, 1, 0.36, 1)' })
  }, [pathname])

  return <div ref={ref}>{children}</div>
}
