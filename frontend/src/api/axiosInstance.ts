import axios from 'axios'
import { useAuthStore } from '@/store/authStore'

const axiosInstance = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api',
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

// Tự động logout nếu token hết hạn
axiosInstance.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      useAuthStore.getState().logout()
      window.location.href = '/?login=true'
    }
    return Promise.reject(error)
  }
)

export default axiosInstance