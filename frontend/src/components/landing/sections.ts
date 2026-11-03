import { CircleHelp, CreditCard, ListChecks, Sparkles, Users } from 'lucide-react'

/** Các phần của trang chủ (id dùng cho liên kết #anchor, icon hiện khi thanh nav ở dạng dọc). */
export const LANDING_SECTIONS = [
  { id: 'tinh-nang', label: 'Tính năng', icon: Sparkles },
  { id: 'cach-hoat-dong', label: 'Cách hoạt động', icon: ListChecks },
  { id: 'danh-cho-ai', label: 'Dành cho ai', icon: Users },
  { id: 'bang-gia', label: 'Bảng giá', icon: CreditCard },
  { id: 'hoi-dap', label: 'Hỏi đáp', icon: CircleHelp },
] as const
