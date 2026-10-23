/**
 * Nhận thông báo realtime qua SSE (GET /notifications/stream).
 * EventSource không gửi được header Authorization nên đọc stream bằng fetch.
 * Server đóng kết nối sau ~120 giây → tự kết nối lại; lỗi mạng thì chờ lâu dần (tối đa 60 giây).
 */
import { useEffect } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { API_BASE_URL, useAuthStore } from '@/store/authStore'
import { notify } from '@/lib/notify'
import type { AppNotification } from '@/types/student'

export function useNotificationStream() {
  const token = useAuthStore(s => s.token)
  const queryClient = useQueryClient()

  useEffect(() => {
    if (!token) return
    const controller = new AbortController()
    let retryMs = 2000
    let timer: ReturnType<typeof setTimeout> | undefined

    const handleEvent = (event: string, data: string) => {
      if (event !== 'notification') return
      queryClient.invalidateQueries({ queryKey: ['notifications'] })
      try {
        const n = JSON.parse(data) as AppNotification
        if (n.title) notify.info(n.title, n.message)
      } catch { /* dữ liệu không phải JSON: chỉ làm mới danh sách */ }
    }

    const connect = async () => {
      try {
        const res = await fetch(`${API_BASE_URL}/notifications/stream`, {
          headers: { Authorization: `Bearer ${token}`, Accept: 'text/event-stream' },
          signal: controller.signal,
        })
        // 401: token hết hạn — để axios làm mới token; khi token đổi, effect chạy lại với token mới
        if (res.status === 401 || res.status === 403) return
        if (!res.ok || !res.body) throw new Error(`SSE ${res.status}`)
        retryMs = 2000
        const reader = res.body.getReader()
        const decoder = new TextDecoder()
        let buffer = ''
        for (;;) {
          const { value, done } = await reader.read()
          if (done) break
          buffer += decoder.decode(value, { stream: true })
          // Mỗi sự kiện SSE kết thúc bằng một dòng trống
          let sep: number
          while ((sep = buffer.search(/\r?\n\r?\n/)) >= 0) {
            const block = buffer.slice(0, sep)
            buffer = buffer.slice(sep).replace(/^\r?\n\r?\n/, '')
            let event = 'message'
            const data: string[] = []
            for (const line of block.split(/\r?\n/)) {
              if (line.startsWith('event:')) event = line.slice(6).trim()
              else if (line.startsWith('data:')) data.push(line.slice(5).trimStart())
            }
            handleEvent(event, data.join('\n'))
          }
        }
        // Server đóng kết nối bình thường (hết 120 giây) → kết nối lại ngay
        if (!controller.signal.aborted) timer = setTimeout(connect, 500)
      } catch {
        if (controller.signal.aborted) return
        timer = setTimeout(connect, retryMs)
        retryMs = Math.min(retryMs * 2, 60_000)
      }
    }

    connect()
    return () => {
      controller.abort()
      if (timer) clearTimeout(timer)
    }
  }, [token, queryClient])
}
