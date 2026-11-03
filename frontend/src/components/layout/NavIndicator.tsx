import { motion } from 'framer-motion'
import { cn } from '@/lib/utils'

interface NavIndicatorProps {
  /** Giữ nguyên giữa các mục để nền trượt từ mục cũ sang mục mới */
  layoutId: string
  className?: string
  barClassName?: string
}

/** Nền và vạch đánh dấu mục menu đang chọn. Thẻ Link cha cần có `relative isolate`. */
export function NavIndicator({ layoutId, className, barClassName }: NavIndicatorProps) {
  return (
    <motion.div
      layoutId={layoutId}
      className={cn('absolute inset-0 -z-10 rounded-xl', className)}
      transition={{ type: 'spring', stiffness: 500, damping: 38 }}
      aria-hidden="true"
    >
      <div className={cn('absolute left-0 top-1/2 h-5 w-1 -translate-y-1/2 rounded-r-full', barClassName)} />
    </motion.div>
  )
}
