import { create } from 'zustand'
import { createJSONStorage, persist } from 'zustand/middleware'
import type { StateStorage } from 'zustand/middleware'

/**
 * Lưu phiên đăng nhập theo từng tab.
 * - sessionStorage: phiên riêng của tab, còn nguyên khi F5 → mở song song tab giáo viên và tab học sinh
 *   không ghi đè lẫn nhau (trước đây dùng chung localStorage nên F5 ở tab này lại ra phiên của tab kia).
 * - localStorage: bản sao của lần đăng nhập gần nhất, chỉ dùng để tab mới mở không phải đăng nhập lại.
 */
const tabScopedStorage: StateStorage = {
  getItem: name => {
    const own = sessionStorage.getItem(name)
    if (own !== null) return own
    // Tab mới: nhận phiên gần nhất và giữ bản riêng ngay, để lần đăng nhập ở tab khác không ảnh hưởng
    const latest = localStorage.getItem(name)
    if (latest !== null) sessionStorage.setItem(name, latest)
    return latest
  },
  setItem: (name, value) => {
    sessionStorage.setItem(name, value)
    localStorage.setItem(name, value)
  },
  removeItem: name => {
    sessionStorage.removeItem(name)
    localStorage.removeItem(name)
  },
}

// Shared role type — import this wherever roles are needed
export type Role = 'TEACHER' | 'STUDENT' | 'ADMIN'

export interface User {
  id: string
  name: string
  email: string
  role: Role
}

interface AuthState {
  user: User | null
  token: string | null
  /** Dùng để lấy access token mới (POST /auth/refresh) và thu hồi khi đăng xuất */
  refreshToken: string | null
  isAuthenticated: boolean
  setAuth: (user: User, token: string, refreshToken?: string | null) => void
  /** Cập nhật token sau khi refresh, giữ nguyên user */
  setTokens: (token: string, refreshToken: string | null) => void
  logout: () => void
}

// Local Spring Boot runs on 8080. Docker overrides this at build time with
// VITE_API_BASE_URL=http://localhost:8081/api because Docker publishes 8081.
export const API_BASE_URL: string = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api'

export const useAuthStore = create<AuthState>()(
  persist(
    (set, get) => ({
      user: null,
      token: null,
      refreshToken: null,
      isAuthenticated: false,

      setAuth: (user, token, refreshToken = null) => {
        set({ user, token, refreshToken, isAuthenticated: true })
      },

      setTokens: (token, refreshToken) => {
        set({ token, refreshToken })
      },

      logout: () => {
        // Thu hồi refresh token phía server (không chờ kết quả, không chặn việc đăng xuất).
        // Dùng fetch thay vì axiosInstance để tránh import vòng với interceptor.
        const refreshToken = get().refreshToken
        if (refreshToken) {
          fetch(`${API_BASE_URL}/auth/logout`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ refreshToken }),
            keepalive: true,
          }).catch(() => { /* đăng xuất cục bộ vẫn thành công */ })
        }
        set({ user: null, token: null, refreshToken: null, isAuthenticated: false })
      },
    }),
    { name: 'auth-storage', storage: createJSONStorage(() => tabScopedStorage) }
  )
)
