import type { ChangeEvent } from 'react'
import { Input } from '@/components/ui/input'
import { Field } from '@/components/teacher/FormKit'
import { BANK_ACCOUNT_PATTERN, BANK_NAME_PATTERN, VIETQR_BANKS } from '@/lib/banks'
import type { RefundBankAccount } from '@/types/billing'

/** Ba ô tài khoản nhận tiền hoàn: ngân hàng (gợi ý theo VietQR), số tài khoản, tên chủ tài khoản. */
export function RefundBankFields({ value, onChange, idPrefix }: {
  value: RefundBankAccount
  onChange: (next: RefundBankAccount) => void
  idPrefix: string
}) {
  const set = (key: keyof RefundBankAccount) => (e: ChangeEvent<HTMLInputElement>) => onChange({ ...value, [key]: e.target.value })
  return (
    <fieldset className="space-y-3 rounded-xl border border-border/60 p-3">
      <legend className="px-1 text-sm font-medium text-foreground">Tài khoản nhận tiền hoàn</legend>
      <Field label="Ngân hàng *">
        <Input
          id={`${idPrefix}-bank`}
          required
          list={`${idPrefix}-banks`}
          pattern={BANK_NAME_PATTERN}
          title="Tên viết tắt (VD MBBank, Vietcombank) hoặc mã BIN, không dấu, không khoảng trắng"
          placeholder="VD: MBBank"
          autoComplete="off"
          value={value.bankName}
          onChange={set('bankName')}
        />
        <datalist id={`${idPrefix}-banks`}>
          {VIETQR_BANKS.map(b => <option key={b} value={b} />)}
        </datalist>
      </Field>
      <Field label="Số tài khoản *">
        <Input
          id={`${idPrefix}-account`}
          required
          inputMode="numeric"
          pattern={BANK_ACCOUNT_PATTERN}
          title="4–34 ký tự chữ / số, không khoảng trắng"
          autoComplete="off"
          value={value.bankAccount}
          onChange={set('bankAccount')}
        />
      </Field>
      <Field label="Tên chủ tài khoản *">
        <Input
          id={`${idPrefix}-holder`}
          required
          maxLength={150}
          placeholder="VD: NGUYEN VAN A"
          autoComplete="off"
          value={value.accountHolder}
          onChange={e => onChange({ ...value, accountHolder: e.target.value.toUpperCase() })}
        />
      </Field>
      <p className="text-xs text-muted-foreground">Kiểm tra kỹ: tiền hoàn được chuyển đúng vào tài khoản này và không sửa được sau khi đã duyệt.</p>
    </fieldset>
  )
}
