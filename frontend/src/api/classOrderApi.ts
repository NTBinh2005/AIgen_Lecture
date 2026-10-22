import axiosInstance from './axiosInstance'

export interface ClassOrder {
  orderId: number
  orderCode: string
  studentId: number
  studentName: string
  classId: number
  className: string
  price: number
  platformFeePercent: number
  platformFeeAmount: number
  teacherAmount: number
  paymentMethod: string
  status: 'ACTIVE' | 'REFUNDED'
  holdUntil: string
  createdAt: string
  refundedAt?: string
  qrUrl?: string
  accountNumber?: string
  accountName?: string
  bankName?: string
}

export interface ClassProgress {
  classId: number
  totalLectures: number
  completedLectures: number
  watchedPercentage: number
  canRefund: boolean
  refundBlockReason?: string
  remainingHoldSeconds?: number
}

export async function purchaseClass(classId: number, paymentMethod: 'WALLET' | 'SEPAY_QR'): Promise<ClassOrder> {
  const res = await axiosInstance.post<ClassOrder>('/class-orders/purchase', { classId, paymentMethod })
  return res.data
}

export async function refundClass(classId: number): Promise<{ message: string }> {
  const res = await axiosInstance.post<{ message: string }>(`/class-orders/classes/${classId}/refund`)
  return res.data
}

export async function getMyClassOrders(page = 0, size = 20): Promise<{ content: ClassOrder[]; totalElements: number; totalPages: number }> {
  const res = await axiosInstance.get('/class-orders/my', { params: { page, size } })
  return res.data
}

export async function getClassOrders(classId: number, page = 0, size = 20): Promise<{ content: ClassOrder[]; totalElements: number; totalPages: number }> {
  const res = await axiosInstance.get(`/class-orders/classes/${classId}`, { params: { page, size } })
  return res.data
}

export async function recordLectureProgress(
  classId: number,
  lectureId: number | string,
  payload: { watchedSeconds: number; totalSeconds: number; isCompleted?: boolean }
): Promise<{ success: boolean }> {
  const res = await axiosInstance.post(`/classes/${classId}/lectures/${lectureId}/progress`, payload)
  return res.data
}

export async function getClassProgress(classId: number): Promise<ClassProgress> {
  const res = await axiosInstance.get<ClassProgress>(`/classes/${classId}/progress`)
  return res.data
}
