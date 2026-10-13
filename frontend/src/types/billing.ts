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
  productId: number
  amount: number
  paymentMethod: PaymentMethod
  orderInfo?: string
  returnUrl?: string
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
