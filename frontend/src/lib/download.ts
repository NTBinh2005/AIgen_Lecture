/** Tải file từ API cần đăng nhập (kèm JWT) rồi lưu về máy. */
import axiosInstance from '@/api/axiosInstance'

export async function downloadFromApi(path: string, filename: string): Promise<void> {
  const res = await axiosInstance.get<Blob>(path, { responseType: 'blob', timeout: 120_000 })
  const url = URL.createObjectURL(res.data)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  document.body.appendChild(a)
  a.click()
  a.remove()
  URL.revokeObjectURL(url)
}

export function formatMoney(amount: number): string {
  return new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND', maximumFractionDigits: 0 }).format(amount)
}
