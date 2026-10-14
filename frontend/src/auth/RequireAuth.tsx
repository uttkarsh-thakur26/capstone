import type { ReactNode } from 'react'
import { Navigate, Outlet, useLocation } from 'react-router'
import type { Role } from '../api/types'
import { NotAllowedPage } from '../pages/NotAllowedPage'
import { useAuth } from './useAuth'

/**
 * Route guard. Anonymous visitors go to sign-in and come back afterwards; a signed-in user without
 * one of the roles sees the not-allowed page. After a deliberate sign-out nobody comes back: the
 * next person to sign in starts at home, not on the page the previous user had open. The guard is
 * for convenience only: the server checks every request itself (UI-2).
 */
export function RequireAuth({
  roles,
  children,
}: {
  roles?: readonly Role[]
  children?: ReactNode
}) {
  const { state } = useAuth()
  const location = useLocation()

  if (state.status === 'loading') {
    return (
      <p role="status" className="p-6 text-slate-600 dark:text-slate-300">
        Loading…
      </p>
    )
  }
  if (state.status === 'anonymous') {
    return (
      <Navigate
        to="/login"
        replace
        state={state.signedOut ? undefined : { from: location }}
      />
    )
  }
  if (roles && !roles.includes(state.user.role)) {
    return <NotAllowedPage />
  }
  return children ?? <Outlet />
}
