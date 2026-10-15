/**
 * API thanh toán, hoá đơn, hoàn tiền và quyền học của người dùng đang đăng nhập.
 */
import axiosInstance from './axiosInstance'
import { asList, LIST_PARAMS } from './asList'
import type { Invoice } from '@/types/admin'
import type { LearningAccess, Payment, PaymentCreatePayload, Refund, Subscription, SubscriptionPlan } from '@/types/billing'

/** Tạo giao dịch PENDING và nhận link cổng thanh toán (paymentUrl). */
export async function createPayment(payload: PaymentCreatePayload): Promise<Payment> {
  const res = await axiosInstance.post<Payment>('/payments', payload)
  return res.data
}

export async function getMyPayments(): Promise<Payment[]> {
  const res = await axiosInstance.get<Payment[]>('/payments/my-payments')
  return res.data
}

export async function getPayment(paymentId: number): Promise<Payment> {
  const res = await axiosInstance.get<Payment>(`/payments/${paymentId}`)
  return res.data
}

/** Chỉ huỷ được giao dịch đang PENDING. */
export async function cancelPayment(paymentId: number): Promise<Payment> {
  const res = await axiosInstance.post<Payment>(`/payments/${paymentId}/cancel`)
  return res.data
}

export async function getMyInvoices(): Promise<Invoice[]> {
  const res = await axiosInstance.get('/invoices', { params: LIST_PARAMS })
  return asList<Invoice>(res.data)
}

/** Chỉ hoàn được giao dịch SUCCESS, mỗi giao dịch một yêu cầu. */
export async function requestRefund(paymentId: number, reason: string): Promise<Refund> {
  const res = await axiosInstance.post<Refund>('/refunds', { paymentId, reason })
  return res.data
}

export async function getMyRefunds(): Promise<Refund[]> {
  const res = await axiosInstance.get<Refund[]>('/refunds/my-refunds')
  return res.data
}

export async function getMyAccess(): Promise<LearningAccess[]> {
  const res = await axiosInstance.get<LearningAccess[]>('/access/my-access')
  return res.data
}

export async function checkAccess(productId: number): Promise<boolean> {
  const res = await axiosInstance.get<{ hasAccess: boolean }>('/access/check', { params: { productId } })
  return res.data.hasAccess
}

/** Các gói đang bán cho một vai trò (học sinh / giáo viên). */
export async function getPlans(role: 'STUDENT' | 'TEACHER'): Promise<SubscriptionPlan[]> {
  const res = await axiosInstance.get<SubscriptionPlan[]>('/plans', { params: { role } })
  return res.data
}

export async function getMySubscriptions(): Promise<Subscription[]> {
  const res = await axiosInstance.get<Subscription[]>('/subscriptions/me')
  return res.data
}

export async function cancelSubscription(subscriptionId: number): Promise<Subscription> {
  const res = await axiosInstance.post<Subscription>(`/subscriptions/${subscriptionId}/cancel`)
  return res.data
}

export async function resumeSubscription(subscriptionId: number): Promise<Subscription> {
  const res = await axiosInstance.post<Subscription>(`/subscriptions/${subscriptionId}/resume`)
  return res.data
}
