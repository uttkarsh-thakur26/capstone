import { Link } from 'react-router'
import { useAuth } from '../auth/useAuth'

/**
 * The start page after sign-in. Documents, search and the assistant arrive in later phases, so
 * this page says so instead of showing empty sections.
 */
export function HomePage() {
  const { state } = useAuth()
  if (state.status !== 'authenticated') return null
  const { user } = state

  return (
    <section className="space-y-4">
      <h1 className="text-2xl font-semibold">Welcome, {user.displayName}</h1>
      <p>
        You are signed in as <strong>{user.email}</strong> with the role{' '}
        <strong>{user.role}</strong>.
      </p>
      <p className="text-slate-700 dark:text-slate-300">
        Documents, search and questions are not available yet.
      </p>
      {user.role === 'ADMIN' && (
        <p>
          <Link className="underline underline-offset-2" to="/admin/users">
            Manage users and roles
          </Link>
        </p>
      )}
    </section>
  )
}
