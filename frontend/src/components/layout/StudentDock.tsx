import { Link, useLocation } from 'react-router-dom'
import { cn } from '@/lib/utils'
import { isStudentNavActive, studentNavItems } from './studentNav'

/**
 * Thanh điều hướng nổi ở đáy màn hình của học sinh. StudentLayout chừa khoảng trống phía dưới nội dung
 * để dock không che mất nội dung khi cuộn tới cuối trang.
 */
export function StudentDock() {
  const { pathname } = useLocation()

  return (
    <nav
      aria-label="Điều hướng học sinh"
      className="pointer-events-none fixed inset-x-0 bottom-3 z-40 flex justify-center px-3 pb-[env(safe-area-inset-bottom)]"
    >
      <div className="pointer-events-auto flex max-w-full gap-1 rounded-2xl border border-border/60 bg-background/80 p-1.5 shadow-xl shadow-primary/10 backdrop-blur-xl">
        {studentNavItems.map(item => {
          const isActive = isStudentNavActive(pathname, item.href)
          return (
            <Link
              key={item.href}
              to={item.href}
              aria-label={item.title}
              aria-current={isActive ? 'page' : undefined}
              className="group/dock relative flex h-11 w-11 min-w-0 shrink items-center justify-center rounded-xl outline-none"
            >
              <span
                className={cn(
                  'flex h-full w-full items-center justify-center rounded-xl transition-all duration-200 group-hover/dock:-translate-y-1 group-hover/dock:scale-110 group-focus-visible/dock:ring-2 group-focus-visible/dock:ring-ring',
                  isActive
                    ? 'bg-primary/15 text-primary ring-1 ring-primary/30'
                    : 'bg-primary/5 text-muted-foreground group-hover/dock:bg-primary/10 group-hover/dock:text-foreground',
                )}
              >
                <item.icon size={20} aria-hidden="true" />
              </span>
              <span
                aria-hidden="true"
                className="pointer-events-none absolute bottom-full left-1/2 mb-2.5 -translate-x-1/2 whitespace-nowrap rounded-lg bg-foreground px-2.5 py-1.5 text-xs font-medium text-background opacity-0 shadow-lg transition-opacity duration-150 after:absolute after:left-1/2 after:top-full after:-mt-1 after:size-2 after:-translate-x-1/2 after:rotate-45 after:bg-foreground group-hover/dock:opacity-100 group-focus-visible/dock:opacity-100"
              >
                {item.title}
              </span>
            </Link>
          )
        })}
      </div>
    </nav>
  )
}
