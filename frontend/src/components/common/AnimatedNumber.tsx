import { useEffect } from 'react'
import { animate, motion, useMotionValue, useReducedMotion, useTransform } from 'framer-motion'

/** Số liệu đếm dần tới giá trị thật; giá trị chữ (VD '—' khi đang tải) được hiển thị nguyên trạng. */
export function AnimatedNumber({ value }: { value: number | string }) {
  const reduceMotion = useReducedMotion()
  const count = useMotionValue(0)
  const text = useTransform(count, v => Math.round(v).toLocaleString('vi-VN'))

  useEffect(() => {
    if (typeof value !== 'number') return
    if (reduceMotion) {
      count.set(value)
      return
    }
    const controls = animate(count, value, { duration: 0.8, ease: [0.22, 1, 0.36, 1] })
    return () => controls.stop()
  }, [value, reduceMotion, count])

  if (typeof value !== 'number') return <>{value}</>
  return <motion.span className="tabular-nums">{text}</motion.span>
}
