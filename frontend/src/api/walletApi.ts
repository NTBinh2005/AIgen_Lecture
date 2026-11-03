import axiosInstance from './axiosInstance'

export interface Wallet {
  walletId: number
  userId: number
  balance: number
  pendingBalance: number
  lockedBalance: number
  totalBalance: number
  updatedAt: string
}

export type WalletTxType =
  | 'DEPOSIT'
  | 'CLASS_PURCHASE'
  | 'CLASS_INCOME_PENDING'
  | 'CLASS_INCOME_AVAILABLE'
  | 'CLASS_REFUND_STUDENT'
  | 'CLASS_REFUND_TEACHER_DEDUCTION'
  | 'WITHDRAWAL_LOCK'
  | 'WITHDRAWAL_COMPLETED'
  | 'WITHDRAWAL_CANCELLED'
  | 'SUBSCRIPTION_PAYMENT'

export interface WalletTransaction {
  transactionId: number
  type: WalletTxType
  amount: number
  balanceAfter: number
  description: string
  referenceId: string
  createdAt: string
}

export interface DepositResponse {
  orderCode: string
  amount: number
  qrUrl: string
  accountNumber: string
  accountName: string
  bankName: string
  description: string
}

export async function getMyWallet(): Promise<Wallet> {
  const res = await axiosInstance.get<Wallet>('/wallets/me')
  return res.data
}

export async function getMyTransactions(page = 0, size = 20): Promise<{ content: WalletTransaction[]; totalElements: number; totalPages: number }> {
  const res = await axiosInstance.get('/wallets/me/transactions', { params: { page, size } })
  return res.data
}

export async function createDeposit(amount: number): Promise<DepositResponse> {
  const res = await axiosInstance.post<DepositResponse>('/wallets/deposit', { amount })
  return res.data
}

export async function paySubscriptionWithWallet(planId: number): Promise<{ message: string }> {
  const res = await axiosInstance.post<{ message: string }>('/wallets/pay-subscription', { planId })
  return res.data
}
