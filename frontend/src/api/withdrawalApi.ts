import axiosInstance from './axiosInstance'

export interface WithdrawalRequestItem {
  withdrawalId: number
  teacherId: number
  teacherName: string
  teacherEmail: string
  amount: number
  bankName: string
  bankAccount: string
  accountHolder: string
  status: 'REQUESTED' | 'APPROVED' | 'REJECTED' | 'COMPLETED'
  adminNote?: string
  qrUrl?: string
  createdAt: string
  updatedAt: string
}

export interface WithdrawalCreatePayload {
  amount: number
  bankName: string
  bankAccount: string
  accountHolder: string
}

export interface WithdrawalReviewPayload {
  status: 'APPROVED' | 'REJECTED' | 'COMPLETED'
  adminNote?: string
}

export async function requestWithdrawal(payload: WithdrawalCreatePayload): Promise<WithdrawalRequestItem> {
  const res = await axiosInstance.post<WithdrawalRequestItem>('/withdrawals', payload)
  return res.data
}

export async function getMyWithdrawals(page = 0, size = 20): Promise<{ content: WithdrawalRequestItem[]; totalElements: number; totalPages: number }> {
  const res = await axiosInstance.get('/withdrawals/my', { params: { page, size } })
  return res.data
}

export async function getAllWithdrawals(status?: string, page = 0, size = 20): Promise<{ content: WithdrawalRequestItem[]; totalElements: number; totalPages: number }> {
  const res = await axiosInstance.get('/withdrawals/admin', { params: { status, page, size } })
  return res.data
}

export async function reviewWithdrawal(id: number, payload: WithdrawalReviewPayload): Promise<WithdrawalRequestItem> {
  const res = await axiosInstance.patch<WithdrawalRequestItem>(`/withdrawals/admin/${id}`, payload)
  return res.data
}
