import { useMemo, useState } from 'react'

/**
 * Phân trang phía client cho danh sách đã tải đủ (lọc / đếm trên toàn bộ dữ liệu).
 * `resetKey` đổi (VD chuỗi bộ lọc) → tự về trang đầu; trang vượt quá (do xoá bớt dữ liệu) → tự lùi về trang cuối.
 */
export function usePagination<T>(items: readonly T[], pageSize = 15, resetKey: unknown = null) {
  const [state, setState] = useState<{ key: unknown; page: number }>({ key: resetKey, page: 0 })
  const totalPages = Math.max(1, Math.ceil(items.length / pageSize))
  const requested = Object.is(state.key, resetKey) ? state.page : 0
  const page = Math.min(requested, totalPages - 1)
  const pageItems = useMemo(() => items.slice(page * pageSize, (page + 1) * pageSize), [items, page, pageSize])
  return {
    page,
    totalPages,
    pageItems,
    total: items.length,
    pageSize,
    setPage: (next: number) => setState({ key: resetKey, page: next }),
  }
}
