import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { ThemeProvider } from 'next-themes'
import { AppToaster } from '@/components/common/AppToaster'
import { ConfirmHost } from '@/components/common/ConfirmHost'
import { ErrorBoundary } from '@/components/common/ErrorBoundary'
import AppRouter from '@/router/index'
import { getErrorStatus } from '@/lib/format'
import './index.css'

// Lỗi 4xx (không tìm thấy, không có quyền) thử lại cũng vô ích → báo lỗi ngay thay vì chờ 3 lần thử lại (~7 giây)
const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: (failureCount, error) => {
        const status = getErrorStatus(error)
        if (status !== undefined && status >= 400 && status < 500) return false
        return failureCount < 2
      },
    },
  },
})

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <ThemeProvider attribute="class" defaultTheme="system" enableSystem>
        <ErrorBoundary>
          <AppRouter />
        </ErrorBoundary>
        <ConfirmHost />
        <AppToaster />
      </ThemeProvider>
    </QueryClientProvider>
  </StrictMode>
)