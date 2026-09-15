# FRONTEND GUIDELINES — AIGEN-LECTURE

> File này dành cho AI (Cursor, Copilot, v.v.) đọc để generate code nhất quán.
> Stack: React 18 + TypeScript + Vite + Tailwind CSS + shadcn/ui + TanStack React Query + Axios + next-themes (dark mode)

---

## 1. Cấu trúc thư mục

```
src/
├── api/              # axiosInstance + từng file API theo resource
│   ├── axiosInstance.ts
│   ├── authApi.ts
│   ├── lectureApi.ts
│   ├── documentApi.ts
│   └── analyticsApi.ts
├── assets/           # ảnh, icon tĩnh
├── components/       # shared components dùng lại nhiều nơi
│   ├── ui/           # shadcn/ui components (KHÔNG sửa trực tiếp)
│   └── common/       # custom shared components: PageHeader, LoadingSpinner, EmptyState...
├── hooks/            # custom hooks (useAuth, useLecture, v.v.)
├── lib/              # utils, helpers (cn(), formatDate(), v.v.)
├── pages/
│   ├── admin/        # các trang của Admin
│   ├── auth/         # Login, Register
│   ├── student/      # các trang của Student
│   └── teacher/      # các trang của Teacher
├── router/           # React Router config
├── store/            # Zustand stores
├── types/            # TypeScript interfaces & types toàn cục
├── App.tsx
├── App.css
├── main.tsx
└── index.css
```

**Quy tắc:**
- Mỗi page có folder riêng: `pages/teacher/LecturePage/index.tsx`
- Component dùng ≥ 2 nơi → đưa vào `components/common/`
- Component chỉ dùng trong 1 page → đặt cạnh page đó
- KHÔNG import thẳng từ `pages/` sang `pages/` khác

---

## 2. Design System & Theme

### Dark Mode — next-themes

Dự án dùng `next-themes` kết hợp với Tailwind dark mode (`class` strategy).

**Setup trong `main.tsx`:**

```tsx
import { ThemeProvider } from 'next-themes'

ReactDOM.createRoot(document.getElementById('root')!).render(
  <ThemeProvider attribute="class" defaultTheme="system" enableSystem>
    <App />
  </ThemeProvider>
)
```

**Setup Tailwind (`tailwind.config.ts`):**

```ts
export default {
  darkMode: 'class', // bắt buộc để next-themes hoạt động
  // ...
}
```

**Toggle theme trong component:**

```tsx
import { useTheme } from 'next-themes'
import { Moon, Sun } from 'lucide-react'
import { Button } from '@/components/ui/button'

export function ThemeToggle() {
  const { theme, setTheme } = useTheme()
  return (
    <Button
      variant="ghost"
      size="icon"
      onClick={() => setTheme(theme === 'dark' ? 'light' : 'dark')}
    >
      <Sun className="h-4 w-4 rotate-0 scale-100 transition-all dark:-rotate-90 dark:scale-0" />
      <Moon className="absolute h-4 w-4 rotate-90 scale-0 transition-all dark:rotate-0 dark:scale-100" />
    </Button>
  )
}
```

---

### Màu sắc (Tailwind CSS Variables — Light & Dark)

Định nghĩa đầy đủ cả 2 theme trong `index.css`:

```css
@layer base {
  :root {
    /* Brand */
    --primary: 221 83% 53%;
    --primary-foreground: 0 0% 100%;

    /* Background */
    --background: 0 0% 100%;
    --foreground: 222 47% 11%;

    /* Surface */
    --card: 0 0% 100%;
    --card-foreground: 222 47% 11%;
    --muted: 210 40% 96%;
    --muted-foreground: 215 16% 47%;

    /* Accent */
    --accent: 210 40% 96%;
    --accent-foreground: 222 47% 11%;

    /* Semantic */
    --destructive: 0 84% 60%;
    --success: 142 76% 36%;
    --warning: 38 92% 50%;

    /* Border & Ring */
    --border: 214 32% 91%;
    --ring: 221 83% 53%;
    --radius: 0.5rem;
  }

  .dark {
    /* Brand — giữ nguyên primary, chỉ đổi nền */
    --primary: 217 91% 60%;
    --primary-foreground: 0 0% 100%;

    /* Background */
    --background: 222 47% 8%;          /* nền tối chính */
    --foreground: 210 40% 98%;         /* chữ sáng */

    /* Surface */
    --card: 222 47% 11%;               /* card tối hơn nền 1 bậc */
    --card-foreground: 210 40% 98%;
    --muted: 217 33% 18%;
    --muted-foreground: 215 20% 65%;

    /* Accent */
    --accent: 217 33% 18%;
    --accent-foreground: 210 40% 98%;

    /* Semantic */
    --destructive: 0 63% 55%;
    --success: 142 70% 45%;
    --warning: 38 92% 50%;

    /* Border & Ring */
    --border: 217 33% 20%;
    --ring: 217 91% 60%;
  }
}
```

