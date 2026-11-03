import type { ReactNode } from 'react'
import { Alert } from './Alert'
import { Button } from './Button'

/** A loading message with placeholder lines; screen readers hear the message only (UI-3). */
export function LoadingState({
  children,
  lines = 3,
}: {
  children: ReactNode
  lines?: number
}) {
  return (
    <div className="space-y-3">
      <p role="status" className="text-sm text-muted">
        {children}
      </p>
      <div aria-hidden="true" className="space-y-2">
        {Array.from({ length: lines }, (_, i) => (
          <div
            key={i}
            className="h-4 animate-pulse rounded bg-subtle"
            style={{ width: `${90 - i * 15}%` }}
          />
        ))}
      </div>
    </div>
  )
}

/** A failure with a way to try again (UI-3). */
export function ErrorState({
  children,
  onRetry,
}: {
  children: ReactNode
  onRetry?: () => void
}) {
  return (
    <div className="space-y-3">
      <Alert>{children}</Alert>
      {onRetry && (
        <Button variant="secondary" size="sm" onPress={onRetry}>
          Try again
        </Button>
      )}
    </div>
  )
}

/** Nothing to show yet, said in words, with the next step when there is one (UI-3). */
export function EmptyState({
  children,
  action,
}: {
  children: ReactNode
  action?: ReactNode
}) {
  return (
    <div className="space-y-3 rounded-lg border border-dashed border-line-strong/60 px-4 py-6 text-center text-sm text-muted">
      <p>{children}</p>
      {action && <div className="flex justify-center">{action}</div>}
    </div>
  )
}
