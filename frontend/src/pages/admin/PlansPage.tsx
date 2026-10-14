import { useState } from 'react'
import type { FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Check, Crown, Pencil, Plus, Power, PowerOff } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Field, FormError, Select, SubmitButton, TextArea } from '@/components/teacher/FormKit'
import { CardSkeleton, EmptyState, ErrorState, PageHeader, Pill } from '@/components/student/StudentUi'
import { createPlan, deactivatePlan, getAllPlans, updatePlan } from '@/api/adminApi'
import { adminKeys } from '@/hooks/useAdminData'
import { BILLING_CYCLE_LABEL, planPeriod } from '@/lib/billing'
import { formatMoney } from '@/lib/download'
import { getErrorMessage } from '@/lib/format'
import { confirmDialog, notify } from '@/lib/notify'
import { cn } from '@/lib/utils'
import type { BillingCycle, SubscriptionPlan, SubscriptionPlanPayload } from '@/types/billing'

type Target = 'all' | 'STUDENT' | 'TEACHER'
const TARGET_LABEL = { STUDENT: 'Học sinh', TEACHER: 'Giáo viên' } as const

function toPayload(plan: SubscriptionPlan, patch: Partial<SubscriptionPlanPayload> = {}): SubscriptionPlanPayload {
  return {
    code: plan.code,
    name: plan.name,
    description: plan.description,
    price: plan.price,
    billingCycle: plan.billingCycle,
    durationDays: plan.durationDays,
    targetRole: plan.targetRole,
    features: plan.features,
    active: plan.active,
    ...patch,
  }
}

