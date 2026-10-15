import { useCallback, useSyncExternalStore } from 'react'

function subscribe(onChange: () => void) {
  window.addEventListener('scroll', onChange, { passive: true })
  window.addEventListener('resize', onChange)
  return () => {
    window.removeEventListener('scroll', onChange)
    window.removeEventListener('resize', onChange)
  }
}

/**
 * Scroll-spy: trả id của phần đang xem — phần cuối cùng có mép trên đã đi qua vạch `offset` (px tính từ đỉnh màn hình).
 * Cuộn chạm đáy trang thì chọn phần cuối (phần ngắn như "Hỏi đáp" có thể không bao giờ chạm vạch). Chưa tới phần nào → null.
 */
export function useActiveSection(ids: readonly string[], offset = 120): string | null {
  const getSnapshot = useCallback(() => {
    const atBottom = window.innerHeight + window.scrollY >= document.documentElement.scrollHeight - 2
    if (atBottom && ids.length > 0 && document.getElementById(ids[ids.length - 1])) return ids[ids.length - 1]
    let active: string | null = null
    for (const id of ids) {
      const el = document.getElementById(id)
      if (el && el.getBoundingClientRect().top <= offset) active = id
    }
    return active
  }, [ids, offset])
  return useSyncExternalStore(subscribe, getSnapshot, () => null)
}
