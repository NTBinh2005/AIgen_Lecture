/**
 * Thông báo và hộp thoại xác nhận dùng chung, thay cho alert / confirm / prompt của trình duyệt.
 * - notify.success / error / info / warning: toast ở góc màn hình.
 * - confirmDialog(...): hộp thoại xác nhận, trả về Promise<boolean>.
 * - promptDialog(...): hộp thoại nhập một dòng, trả về chuỗi đã nhập hoặc null nếu huỷ.
 * Cần <AppToaster /> và <ConfirmHost /> được gắn một lần ở gốc ứng dụng (main.tsx).
 */
import { toast } from 'sonner'
import { create } from 'zustand'
import { getErrorMessage } from '@/lib/format'

export const notify = {
  success: (message: string, description?: string) => toast.success(message, { description }),
  error: (message: string, description?: string) => toast.error(message, { description }),
  info: (message: string, description?: string) => toast.info(message, { description }),
  warning: (message: string, description?: string) => toast.warning(message, { description }),
  /** Hiện lỗi từ API (lấy `message` backend trả về, không có thì dùng câu dự phòng). */
  apiError: (error: unknown, fallback: string) => toast.error(getErrorMessage(error, fallback)),
}

export interface ConfirmOptions {
  title: string
  description?: string
  confirmText?: string
  cancelText?: string
  /** Thao tác xoá / không hoàn tác được → nút xác nhận màu đỏ */
  destructive?: boolean
}

export interface PromptOptions extends ConfirmOptions {
  label?: string
  placeholder?: string
  defaultValue?: string
  /** Bắt buộc nhập mới bấm được nút xác nhận */
  required?: boolean
}

export type DialogRequest =
  | ({ kind: 'confirm'; resolve: (ok: boolean) => void } & ConfirmOptions)
  | ({ kind: 'prompt'; resolve: (value: string | null) => void } & PromptOptions)

interface DialogState {
  request: DialogRequest | null
  open: (request: DialogRequest) => void
  close: () => void
}

export const useDialogStore = create<DialogState>(set => ({
  request: null,
  open: request => set({ request }),
  close: () => set({ request: null }),
}))

export function confirmDialog(options: ConfirmOptions | string): Promise<boolean> {
  const opts = typeof options === 'string' ? { title: options } : options
  return new Promise(resolve => useDialogStore.getState().open({ kind: 'confirm', resolve, ...opts }))
}

export function promptDialog(options: PromptOptions): Promise<string | null> {
  return new Promise(resolve => useDialogStore.getState().open({ kind: 'prompt', resolve, ...options }))
}

/** Tách câu hỏi dạng "Đóng lớp? Lớp sẽ không nhận thêm học sinh." thành tiêu đề + mô tả cho confirmDialog. */
export function splitQuestion(message: string): ConfirmOptions {
  const i = message.indexOf('?')
  const rest = i < 0 ? '' : message.slice(i + 1).trim()
  return rest ? { title: message.slice(0, i + 1), description: rest } : { title: message }
}
