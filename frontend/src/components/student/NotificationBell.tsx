import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Bell, CheckCheck } from 'lucide-react'
import {
  DropdownMenu, DropdownMenuContent, DropdownMenuLabel, DropdownMenuSeparator, DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { getNotifications, getUnreadCount, markAllNotificationsRead, markNotificationRead } from '@/api/accountApi'
import { formatDateTime } from '@/lib/format'
import { cn } from '@/lib/utils'
import type { AppNotification } from '@/types/student'

const isRead = (n: AppNotification) => Boolean(n.isRead ?? n.read)

export function NotificationBell() {
  const queryClient = useQueryClient()
  const unread = useQuery({ queryKey: ['notifications', 'unread'], queryFn: getUnreadCount, refetchInterval: 60_000 })
  const list = useQuery({ queryKey: ['notifications', 'list'], queryFn: getNotifications })

  const refresh = () => queryClient.invalidateQueries({ queryKey: ['notifications'] })
  const readOne = useMutation({ mutationFn: markNotificationRead, onSuccess: refresh })
  const readAll = useMutation({ mutationFn: markAllNotificationsRead, onSuccess: refresh })

  const count = unread.data ?? 0
  const items = list.data ?? []

  return (
    <DropdownMenu onOpenChange={open => { if (open) list.refetch() }}>
      <DropdownMenuTrigger asChild>
        <button
          className="relative flex h-10 w-10 items-center justify-center rounded-full text-muted-foreground transition-colors hover:bg-muted hover:text-foreground"
          aria-label={count > 0 ? `Thông báo, ${count} chưa đọc` : 'Thông báo'}
        >
          <Bell size={20} />
          {count > 0 && (
            <span className="absolute right-1 top-1 flex h-4 min-w-4 items-center justify-center rounded-full bg-blue-600 px-1 text-[10px] font-bold text-white">
              {count > 9 ? '9+' : count}
            </span>
          )}
        </button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end" className="w-[min(22rem,calc(100vw-2rem))] p-0">
        <div className="flex items-center justify-between px-3 py-2">
          <DropdownMenuLabel className="p-0 text-sm font-semibold text-foreground">Thông báo</DropdownMenuLabel>
          {count > 0 && (
            <button
              onClick={() => readAll.mutate()}
              className="flex items-center gap-1 text-xs font-medium text-primary hover:underline"
            >
              <CheckCheck size={14} aria-hidden="true" />
              Đánh dấu đã đọc
            </button>
          )}
        </div>
        <DropdownMenuSeparator className="m-0" />
        <div className="max-h-80 overflow-y-auto">
          {items.length === 0 ? (
            <p className="px-4 py-8 text-center text-sm text-muted-foreground">Chưa có thông báo nào.</p>
          ) : (
            items.map(n => (
              <button
                key={n.id}
                onClick={() => { if (!isRead(n)) readOne.mutate(n.id) }}
                className={cn(
                  'flex w-full gap-3 border-b border-border/40 px-3 py-3 text-left last:border-0 hover:bg-muted/60',
                  !isRead(n) && 'bg-primary/5',
                )}
              >
                <span className={cn('mt-1.5 h-2 w-2 shrink-0 rounded-full', isRead(n) ? 'bg-transparent' : 'bg-blue-600')} aria-hidden="true" />
                <span className="min-w-0">
                  <span className="block text-sm font-medium text-foreground">{n.title}</span>
                  <span className="mt-0.5 block text-xs text-muted-foreground line-clamp-2">{n.message}</span>
                  <span className="mt-1 block text-[11px] text-muted-foreground/80">{formatDateTime(n.createdAt)}</span>
                </span>
              </button>
            ))
          )}
        </div>
      </DropdownMenuContent>
    </DropdownMenu>
  )
}