/** Quản lý gói đăng ký (GET /admin/plans, POST /plans, PUT /plans/{id}, DELETE /plans/{id}). */
export default function AdminPlansPage() {
  const queryClient = useQueryClient()
  const plans = useQuery({ queryKey: adminKeys.plans, queryFn: getAllPlans })
  const [target, setTarget] = useState<Target>('all')
  const [editing, setEditing] = useState<{ open: boolean; plan: SubscriptionPlan | null }>({ open: false, plan: null })

  const refresh = () => queryClient.invalidateQueries({ queryKey: adminKeys.plans })
  const toggle = useMutation({
    mutationFn: (plan: SubscriptionPlan) => (plan.active ? deactivatePlan(plan.planId) : updatePlan(plan.planId, toPayload(plan, { active: true })).then(() => undefined)),
    onSuccess: (_d, plan) => { refresh(); notify.success(plan.active ? `Đã ngừng bán "${plan.name}"` : `Đã mở bán lại "${plan.name}"`) },
    onError: e => notify.apiError(e, 'Không cập nhật được gói.'),
  })

  const askToggle = async (plan: SubscriptionPlan) => {
    const ok = plan.active
      ? await confirmDialog({
        title: `Ngừng bán gói "${plan.name}"?`,
        description: 'Người dùng không mua mới được nữa. Người đã mua vẫn dùng tới hết hạn.',
        confirmText: 'Ngừng bán',
        destructive: true,
      })
      : await confirmDialog({ title: `Mở bán lại gói "${plan.name}"?`, confirmText: 'Mở bán' })
    if (ok) toggle.mutate(plan)
  }

  const list = (plans.data ?? [])
    .filter(p => target === 'all' || p.targetRole === target)
    .sort((a, b) => Number(b.active) - Number(a.active) || a.targetRole.localeCompare(b.targetRole) || a.price - b.price)

  return (
    <div className="mx-auto max-w-6xl space-y-6">
      <PageHeader
        title="Gói đăng ký"
        description="Các gói học sinh và giáo viên có thể mua. Giá khi thanh toán luôn lấy từ đây."
        action={(
          <Button className="h-10 rounded-xl px-4" onClick={() => setEditing({ open: true, plan: null })}>
            <Plus size={16} aria-hidden="true" /> Tạo gói
          </Button>
        )}
      />

      <Select aria-label="Lọc theo đối tượng" value={target} onChange={e => setTarget(e.target.value as Target)} className="h-10 sm:w-56">
        <option value="all">Mọi đối tượng</option>
        <option value="STUDENT">Gói cho học sinh</option>
        <option value="TEACHER">Gói cho giáo viên</option>
      </Select>

      {plans.isError ? <ErrorState /> : plans.isLoading ? <CardSkeleton count={3} /> : list.length === 0 ? (
        <EmptyState icon={Crown} title="Chưa có gói nào" />
      ) : (
        <ul className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
          {list.map(plan => (
            <li key={plan.planId} className={cn('flex flex-col rounded-2xl border border-border/50 bg-card p-5 dark:bg-card/70', !plan.active && 'opacity-70')}>
              <div className="flex flex-wrap items-center gap-2">
                <Pill tone={plan.targetRole === 'TEACHER' ? 'violet' : 'blue'}>{TARGET_LABEL[plan.targetRole]}</Pill>
                <Pill tone={plan.active ? 'emerald' : 'muted'}>{plan.active ? 'Đang bán' : 'Ngừng bán'}</Pill>
              </div>
              <h3 className="mt-3 font-semibold text-foreground">{plan.name}</h3>
              <p className="font-mono text-xs text-muted-foreground">{plan.code}</p>
              {plan.description && <p className="mt-2 text-sm text-muted-foreground">{plan.description}</p>}
              <p className="mt-3">
                <span className="text-2xl font-bold tabular-nums tracking-tight text-foreground">{formatMoney(plan.price)}</span>
                <span className="ml-1 text-sm text-muted-foreground">{planPeriod(plan.billingCycle, plan.durationDays)}</span>
              </p>
              <ul className="mt-3 flex-1 space-y-1.5 text-sm text-foreground">
                {plan.features.map(f => (
                  <li key={f} className="flex items-start gap-2"><Check size={15} className="mt-0.5 shrink-0 text-emerald-600 dark:text-emerald-400" aria-hidden="true" />{f}</li>
                ))}
              </ul>
              <div className="mt-4 flex gap-2">
                <Button variant="outline" className="h-10 flex-1 rounded-xl" onClick={() => setEditing({ open: true, plan })}>
                  <Pencil size={15} aria-hidden="true" /> Sửa
                </Button>
                <Button
                  variant="outline"
                  className={cn('h-10 flex-1 rounded-xl', plan.active && 'text-destructive hover:bg-destructive/10')}
                  disabled={toggle.isPending}
                  onClick={() => askToggle(plan)}
                >
                  {plan.active ? <><PowerOff size={15} aria-hidden="true" /> Ngừng bán</> : <><Power size={15} aria-hidden="true" /> Mở bán</>}
                </Button>
              </div>
            </li>
          ))}
        </ul>
      )}

      <Dialog open={editing.open} onOpenChange={open => { if (!open) setEditing({ open: false, plan: null }) }}>
        <DialogContent className="max-h-[90vh] overflow-y-auto sm:max-w-lg">
          {editing.open && (
            <PlanForm
              key={editing.plan?.planId ?? 'new'}
              plan={editing.plan}
              onDone={() => { setEditing({ open: false, plan: null }); refresh() }}
            />
          )}
        </DialogContent>
      </Dialog>
    </div>
  )
}

