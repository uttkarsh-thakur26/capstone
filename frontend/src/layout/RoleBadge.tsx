import type { Role } from '../api/types'

const LABELS: Record<Role, string> = {
  ADMIN: 'Admin',
  FACULTY: 'Faculty',
  STUDENT: 'Student',
}

/** The user's role in words. */
export function RoleBadge({ role }: { role: Role }) {
  return (
    <span className="shrink-0 rounded-full bg-subtle px-2 py-0.5 text-xs font-medium text-muted">
      {LABELS[role]}
    </span>
  )
}