**Quy tắc màu — QUAN TRỌNG:**
- LUÔN dùng semantic tokens: `bg-background`, `bg-card`, `text-foreground`, `text-muted-foreground`, `border-border`
- KHÔNG hardcode màu cụ thể: `bg-white`, `bg-gray-900`, `text-black` — những màu này không tự đổi theo dark mode
- KHÔNG hardcode hex: `text-[#2563eb]`
- Màu semantic: `text-destructive` cho lỗi, `text-success` (custom) cho thành công

```tsx
// ✅ Đúng — tự đổi theo theme
<div className="bg-background text-foreground border-border">
<p className="text-muted-foreground">

// ❌ Sai — cứng màu, dark mode bị vỡ
<div className="bg-white text-gray-900">
<p className="text-gray-500">
```

### ⚡ Quy tắc bắt buộc: Dark Mode + Responsive phải làm SONG SONG

> **KHÔNG được code xong UI rồi mới adapt dark mode hay responsive — sửa lại rất tốn công và dễ hỏng.**

Mỗi khi viết bất kỳ component hay page nào, phải đảm bảo **cùng lúc**:

**Checklist Dark Mode (mỗi element):**
```tsx
// Hỏi: "Element này có đổi màu đúng khi dark mode không?"
// ✅ bg-background / bg-card / bg-muted        → tự đổi
// ✅ text-foreground / text-muted-foreground   → tự đổi
// ✅ border-border                              → tự đổi
// ❌ bg-white, bg-gray-100, text-gray-600      → CỨNG, phải sửa
// ❌ style={{ background: '#fff' }}            → CỨNG, phải sửa

// Trường hợp cần override dark riêng:
<div className="bg-blue-50 dark:bg-blue-950">
<p className="text-blue-700 dark:text-blue-300">
```

**Checklist Responsive (mỗi layout):**
```tsx
// Mobile-first: viết class mobile trước, rồi thêm md: lg: sau
// ✅ Đúng
<div className="flex flex-col gap-4 md:flex-row md:gap-6">
<div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
<p className="text-sm md:text-base">

// ❌ Sai — chỉ nghĩ cho desktop
<div className="flex flex-row gap-6">
<div className="grid grid-cols-3 gap-4">
```

**Breakpoints chuẩn của project:**
| Breakpoint | Class prefix | Dùng cho |
|------------|-------------|---------|
| < 640px | (default) | Mobile |
| ≥ 640px | `sm:` | Mobile lớn / tablet nhỏ |
| ≥ 768px | `md:` | Tablet |
| ≥ 1024px | `lg:` | Desktop |

**Layout patterns theo màn hình:**
```tsx
// Sidebar layout — ẩn sidebar trên mobile
<div className="flex">
  <aside className="hidden lg:block w-64 shrink-0">
    <Sidebar />
  </aside>
  <main className="flex-1 p-4 lg:p-6">
    {children}
  </main>
</div>

// Card grid — 1 cột mobile, 2 tablet, 3 desktop
<div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">

// Form — full width mobile, giới hạn desktop
<div className="w-full max-w-md mx-auto">

// Padding trang
<div className="p-4 md:p-6 lg:p-8">
```

**Cách test nhanh trong dev:**
- Bật DevTools → Toggle device toolbar (Ctrl+Shift+M)
- Test ở 3 breakpoint: **375px** (mobile), **768px** (tablet), **1280px** (desktop)
- Test dark mode: DevTools → Rendering → "Emulate CSS media feature prefers-color-scheme: dark"
- Hoặc click ThemeToggle ngay trên UI

### Typography

```css
/* index.css */
@import url('https://fonts.googleapis.com/css2?family=Be+Vietnam+Pro:wght@400;500;600;700&display=swap');

body {
  font-family: 'Be Vietnam Pro', sans-serif;
}
```

| Dùng cho | Class Tailwind |
|----------|----------------|
| Tiêu đề trang | `text-2xl font-bold text-foreground` |
| Tiêu đề section | `text-lg font-semibold text-foreground` |
| Body text | `text-sm text-foreground` |
| Mô tả phụ | `text-sm text-muted-foreground` |
| Label form | `text-sm font-medium` |
| Badge / tag | `text-xs font-medium` |

### Spacing

