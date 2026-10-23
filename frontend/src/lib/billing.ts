/** Nhãn trạng thái giao dịch / hoàn tiền — dùng chung cho trang Thanh toán của học sinh và của admin. */
import type { BillingCycle, PaymentStatus, RefundStatus, SubscriptionStatus } from '@/types/billing'

export const PAYMENT_STATUS: Record<PaymentStatus, { label: string; tone: 'amber' | 'emerald' | 'red' | 'muted' | 'violet' }> = {
  PENDING: { label: 'Chờ thanh toán', tone: 'amber' },
  SUCCESS: { label: 'Thành công', tone: 'emerald' },
  FAILED: { label: 'Thất bại', tone: 'red' },
  CANCELLED: { label: 'Đã huỷ', tone: 'muted' },
  REFUNDED: { label: 'Đã hoàn tiền', tone: 'violet' },
}

export const REFUND_STATUS: Record<RefundStatus, { label: string; tone: 'amber' | 'emerald' | 'red' | 'blue' }> = {
  REQUESTED: { label: 'Chờ duyệt', tone: 'amber' },
  PROCESSING: { label: 'Đang xử lý', tone: 'blue' },
  APPROVED: { label: 'Đã duyệt', tone: 'emerald' },
  COMPLETED: { label: 'Đã hoàn tiền', tone: 'emerald' },
  REJECTED: { label: 'Bị từ chối', tone: 'red' },
  FAILED: { label: 'Thất bại', tone: 'red' },
}

export const BILLING_CYCLE_LABEL: Record<BillingCycle, string> = {
  MONTHLY: 'Theo tháng',
  YEARLY: 'Theo năm',
  ONE_TIME: 'Một lần',
}

export const SUBSCRIPTION_STATUS: Record<SubscriptionStatus, { label: string; tone: 'emerald' | 'muted' | 'red' }> = {
  ACTIVE: { label: 'Đang dùng', tone: 'emerald' },
  CANCELLED: { label: 'Đã huỷ', tone: 'red' },
  EXPIRED: { label: 'Hết hạn', tone: 'muted' },
}

/** VD "150.000 ₫ / tháng" — giá kèm chu kỳ cho thẻ gói. */
export function planPeriod(cycle: BillingCycle, durationDays: number | null): string {
  if (cycle === 'MONTHLY') return '/ tháng'
  if (cycle === 'YEARLY') return '/ năm'
  return durationDays ? `/ ${durationDays} ngày` : 'trọn đời'
}
