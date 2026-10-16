import type { RefundBankAccount } from '@/types/billing'

/** Tên viết tắt ngân hàng theo VietQR (backend nhận mã BIN hoặc tên viết tắt, 2–30 ký tự chữ/số). */
export const VIETQR_BANKS = [
  'Vietcombank', 'VietinBank', 'BIDV', 'Agribank', 'MBBank', 'Techcombank', 'ACB', 'VPBank', 'TPBank',
  'Sacombank', 'HDBank', 'VIB', 'SHB', 'SeABank', 'OCB', 'MSB', 'Eximbank', 'LPBank', 'NamABank', 'ABBANK',
  'BacABank', 'PVcomBank', 'VietABank', 'KienLongBank', 'VietBank', 'BaoVietBank', 'PGBank', 'NCB', 'SaigonBank',
  'Cake', 'Timo',
] as const

export const BANK_NAME_PATTERN = '[A-Za-z0-9]{2,30}'
export const BANK_ACCOUNT_PATTERN = '[A-Za-z0-9]{4,34}'

export const EMPTY_BANK_ACCOUNT: RefundBankAccount = { bankName: '', bankAccount: '', accountHolder: '' }

/** Chuẩn hoá trước khi gửi: bỏ khoảng trắng trong mã ngân hàng / số tài khoản, tên chủ tài khoản viết hoa. */
export function normalizeBankAccount(a: RefundBankAccount): RefundBankAccount {
  return {
    bankName: a.bankName.replace(/\s+/g, ''),
    bankAccount: a.bankAccount.replace(/\s+/g, ''),
    accountHolder: a.accountHolder.trim().replace(/\s+/g, ' ').toUpperCase(),
  }
}

export function isBankAccountComplete(a: Pick<RefundBankAccount, 'bankName' | 'bankAccount' | 'accountHolder'> | { bankName?: string | null; bankAccount?: string | null; accountHolder?: string | null }) {
  return !!(a.bankName && a.bankAccount && a.accountHolder)
}
