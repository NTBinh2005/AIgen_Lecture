/** Types thanh toán / hoá đơn / hoàn tiền / quyền học — khớp DTO backend (đã gọi thử). */

export type PaymentMethod = 'SEPAY'
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
  /** URL ảnh VietQR do SePay tạo */
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

/** Body của POST /plans và PUT /plans/{id} (chỉ ADMIN). */
export interface SubscriptionPlanPayload {
  code: string
  name: string
  description: string | null
  price: number
  billingCycle: BillingCycle
  durationDays: number | null
  targetRole: 'STUDENT' | 'TEACHER'
  features: string[]
  active: boolean
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
  /** Tài khoản nhận tiền hoàn (null với yêu cầu cũ) */
  bankName?: string | null
  bankAccount?: string | null
  accountHolder?: string | null
  /** Nội dung chuyển khoản hoàn tiền, VD HT12 */
  refundCode?: string | null
  /** Ảnh VietQR chuyển tới tài khoản nhận — chỉ có khi đã duyệt (SePay) */
  qrUrl?: string | null
  sepayWebhookId?: number | null
  sepayReferenceCode?: string | null
}

export interface RefundBankAccount {
  bankName: string
  bankAccount: string
  accountHolder: string
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
