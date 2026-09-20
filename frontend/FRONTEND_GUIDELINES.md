# Frontend Guidelines — aigen-lecture

> Tài liệu này là luật của team frontend. Mọi thành viên đều phải đọc và tuân thủ trước khi bắt đầu code.

---

## 1. Nguyên tắc vàng

- **Đọc trước, code sau** — hiểu rõ task trước khi mở file
- **Một component, một trách nhiệm** — không nhồi quá nhiều logic vào một file
- **Dark mode + Responsive phải làm song song** — không được code xong hết mới quay lại làm, tránh nổ UI
- **Commit nhỏ, commit thường xuyên** — đừng để 3 ngày mới commit một lần

---

## 2. Cấu trúc thư mục

```
src/
├── api/              # axios instance + các hàm gọi API
├── components/
│   ├── ui/           # shadcn/ui (KHÔNG chỉnh sửa trực tiếp)
│   └── layout/       # Navbar, Sidebar, Layout chung
├── pages/
│   ├── auth/         # LoginPage
│   ├── teacher/      # Upload, Generate, Analytics
│   ├── student/      # Lecture, Quiz
│   └── admin/        # User Management
├── store/            # Zustand stores
├── types/            # TypeScript interfaces & types
├── hooks/            # Custom hooks
└── router/           # Route definitions + ProtectedRoute
```

**Quy tắc:**
- `pages/` chỉ chứa page-level component, không chứa logic phức tạp
- Logic tái sử dụng → đưa vào `hooks/`
- Gọi API → đưa vào `api/`, không gọi trực tiếp trong component
- Không tạo file linh tinh ngoài cấu trúc trên nếu chưa thống nhất với team

---

## 3. Quy tắc đặt tên

| Loại | Convention | Ví dụ |
|---|---|---|
| Component | PascalCase | `LoginPage.tsx`, `UserCard.tsx` |
| Hook | camelCase + `use` | `useAuth.ts`, `useLecture.ts` |
| Store | camelCase + `Store` | `authStore.ts` |
| Type/Interface | PascalCase | `User`, `Lecture`, `QuizQuestion` |
| File API | camelCase | `authApi.ts`, `lectureApi.ts` |
| Biến, hàm | camelCase | `isLoading`, `handleSubmit` |
| Hằng số | UPPER_SNAKE_CASE | `MAX_FILE_SIZE`, `API_TIMEOUT` |

---

## 4. TypeScript

- **Không dùng `any`** — nếu không biết type thì dùng `unknown` rồi narrow dần
- Khai báo interface trong `src/types/` nếu dùng ở nhiều nơi
- Props của component phải có type rõ ràng

```tsx
// ❌ Sai
const UserCard = ({ user }: any) => { ... }

// ✅ Đúng
interface UserCardProps {
  user: User
  onClick?: () => void
}
const UserCard = ({ user, onClick }: UserCardProps) => { ... }
```

---

## 5. Dark Mode — BẮT BUỘC LÀM SONG SONG

> Mỗi khi code UI, phải test dark mode ngay lúc đó, không để sau.

**Cách dùng đúng với Tailwind:**

```tsx
// ❌ Sai — chỉ có light mode
<div className="bg-white text-black">

// ✅ Đúng — có cả dark mode
<div className="bg-white text-black dark:bg-gray-900 dark:text-white">
```

**Checklist dark mode cho mỗi component:**
- [ ] Background có `dark:` chưa?
- [ ] Text color có `dark:` chưa?
- [ ] Border, input, card có `dark:` chưa?
- [ ] Icon, placeholder có đủ contrast chưa?

**Dùng CSS variables của shadcn/ui thay vì hardcode màu:**

```tsx
// ❌ Tránh hardcode màu
<div className="bg-white dark:bg-gray-900">

// ✅ Dùng semantic colors của shadcn — tự handle dark mode
<div className="bg-background text-foreground">
```

