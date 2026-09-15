import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { ThemeProvider } from 'next-themes'
import { ErrorBoundary } from '@/components/common/ErrorBoundary'
import AppRouter from '@/router/index'
import './index.css'

const queryClient = new QueryClient()

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <ThemeProvider attribute="class" defaultTheme="system" enableSystem>
        <ErrorBoundary>
          <AppRouter />
        </ErrorBoundary>
      </ThemeProvider>
    </QueryClientProvider>
  </StrictMode>
)