- Padding trang: `p-6` (desktop), `p-4` (mobile)
- Gap giữa các section: `space-y-6`
- Gap trong card: `space-y-4`
- Gap giữa các item nhỏ: `gap-2` hoặc `gap-3`

---

## 3. Component Conventions

### Cách dùng shadcn/ui

```tsx
// ✅ Đúng — import từ components/ui
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Badge } from '@/components/ui/badge'

// ❌ Sai — KHÔNG cài thêm UI library khác (MUI, Antd...)
```

### Button variants

```tsx
// Primary action (1 per page)
<Button>Tạo bài giảng</Button>

// Secondary
<Button variant="outline">Hủy</Button>

// Destructive
<Button variant="destructive">Xóa</Button>

// Loading state — LUÔN disable khi đang xử lý
<Button disabled={isPending}>
  {isPending ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : null}
  Lưu
</Button>
```

### Card layout chuẩn

```tsx
<Card>
  <CardHeader>
    <CardTitle>Tiêu đề</CardTitle>
    <CardDescription>Mô tả ngắn</CardDescription>
  </CardHeader>
  <CardContent>
    {/* nội dung */}
  </CardContent>
</Card>
```

### Empty state chuẩn

```tsx
// components/common/EmptyState.tsx
<div className="flex flex-col items-center justify-center py-12 text-center">
  <IconComponent className="h-12 w-12 text-muted-foreground mb-4" />
  <h3 className="text-lg font-semibold">Chưa có dữ liệu</h3>
  <p className="text-sm text-muted-foreground mt-1">Mô tả hướng dẫn tiếp theo</p>
  <Button className="mt-4">Action chính</Button>
</div>
```

### Loading state chuẩn

```tsx
// Dùng Skeleton của shadcn/ui, KHÔNG dùng spinner cho toàn trang
import { Skeleton } from '@/components/ui/skeleton'

// Ví dụ skeleton card
<Card>
  <CardHeader>
    <Skeleton className="h-5 w-1/2" />
  </CardHeader>
  <CardContent>
    <Skeleton className="h-4 w-full mb-2" />
    <Skeleton className="h-4 w-3/4" />
  </CardContent>
</Card>
```

---

## 4. API Layer

### axiosInstance (src/api/axiosInstance.ts)

```typescript
import axios from 'axios'
import { useAuthStore } from '@/store/authStore'

const axiosInstance = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL,
  timeout: 15000, // 15s — fail fast trước browser timeout, vẫn đáp ứng NFR-04
})

// Tự động gắn JWT token
axiosInstance.interceptors.request.use((config) => {
  const token = useAuthStore.getState().token
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

// Tự redirect về login nếu 401
axiosInstance.interceptors.response.use(
  (res) => res,
  (error) => {
    if (error.response?.status === 401) {
      useAuthStore.getState().logout()
      window.location.href = '/login'
    }
    return Promise.reject(error)
  }
)

export default axiosInstance
```

### Cách tổ chức file API

```typescript
// src/api/lectureApi.ts
import axiosInstance from './axiosInstance'
import type { Lecture, CreateLectureDto } from '@/types/lecture'

export const lectureApi = {
  getAll: () =>
    axiosInstance.get<Lecture[]>('/lectures').then(r => r.data),

  getById: (id: string) =>
    axiosInstance.get<Lecture>(`/lectures/${id}`).then(r => r.data),

  create: (dto: CreateLectureDto) =>
    axiosInstance.post<Lecture>('/lectures', dto).then(r => r.data),

  update: (id: string, dto: Partial<CreateLectureDto>) =>
    axiosInstance.put<Lecture>(`/lectures/${id}`, dto).then(r => r.data),

  delete: (id: string) =>
    axiosInstance.delete(`/lectures/${id}`).then(r => r.data),
}
```

### React Query conventions

```typescript
// Query keys — tập trung 1 chỗ để tránh typo
export const queryKeys = {
  lectures: {
    all: ['lectures'] as const,
    detail: (id: string) => ['lectures', id] as const,
  },
  analytics: {
    byLecture: (id: string) => ['analytics', id] as const,
  },
}

// Trong component
const { data, isLoading, isError } = useQuery({
  queryKey: queryKeys.lectures.all,
  queryFn: lectureApi.getAll,
})

const { mutate, isPending } = useMutation({
  mutationFn: lectureApi.create,
  onSuccess: () => {
    queryClient.invalidateQueries({ queryKey: queryKeys.lectures.all })
    toast.success('Tạo bài giảng thành công!')
  },
  onError: () => {
    toast.error('Có lỗi xảy ra, vui lòng thử lại.')
  },
})
```

