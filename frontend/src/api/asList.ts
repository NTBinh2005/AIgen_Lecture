/**
 * Backend có endpoint trả mảng, có endpoint trả Spring Page ({ content: [...] }).
 * Helper này chuẩn hoá cả hai về mảng để UI luôn gọi được .map/.filter.
 */
export function asList<T>(data: unknown): T[] {
  if (Array.isArray(data)) return data as T[]
  const content = (data as { content?: unknown } | null | undefined)?.content
  return Array.isArray(content) ? (content as T[]) : []
}

/** Lấy tối đa số bản ghi cho phép (spring.data.web.pageable.max-page-size=100). */
export const LIST_PARAMS = { size: 100 } as const
