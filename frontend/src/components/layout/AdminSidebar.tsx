import { Link, useLocation } from 'react-router-dom'
import { adminNavItems, isAdminNavActive } from './adminNav'

export function AdminSidebar() {
  const location = useLocation()

  return (
    <aside className="hidden lg:flex h-screen flex-col fixed left-0 top-0 border-r border-border/50 bg-background/80 hover:bg-background/95 backdrop-blur-xl z-30 transition-[width,box-shadow,background-color] duration-300 ease-in-out w-20 hover:w-72 hover:shadow-2xl overflow-hidden group/sidebar">
      {/* Logo Area */}
      <div className="h-16 flex items-center px-3 border-b border-border/50 shrink-0 overflow-hidden">
        <Link to="/admin" className="flex items-center gap-3 px-2 py-2 rounded-xl group w-full">
          <div className="w-9 h-9 flex items-center justify-center shrink-0 group-hover:scale-105 transition-transform">
            <img src="/logo-icon.png" alt="EduAdmin Logo" className="w-9 h-9 object-contain" />
          </div>
          <span className="font-bold text-xl tracking-tight text-foreground whitespace-nowrap overflow-hidden transition-all duration-300 opacity-0 group-hover/sidebar:opacity-100 max-w-0 group-hover/sidebar:max-w-xs">
            Edu<span className="text-violet-600">Admin</span>
          </span>
        </Link>
      </div>

      {/* Navigation */}
      <div className="flex-1 overflow-y-auto py-6 px-3 flex flex-col gap-1.5 scrollbar-none overflow-x-hidden">
        <div className="h-6 flex items-center px-3 mb-2">
          <span className="text-xs font-semibold text-muted-foreground uppercase tracking-wider whitespace-nowrap overflow-hidden transition-all duration-300 opacity-0 group-hover/sidebar:opacity-100">
            Quản trị
          </span>
          <div className="w-6 h-px bg-border/60 mx-auto group-hover/sidebar:hidden transition-all" />
        </div>
        {adminNavItems.map((item) => {
          const isActive = isAdminNavActive(location.pathname, item.href)
          return (
            <Link
              key={item.href}
              to={item.href}
              title={item.title}
              className={`flex items-center gap-3 px-3 py-2.5 min-h-11 rounded-xl transition-all duration-200 group relative ${
                isActive
                  ? 'bg-violet-600/10 text-violet-600 font-semibold'
                  : 'text-muted-foreground hover:bg-muted/50 hover:text-foreground'
              }`}
            >
              {isActive && (
                <div className="absolute left-0 top-1/2 -translate-y-1/2 w-1 h-5 bg-violet-600 rounded-r-full" />
              )}
              <div className="w-8 h-8 flex items-center justify-center shrink-0">
                <item.icon size={18} className={isActive ? 'text-violet-600' : 'text-muted-foreground group-hover:text-foreground transition-colors'} aria-hidden="true" />
              </div>
              <span className="text-sm font-medium whitespace-nowrap overflow-hidden transition-all duration-300 opacity-0 group-hover/sidebar:opacity-100 max-w-0 group-hover/sidebar:max-w-[200px]">{item.title}</span>
            </Link>
          )
        })}
      </div>
    </aside>
  )
}