---

## 5. TypeScript Conventions

```typescript
// ✅ Dùng interface cho object shapes
interface Lecture {
  id: string
  title: string
  teacherId: string
  createdAt: string
  status: 'ACTIVE' | 'INACTIVE'
}

// ✅ Dùng type cho union, utility types
type UserRole = 'ADMIN' | 'TEACHER' | 'STUDENT'
type CreateLectureDto = Omit<Lecture, 'id' | 'createdAt'>

// ❌ KHÔNG dùng any
const data: any = ... // ❌
const data: unknown = ... // ✅ nếu chưa biết type

// ✅ Đặt tất cả types/interfaces trong src/types/
// src/types/lecture.ts, src/types/user.ts, v.v.
```

---

## 6. Routing & Phân quyền

```typescript
// src/router/index.tsx
// Cấu trúc route theo role:
// /                   → redirect về dashboard theo role
// /login              → trang đăng nhập
// /teacher/*          → chỉ TEACHER truy cập
// /student/*          → chỉ STUDENT truy cập
// /admin/*            → chỉ ADMIN truy cập

// ProtectedRoute component — wrap các route cần auth
<ProtectedRoute allowedRoles={['TEACHER']}>
  <TeacherDashboard />
</ProtectedRoute>
```

---

## 7. State Management (Zustand)

```typescript
// src/store/authStore.ts
import { create } from 'zustand'
import { persist } from 'zustand/middleware'

interface AuthState {
  token: string | null
  user: { id: string; role: UserRole; name: string } | null
  setAuth: (token: string, user: AuthState['user']) => void
  logout: () => void
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set) => ({
      token: null,
      user: null,
      setAuth: (token, user) => set({ token, user }),
      logout: () => set({ token: null, user: null }),
    }),
    { name: 'auth-storage' }
  )
)
```

**Quy tắc Zustand:**
- Server state (data từ API) → dùng **React Query**, KHÔNG lưu vào Zustand
- Client state (auth, UI state, theme) → dùng **Zustand**
- KHÔNG đọc/ghi `localStorage` thủ công trong app logic; dùng `persist` để sync store

---

## 8. Error Handling

```tsx
// Mọi page đều phải xử lý 3 trạng thái: loading / error / success

function LecturePage() {
  const { data, isLoading, isError, error } = useQuery(...)

  if (isLoading) return <LectureSkeleton />

  if (isError) return (
    <div className="flex flex-col items-center py-12">
      <p className="text-destructive text-sm">
        {error instanceof Error ? error.message : 'Có lỗi xảy ra'}
      </p>
      <Button variant="outline" onClick={() => refetch()} className="mt-4">
        Thử lại
      </Button>
    </div>
  )

  return <LectureList data={data} />
}
```

---

## 9. Environment Variables

```bash
# .env
VITE_API_BASE_URL=http://localhost:8080/api/v1

# .env.production
VITE_API_BASE_URL=https://your-api-domain.com/api/v1
```

**Quy tắc:**
- Tất cả env var phải bắt đầu bằng `VITE_` thì Vite mới expose ra client
- KHÔNG commit `.env` — chỉ commit `.env.example`

---

## 10. Do & Don't

| ✅ DO | ❌ DON'T |
|-------|---------|
| Dùng `cn()` từ `lib/utils` để merge class | Dùng template literal để ghép class Tailwind |
| Đặt tên component: `PascalCase` | Đặt tên component: `camelCase` hay `kebab-case` |
| Đặt tên file component: `LectureCard.tsx` | Đặt tên: `lectureCard.tsx` hay `lecture-card.tsx` |
| Đặt tên hook: `useLecture`, `useAuth` | Đặt tên hook không có prefix `use` |
| `const` cho function component | `function` declaration cũng OK, nhưng nhất quán trong 1 file |
| Destructure props | Dùng `props.xxx` trực tiếp |
| `toast.success()` / `toast.error()` cho feedback | `alert()` hoặc `console.log()` để báo lỗi cho user |
| Soft-delete — gọi API PATCH status=INACTIVE | Gọi DELETE trực tiếp khi có interaction data (vi phạm BR-08) |
| Hiển thị loading skeleton khi fetch | Để trang trắng trong khi loading |
| Dùng semantic color tokens (`bg-background`, `text-foreground`) | Hardcode `bg-white`, `text-gray-900` — vỡ dark mode |
| Viết class mobile-first, thêm `md:` `lg:` sau | Chỉ nghĩ cho desktop rồi patch mobile sau |
| Test dark + responsive **ngay khi viết** component | Xong cả feature mới quay lại fix — vỡ hàng loạt |
