import { useState } from 'react'
import { Link } from 'react-router-dom'
import { ChevronDown, LayoutDashboard, LogOut, Menu, Settings, X } from 'lucide-react'
import { Button } from '@/components/ui/button'
import {
  DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuLabel, DropdownMenuSeparator, DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { ThemeToggle } from '@/components/common/ThemeToggle'
import { useAuthStore } from '@/store/authStore'
import { HOME_BY_ROLE, ROLE_TITLE } from '@/lib/roles'
import { cn } from '@/lib/utils'
import { useActiveSection } from '@/hooks/useActiveSection'
import { LANDING_SECTIONS } from './sections'

const SECTION_IDS = LANDING_SECTIONS.map(s => s.id)

/** Thanh điều hướng trang chủ: chưa đăng nhập → nút Đăng nhập; đã đăng nhập → tên người dùng + menu. */
export function LandingHeader({ onLogin }: { onLogin: () => void }) {
  const { user, isAuthenticated, logout } = useAuthStore()
  const [mobileOpen, setMobileOpen] = useState(false)
  const signedIn = isAuthenticated && user != null
  const home = signedIn ? HOME_BY_ROLE[user.role] : '/'
  const active = useActiveSection(SECTION_IDS)

  return (
    <header className="fixed inset-x-0 top-0 z-50 border-b border-border/40 bg-background/70 backdrop-blur-xl">
      <div className="mx-auto flex h-16 w-full max-w-6xl items-center justify-between gap-4 px-4 sm:px-6">
        <Link to="/" className="flex shrink-0 items-center gap-2.5">
          <img src="/logo-icon.png" alt="EduMind Logo" className="h-9 w-9 object-contain" />
          <span className="text-xl font-bold tracking-tight text-foreground">Edu<span className="text-primary">Mind</span></span>
        </Link>

        <nav className="hidden items-center gap-1 lg:flex" aria-label="Các phần của trang">
          {LANDING_SECTIONS.map(s => (
            <a
              key={s.id}
              href={`#${s.id}`}
              aria-current={active === s.id ? 'location' : undefined}
              className={cn(
                'rounded-lg px-3 py-2 text-sm font-medium transition-colors',
                active === s.id ? 'bg-primary/10 text-primary' : 'text-muted-foreground hover:bg-muted/60 hover:text-foreground',
              )}
            >
              {s.label}
            </a>
          ))}
        </nav>

        <div className="flex items-center gap-2">
          <ThemeToggle />
          {signedIn ? (
            <DropdownMenu>
              <DropdownMenuTrigger asChild>
                <button className="flex items-center gap-2 rounded-xl border border-border/60 bg-background/60 py-1 pl-1 pr-2 transition-colors hover:bg-muted/60" aria-label={`Tài khoản ${user.name}`}>
                  <span className="flex h-8 w-8 items-center justify-center rounded-full bg-linear-to-tr from-primary to-violet-500 text-sm font-bold text-primary-foreground">
                    {user.name?.charAt(0).toUpperCase() || 'U'}
                  </span>
                  <span className="hidden max-w-36 truncate text-sm font-semibold text-foreground sm:block">{user.name}</span>
                  <ChevronDown size={15} className="text-muted-foreground" aria-hidden="true" />
                </button>
              </DropdownMenuTrigger>
              <DropdownMenuContent align="end" className="w-60">
                <DropdownMenuLabel className="font-normal">
                  <p className="truncate text-sm font-semibold text-foreground">{user.name}</p>
                  <p className="truncate text-xs text-muted-foreground">{user.email} · {ROLE_TITLE[user.role]}</p>
                </DropdownMenuLabel>
                <DropdownMenuSeparator />
                <DropdownMenuItem asChild className="cursor-pointer">
                  <Link to={home}><LayoutDashboard size={15} aria-hidden="true" /> Vào trang của tôi</Link>
                </DropdownMenuItem>
                <DropdownMenuItem asChild className="cursor-pointer">
                  <Link to={`${home}/settings`}><Settings size={15} aria-hidden="true" /> Cài đặt tài khoản</Link>
                </DropdownMenuItem>
                <DropdownMenuSeparator />
                <DropdownMenuItem className="cursor-pointer text-destructive focus:text-destructive" onClick={logout}>
                  <LogOut size={15} aria-hidden="true" /> Đăng xuất
                </DropdownMenuItem>
              </DropdownMenuContent>
            </DropdownMenu>
          ) : (
            <>
              <Button variant="ghost" className="hidden h-10 rounded-xl font-semibold sm:flex" onClick={onLogin}>Đăng nhập</Button>
              <Button className="h-10 rounded-xl px-4 font-semibold shadow-lg shadow-primary/25" onClick={onLogin}>Bắt đầu miễn phí</Button>
            </>
          )}
          <button
            className="flex h-10 w-10 items-center justify-center rounded-xl text-muted-foreground hover:bg-muted lg:hidden"
            onClick={() => setMobileOpen(o => !o)}
            aria-label={mobileOpen ? 'Đóng menu' : 'Mở menu'}
            aria-expanded={mobileOpen}
          >
            {mobileOpen ? <X size={20} /> : <Menu size={20} />}
          </button>
        </div>
      </div>

      {mobileOpen && (
        <nav className="border-t border-border/40 bg-background/95 px-4 py-3 lg:hidden" aria-label="Các phần của trang">
          {LANDING_SECTIONS.map(s => (
            <a
              key={s.id}
              href={`#${s.id}`}
              onClick={() => setMobileOpen(false)}
              aria-current={active === s.id ? 'location' : undefined}
              className={cn('block rounded-lg px-3 py-2.5 text-sm font-medium', active === s.id ? 'bg-primary/10 text-primary' : 'text-foreground hover:bg-muted')}
            >
              {s.label}
            </a>
          ))}
          {!signedIn && (
            <button onClick={() => { setMobileOpen(false); onLogin() }} className="mt-1 block w-full rounded-lg px-3 py-2.5 text-left text-sm font-semibold text-primary hover:bg-muted">
              Đăng nhập
            </button>
          )}
        </nav>
      )}
    </header>
  )
}