function PlanForm({ plan, onDone }: { plan: SubscriptionPlan | null; onDone: () => void }) {
  const [form, setForm] = useState(() => ({
    code: plan?.code ?? '',
    name: plan?.name ?? '',
    description: plan?.description ?? '',
    price: plan ? String(plan.price) : '',
    billingCycle: plan?.billingCycle ?? ('MONTHLY' as BillingCycle),
    durationDays: plan?.durationDays != null ? String(plan.durationDays) : '',
    targetRole: plan?.targetRole ?? ('STUDENT' as 'STUDENT' | 'TEACHER'),
    features: (plan?.features ?? []).join('\n'),
    active: plan?.active ?? true,
  }))
  const set = (k: keyof typeof form) => (e: { target: { value: string } }) => setForm(p => ({ ...p, [k]: e.target.value }))

  const save = useMutation({
    mutationFn: () => {
      const payload: SubscriptionPlanPayload = {
        code: form.code.trim(),
        name: form.name.trim(),
        description: form.description.trim() || null,
        price: Number(form.price),
        billingCycle: form.billingCycle,
        durationDays: form.durationDays ? Number(form.durationDays) : null,
        targetRole: form.targetRole,
        features: form.features.split('\n').map(x => x.trim()).filter(Boolean),
        active: form.active,
      }
      return plan ? updatePlan(plan.planId, payload) : createPlan(payload)
    },
    onSuccess: saved => { notify.success(plan ? `Đã lưu gói "${saved.name}"` : `Đã tạo gói "${saved.name}"`); onDone() },
  })

  const submit = (e: FormEvent) => {
    e.preventDefault()
    save.mutate()
  }

  return (
    <form onSubmit={submit} className="space-y-4">
      <DialogHeader>
        <DialogTitle>{plan ? 'Sửa gói' : 'Tạo gói mới'}</DialogTitle>
        <DialogDescription>
          {plan ? 'Đổi giá chỉ áp dụng cho lần mua sau; người đã mua giữ nguyên thời hạn.' : 'Gói mới sẽ hiện ngay ở trang "Gói & thanh toán" của đối tượng tương ứng.'}
        </DialogDescription>
      </DialogHeader>
      <div className="grid gap-4 sm:grid-cols-2">
        <Field label="Mã gói *" hint="Chữ, số, - hoặc _">
          <Input required maxLength={50} pattern="[A-Za-z0-9_\-]+" value={form.code} onChange={set('code')} className="h-11 font-mono" placeholder="STUDENT_MONTHLY" />
        </Field>
        <Field label="Dành cho *">
          <Select value={form.targetRole} onChange={set('targetRole')}>
            <option value="STUDENT">Học sinh</option>
            <option value="TEACHER">Giáo viên</option>
          </Select>
        </Field>
      </div>
      <Field label="Tên gói *"><Input required maxLength={120} value={form.name} onChange={set('name')} className="h-11" /></Field>
      <Field label="Mô tả"><TextArea maxLength={1000} rows={2} value={form.description} onChange={set('description')} /></Field>
      <div className="grid gap-4 sm:grid-cols-3">
        <Field label="Giá (VND) *"><Input required type="number" min={1000} step={1000} value={form.price} onChange={set('price')} className="h-11" /></Field>
        <Field label="Chu kỳ *">
          <Select value={form.billingCycle} onChange={set('billingCycle')}>
            {(Object.keys(BILLING_CYCLE_LABEL) as BillingCycle[]).map(c => <option key={c} value={c}>{BILLING_CYCLE_LABEL[c]}</option>)}
          </Select>
        </Field>
        <Field label="Số ngày" hint="Trống = 30 / 365 ngày">
          <Input type="number" min={1} value={form.durationDays} onChange={set('durationDays')} className="h-11" />
        </Field>
      </div>
      <Field label="Quyền lợi" hint="Mỗi dòng một quyền lợi">
        <TextArea rows={4} value={form.features} onChange={set('features')} placeholder={'Xem video bài giảng\nLàm bài kiểm tra'} />
      </Field>
      <label className="flex items-center gap-2 text-sm text-foreground">
        <input type="checkbox" checked={form.active} onChange={e => setForm(p => ({ ...p, active: e.target.checked }))} className="h-4 w-4 accent-primary" />
        Đang bán
      </label>
      <FormError message={save.isError ? getErrorMessage(save.error, 'Không lưu được gói.') : null} />
      <DialogFooter><SubmitButton pending={save.isPending}>{plan ? 'Lưu thay đổi' : 'Tạo gói'}</SubmitButton></DialogFooter>
    </form>
  )
}
