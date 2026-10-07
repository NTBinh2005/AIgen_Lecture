import { useState } from 'react'
import { Bar, BarChart, CartesianGrid, LabelList, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { BarChart3, Table2 } from 'lucide-react'
import { cn } from '@/lib/utils'

export interface BarDatum {
  label: string
  value: number
}

interface BarChartCardProps {
  title: string
  description?: string
  data: BarDatum[]
  /** Tên đơn vị cho tooltip/bảng, VD "người dùng" */
  unit: string
  /** Cột nằm ngang — dùng khi nhãn dài (tên lớp) */
  horizontal?: boolean
  loading?: boolean
  emptyText?: string
  className?: string
}

const AXIS_TICK = { fill: 'var(--muted-foreground)', fontSize: 12 }

/**
 * Biểu đồ cột một chuỗi: một màu (--viz-series-1, đã validate light/dark), không cần chú thích,
 * giá trị ghi ở đầu cột, tooltip khi hover, và chế độ xem bảng cho trợ năng.
 */
export function BarChartCard({ title, description, data, unit, horizontal = false, loading, emptyText = 'Chưa có dữ liệu', className }: BarChartCardProps) {
  const [asTable, setAsTable] = useState(false)
  const height = horizontal ? Math.max(180, data.length * 36 + 40) : 260

  return (
    <section className={cn('rounded-2xl border border-border/50 bg-card p-5 dark:bg-card/70', className)}>
      <div className="mb-4 flex items-start justify-between gap-3">
        <div>
          <h2 className="font-semibold text-foreground">{title}</h2>
          {description && <p className="mt-0.5 text-sm text-muted-foreground">{description}</p>}
        </div>
        <button
          onClick={() => setAsTable(v => !v)}
          className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg text-muted-foreground hover:bg-muted hover:text-foreground"
          aria-label={asTable ? 'Xem dạng biểu đồ' : 'Xem dạng bảng'}
          title={asTable ? 'Xem dạng biểu đồ' : 'Xem dạng bảng'}
        >
          {asTable ? <BarChart3 size={16} /> : <Table2 size={16} />}
        </button>
      </div>

      {loading ? (
        <div className="animate-pulse rounded-xl bg-muted" style={{ height }} />
      ) : data.length === 0 ? (
        <p className="flex items-center justify-center text-sm text-muted-foreground" style={{ height: 120 }}>{emptyText}</p>
      ) : asTable ? (
        <table className="w-full text-sm">
          <thead>
            <tr className="border-b border-border/60 text-left text-muted-foreground">
              <th className="py-2 font-medium">Hạng mục</th>
              <th className="py-2 text-right font-medium">Số {unit}</th>
            </tr>
          </thead>
          <tbody>
            {data.map(d => (
              <tr key={d.label} className="border-b border-border/40 last:border-0">
                <td className="py-2 text-foreground">{d.label}</td>
                <td className="py-2 text-right tabular-nums text-foreground">{d.value}</td>
              </tr>
            ))}
          </tbody>
        </table>
      ) : (
        <div style={{ height }} role="img" aria-label={`${title}: ${data.map(d => `${d.label} ${d.value}`).join(', ')}`}>
          <ResponsiveContainer width="100%" height="100%">
            <BarChart
              data={data}
              layout={horizontal ? 'vertical' : 'horizontal'}
              margin={horizontal ? { top: 0, right: 40, left: 0, bottom: 0 } : { top: 20, right: 8, left: -16, bottom: 0 }}
            >
              <CartesianGrid
                strokeDasharray="3 3"
                stroke="var(--border)"
                strokeOpacity={0.6}
                vertical={horizontal}
                horizontal={!horizontal}
              />
              {horizontal ? (
                <>
                  <XAxis type="number" allowDecimals={false} tick={AXIS_TICK} axisLine={false} tickLine={false} />
                  <YAxis type="category" dataKey="label" width={130} tick={AXIS_TICK} axisLine={false} tickLine={false} />
                </>
              ) : (
                <>
                  <XAxis dataKey="label" tick={AXIS_TICK} axisLine={false} tickLine={false} />
                  <YAxis allowDecimals={false} tick={AXIS_TICK} axisLine={false} tickLine={false} />
                </>
              )}
              <Tooltip
                cursor={{ fill: 'var(--muted)', opacity: 0.5 }}
                contentStyle={{
                  background: 'var(--popover)',
                  border: '1px solid var(--border)',
                  borderRadius: 12,
                  color: 'var(--popover-foreground)',
                  fontSize: 13,
                }}
                labelStyle={{ color: 'var(--popover-foreground)', fontWeight: 600 }}
                itemStyle={{ color: 'var(--popover-foreground)' }}
                formatter={(value) => [`${value} ${unit}`, '']}
                separator=""
              />
              <Bar
                dataKey="value"
                fill="var(--viz-series-1)"
                maxBarSize={24}
                radius={horizontal ? [0, 4, 4, 0] : [4, 4, 0, 0]}
                isAnimationActive={false}
              >
                <LabelList
                  dataKey="value"
                  position={horizontal ? 'right' : 'top'}
                  style={{ fill: 'var(--foreground)', fontSize: 12, fontVariantNumeric: 'tabular-nums' }}
                />
              </Bar>
            </BarChart>
          </ResponsiveContainer>
        </div>
      )}
    </section>
  )
}
