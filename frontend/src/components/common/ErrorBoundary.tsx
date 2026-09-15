import { Component, type ErrorInfo, type ReactNode } from 'react'
import { Button } from '@/components/ui/button'

interface ErrorBoundaryProps {
  children: ReactNode
}

interface ErrorBoundaryState {
  hasError: boolean
  error: Error | null
}

/**
 * ErrorBoundary — catches runtime errors and shows a fallback UI
 * instead of crashing the entire application.
 */
export class ErrorBoundary extends Component<ErrorBoundaryProps, ErrorBoundaryState> {
  constructor(props: ErrorBoundaryProps) {
    super(props)
    this.state = { hasError: false, error: null }
  }

  static getDerivedStateFromError(error: Error): ErrorBoundaryState {
    return { hasError: true, error }
  }

  componentDidCatch(error: Error, errorInfo: ErrorInfo) {
    if (import.meta.env.DEV) {
      console.error('[ErrorBoundary]', error, errorInfo)
    }
  }

  render() {
    if (this.state.hasError) {
      return (
        <div className="min-h-screen flex flex-col items-center justify-center bg-background text-foreground p-6">
          <div className="max-w-md text-center space-y-4">
            <div className="w-16 h-16 mx-auto rounded-2xl bg-destructive/10 flex items-center justify-center">
              <span className="text-3xl">⚠️</span>
            </div>
            <h1 className="text-2xl font-bold">Đã xảy ra lỗi</h1>
            <p className="text-sm text-muted-foreground">
              Ứng dụng gặp sự cố không mong muốn. Vui lòng thử tải lại trang.
            </p>
            {this.state.error && (
              <p className="text-xs text-destructive bg-destructive/5 p-3 rounded-xl font-mono">
                {this.state.error.message}
              </p>
            )}
            <Button
              onClick={() => window.location.reload()}
              className="mt-4"
            >
              Tải lại trang
            </Button>
          </div>
        </div>
      )
    }

    return this.props.children
  }
}