shadcn/ui đã định nghĩa sẵn: `background`, `foreground`, `card`, `muted`, `primary`, `destructive`... Dùng những cái này là dark mode tự chạy.

---

## 6. Responsive — BẮT BUỘC LÀM SONG SONG

> Mỗi khi code UI, phải test responsive ngay lúc đó trên DevTools.

**Breakpoints Tailwind:**

| Prefix | Min-width | Thiết bị |
|---|---|---|
| (none) | 0px | Mobile — **code mobile trước** |
| `sm:` | 640px | Tablet nhỏ |
| `md:` | 768px | Tablet |
| `lg:` | 1024px | Laptop |
| `xl:` | 1280px | Desktop |

**Mobile-first — luôn code mobile trước, rồi mới mở rộng ra:**

```tsx
// ❌ Sai — desktop-first
<div className="flex-row md:flex-col">

// ✅ Đúng — mobile-first
<div className="flex-col md:flex-row">
```

**Checklist responsive cho mỗi component:**
- [ ] Layout có vỡ trên màn hình 375px chưa?
- [ ] Font size có quá nhỏ/to trên mobile chưa?
- [ ] Button, input có đủ touch target (min 44px) chưa?
- [ ] Table, chart có scroll ngang trên mobile chưa?

---

## 7. Component

**Giữ component nhỏ và tập trung:**

```tsx
// ❌ Sai — component quá lớn
export default function TeacherDashboard() {
  // 300 dòng code với đủ thứ logic...
}

// ✅ Đúng — chia nhỏ
export default function TeacherDashboard() {
  return (
    <DashboardLayout>
      <LectureList />
      <UploadButton />
    </DashboardLayout>
  )
}
```

**Thứ tự trong file component:**

```tsx
// 1. Imports
// 2. Types/Interfaces
// 3. Component function
// 4. Sub-components (nếu nhỏ và chỉ dùng ở đây)
// 5. Export
```

---

## 8. Gọi API

- **Không gọi axios trực tiếp trong component**
- Tạo file trong `src/api/` cho từng domain

```ts
// src/api/authApi.ts
import axiosInstance from './axiosInstance'
import { LoginRequest, LoginResponse } from '@/types'

export const login = (data: LoginRequest) =>
  axiosInstance.post<LoginResponse>('/auth/login', data)

export const logout = () =>
  axiosInstance.post('/auth/logout')
```

```tsx
// Trong component — dùng TanStack Query
import { useMutation } from '@tanstack/react-query'
import { login } from '@/api/authApi'

const { mutate, isPending } = useMutation({ mutationFn: login })
```

---

## 9. Git Workflow

**Branch naming:**
```
feature/ten-tinh-nang
fix/ten-loi
chore/ten-viec
```

Ví dụ: `feature/login-page`, `fix/dark-mode-navbar`, `chore/update-deps`

**Commit message (tiếng Anh, theo Conventional Commits):**
```
feat: add login page
fix: fix dark mode on navbar
chore: update dependencies
refactor: extract UserCard component
style: fix responsive on mobile dashboard
```

**Quy trình:**
1. Tạo branch mới từ `main`
2. Code + test
3. Tạo Pull Request → assign 1 người review
4. Review xong mới merge vào `main`
5. Không được push thẳng lên `main`

---

## 10. Checklist trước khi tạo Pull Request

- [ ] Không có lỗi TypeScript (`npm run build` không báo lỗi)
- [ ] Dark mode hoạt động đúng
- [ ] Responsive không vỡ ở 375px, 768px, 1280px
- [ ] Không có `console.log` thừa
- [ ] Không có code comment thừa kiểu `// test`, `// xóa sau`
- [ ] Đặt tên biến, hàm, file rõ ràng
- [ ] Không commit file `.env`

---

> Có thắc mắc về guidelines → tạo issue trên GitHub hoặc hỏi trực tiếp trong nhóm, không tự ý thay đổi quy ước mà không thông báo.
