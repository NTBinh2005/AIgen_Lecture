import type { ReactNode } from 'react'
import { AnimatePresence, motion } from 'framer-motion'

interface MobileDrawerProps {
  open: boolean
  onClose: () => void
  children: ReactNode
}

/** Menu trượt trên mobile, có cả hiệu ứng khi đóng (kể cả khi tự đóng do đổi trang). */
export function MobileDrawer({ open, onClose, children }: MobileDrawerProps) {
  return (
    <AnimatePresence>
      {open && (
        <div key="drawer" className="lg:hidden fixed inset-0 z-50" role="dialog" aria-modal="true" aria-label="Menu">
          <motion.div
            className="absolute inset-0 bg-black/40 backdrop-blur-sm"
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            transition={{ duration: 0.2 }}
            onClick={onClose}
          />
          <motion.aside
            className="absolute left-0 top-0 h-full w-72 max-w-[85vw] flex flex-col bg-background border-r border-border/50 shadow-2xl"
            initial={{ x: '-100%' }}
            animate={{ x: 0 }}
            exit={{ x: '-100%' }}
            transition={{ type: 'spring', stiffness: 420, damping: 40 }}
          >
            {children}
          </motion.aside>
        </div>
      )}
    </AnimatePresence>
  )
}
