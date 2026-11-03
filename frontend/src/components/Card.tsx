import { useId, type ReactNode } from 'react'
import { CARD } from './ui'

/** A titled panel: the dashboard's sections and the parts of a detail page. */
export function Card({
  title,
  action,
  children,
  className = '',
}: {
  title?: string
  action?: ReactNode
  children: ReactNode
  className?: string
}) {
  const id = useId()
  return (
    <section
      aria-labelledby={title ? id : undefined}
      className={`${CARD} p-4 sm:p-5 ${className}`}
    >
      {title && (
        <div className="mb-4 flex items-center justify-between gap-3">
          <h2 id={id} className="text-base font-semibold">
            {title}
          </h2>
          {action && <div className="text-sm">{action}</div>}
        </div>
      )}
      {children}
    </section>
  )
}
