/** Types thanh toán / hoá đơn / hoàn tiền / quyền học — khớp DTO backend (đã gọi thử). */

export type PaymentMethod = 'VNPAY' | 'MOMO'
export type PaymentStatus = 'PENDING' | 'SUCCESS' | 'FAILED' | 'CANCELLED' | 'REFUNDED'
export type RefundStatus = 'REQUESTED' | 'PROCESSING' | 'APPROVED' | 'REJECTED' | 'COMPLETED' | 'FAILED'

export interface Payment {
  paymentId: number
  transactionId: string
  userId: number
  /** Backend chưa có danh mục sản phẩm — đây chỉ là một mã số (FIX.md #38) */
  productId: number
  planId: number | null
  planName: string | null
  amount: number
  status: PaymentStatus
  paymentMethod: PaymentMethod
  /** Link sang cổng thanh toán (sandbox) */
  paymentUrl: string | null
  orderInfo: string | null
  createdAt: string
  updatedAt: string
}

export interface PaymentCreatePayload {
  planId: number
  paymentMethod: PaymentMethod
  orderInfo?: string
  returnUrl?: string
}

export type BillingCycle = 'MONTHLY' | 'YEARLY' | 'ONE_TIME'
export type SubscriptionStatus = 'ACTIVE' | 'CANCELLED' | 'EXPIRED'

export interface SubscriptionPlan {
  planId: number
  code: string
  name: string
  description: string | null
  price: number
  billingCycle: BillingCycle
  durationDays: number | null
  targetRole: 'STUDENT' | 'TEACHER'
  features: string[]
  active: boolean
  createdAt: string
  updatedAt: string
}

export interface Subscription {
  subscriptionId: number
  userId: number
  plan: SubscriptionPlan
  status: SubscriptionStatus
  startedAt: string
  expiresAt: string | null
  cancelAtPeriodEnd: boolean
  cancelledAt: string | null
  lastPaymentId: number | null
  createdAt: string
  updatedAt: string
}

export interface Refund {
  refundId: number
  paymentId: number
  userId: number
  amount: number
  reason: string
  status: RefundStatus
  createdAt: string
  updatedAt: string
}

export interface LearningAccess {
  id: number
  userId: number
  productId: number
  paymentId: number
  accessType: string
  expiresAt: string | null
  grantedAt: string
}
