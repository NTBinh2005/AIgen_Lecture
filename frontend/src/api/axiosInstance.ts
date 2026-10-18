import axios from 'axios'
import type { AxiosError, InternalAxiosRequestConfig } from 'axios'
import { API_BASE_URL, useAuthStore } from '@/store/authStore'

const axiosInstance = axios.create({
  baseURL: API_BASE_URL,
  timeout: 15000,
})

// Tự động đính JWT token vào mọi request — đọc từ Zustand store (không dùng localStorage trực tiếp)
axiosInstance.interceptors.request.use((config) => {
  const token = useAuthStore.getState().token
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

/** Một lần refresh dùng chung cho mọi request bị 401 cùng lúc (refresh token chỉ dùng được 1 lần). */
let refreshing: Promise<string | null> | null = null

async function refreshAccessToken(): Promise<string | null> {
  const { refreshToken, setTokens } = useAuthStore.getState()
  if (!refreshToken) return null
  try {
    const res = await fetch(`${API_BASE_URL}/auth/refresh`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ refreshToken }),
    })
    if (!res.ok) return null
    const data = (await res.json()) as { token: string; refreshToken?: string }
    setTokens(data.token, data.refreshToken ?? null)
    return data.token
  } catch {
    return null
  }
}

function forceLogout() {
  useAuthStore.getState().logout()
  window.location.href = '/?login=true'
}

// Token hết hạn → thử lấy token mới bằng refresh token rồi gửi lại request; không được thì đăng xuất
axiosInstance.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    const original = error.config as (InternalAxiosRequestConfig & { _retried?: boolean }) | undefined
    if (error.response?.status !== 401 || !original) return Promise.reject(error)
    // 401 của đăng nhập / đăng ký (sai mật khẩu…) hoặc request chưa gắn token không phải "phiên hết hạn":
    // trả lỗi cho form hiển thị, không đăng xuất / tải lại trang.
    if (original.url?.startsWith('/auth/') || !original.headers?.Authorization) return Promise.reject(error)

    if (original._retried || !useAuthStore.getState().refreshToken) {
      forceLogout()
      return Promise.reject(error)
    }

    original._retried = true
    refreshing ??= refreshAccessToken().finally(() => { refreshing = null })
    const newToken = await refreshing
    if (!newToken) {
      forceLogout()
      return Promise.reject(error)
    }
    original.headers.Authorization = `Bearer ${newToken}`
    return axiosInstance(original)
  }
)

export default axiosInstance
