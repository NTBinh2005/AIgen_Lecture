/**
 * Đọc API danh sách có phân trang của backend (Spring `Page`): `?page=0&size=20&sort=...`,
 * trả `{ content, totalElements, totalPages, number, size, last }`.
 * Chấp nhận cả mảng thuần để vẫn chạy được với backend cũ chưa phân trang.
 */
import axiosInstance from './axiosInstance'

export interface Page<T> {
  content: T[]
  totalElements: number
  totalPages: number
  /** Trang hiện tại, tính từ 0 */
  number: number
  size: number
  first?: boolean
  last?: boolean
}

export type PageParams = Record<string, string | number | boolean | undefined | null>

function toPage<T>(data: Page<T> | T[]): Page<T> {
  if (Array.isArray(data)) {
    return { content: data, totalElements: data.length, totalPages: 1, number: 0, size: data.length, first: true, last: true }
  }
  return data
}

/** Một trang. */
export async function fetchPage<T>(url: string, params: PageParams = {}): Promise<Page<T>> {
  const res = await axiosInstance.get<Page<T> | T[]>(url, { params })
  return toPage(res.data)
}

/**
 * Gộp mọi trang thành một mảng — chỉ dùng cho màn hình cần toàn bộ dữ liệu (thống kê, đếm).
 * `maxPages` chặn trường hợp dữ liệu quá lớn (mặc định 20 trang × 100 = 2.000 bản ghi).
 */
export async function fetchAllPages<T>(url: string, params: PageParams = {}, maxPages = 20): Promise<T[]> {
  const size = 100 // backend giới hạn tối đa 100 / trang
  const first = await fetchPage<T>(url, { ...params, page: 0, size })
  const items = [...first.content]
  const pages = Math.min(first.totalPages, maxPages)
  for (let page = 1; page < pages; page++) {
    const next = await fetchPage<T>(url, { ...params, page, size })
    items.push(...next.content)
  }
  return items
}

/**
 * Như `fetchAllPages` nhưng giữ dạng `Page` (cho các màn hình đang đọc `content` / `totalElements`).
 * `totalElements` là tổng thật của backend, kể cả khi bị `maxPages` cắt bớt.
 */
export async function fetchAllAsPage<T>(url: string, params: PageParams = {}, maxPages = 20): Promise<Page<T>> {
  const size = 100
  const first = await fetchPage<T>(url, { ...params, page: 0, size })
  const content = [...first.content]
  const pages = Math.min(first.totalPages, maxPages)
  for (let page = 1; page < pages; page++) {
    content.push(...(await fetchPage<T>(url, { ...params, page, size })).content)
  }
  return { ...first, content, totalPages: 1, number: 0, size: content.length, first: true, last: true }
}
