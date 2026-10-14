import { useTheme } from 'next-themes'
import { Toaster } from 'sonner'

/** Gắn một lần ở gốc ứng dụng: nơi hiển thị các toast của notify.* */
export function AppToaster() {
  const { resolvedTheme } = useTheme()
  return (
    <Toaster
      theme={resolvedTheme === 'dark' ? 'dark' : 'light'}
      position="top-right"
      // Nằm dưới thanh header (cao 64px) để không che chuông thông báo và avatar
      offset={76}
      richColors
      closeButton
      duration={4000}
      toastOptions={{ className: 'rounded-xl font-sans' }}
    />
  )
}
