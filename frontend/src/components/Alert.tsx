import type { ReactNode } from 'react'

const TONES = {
  error:
    'border-red-700 bg-red-50 text-red-900 dark:border-red-400 dark:bg-red-950 dark:text-red-100',
  success:
    'border-emerald-700 bg-emerald-50 text-emerald-900 dark:border-emerald-400 dark:bg-emerald-950 dark:text-emerald-100',
} as const

/**
 * A message that screen readers announce when it appears: an error as an alert, a confirmation
 * as a polite status (UI-8).
 */
export function Alert({
  tone = 'error',
  children,
}: {
  tone?: keyof typeof TONES
  children: ReactNode
}) {
  return (
    <div
      role={tone === 'error' ? 'alert' : 'status'}
      className={`rounded-md border-l-4 px-4 py-3 text-sm ${TONES[tone]}`}
    >
      {children}
    </div>
  )
}
