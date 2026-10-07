import { NavLink, Outlet } from 'react-router'
import { useAuth } from '../auth/useAuth'
import { Button } from '../components/Button'
import { ProductName, ProductVersion } from './ProductName'

const LINK =
  'rounded-md px-3 py-2 text-sm font-medium outline-none focus-visible:outline-2 focus-visible:outline-indigo-600'
const linkClass = ({ isActive }: { isActive: boolean }) =>
  `${LINK} ${isActive ? 'bg-slate-200 dark:bg-slate-800' : 'hover:bg-slate-100 dark:hover:bg-slate-900'}`

/**
 * The frame of every signed-in page. The navigation shows only what the user's role may use, for
 * convenience; the server enforces every rule (UI-2).
 */
export function AppShell() {
  const { state, logout } = useAuth()
  if (state.status !== 'authenticated') return null
  const { user } = state

  return (
    <div className="flex min-h-screen flex-col">
      <a
        href="#main"
        className="sr-only focus:not-sr-only focus:absolute focus:left-2 focus:top-2 focus:rounded-md focus:bg-white focus:px-3 focus:py-2 dark:focus:bg-slate-900"
      >
        Skip to content
      </a>
      <header className="border-b border-slate-200 dark:border-slate-800">
        <div className="mx-auto flex max-w-5xl flex-wrap items-center gap-x-6 gap-y-2 px-4 py-3">
          <span className="font-semibold">
            <ProductName />
          </span>
          <nav aria-label="Main" className="flex flex-wrap gap-1">
            <NavLink to="/" end className={linkClass}>
              Home
            </NavLink>
            {user.role === 'ADMIN' && (
              <NavLink to="/admin/users" className={linkClass}>
                Users
              </NavLink>
            )}
          </nav>
          <div className="ml-auto flex items-center gap-3 text-sm">
            <span>
              {user.displayName}{' '}
              <span className="rounded bg-slate-200 px-2 py-0.5 text-xs font-medium dark:bg-slate-800">
                {user.role}
              </span>
            </span>
            <Button variant="secondary" onPress={() => void logout()}>
              Sign out
            </Button>
          </div>
        </div>
      </header>
      <main id="main" className="mx-auto w-full max-w-5xl flex-1 px-4 py-8">
        <Outlet />
      </main>
      <footer className="border-t border-slate-200 px-4 py-3 text-center text-xs text-slate-600 dark:border-slate-800 dark:text-slate-300">
        <ProductVersion />
      </footer>
    </div>
  )
}
