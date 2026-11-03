import type { ReactNode } from 'react'

/** The top of every page: one h1, an optional line under it, and the page's main actions. */
export function PageHeader({
  title,
  description,
  actions,
  above,
}: {
  title: ReactNode
  description?: ReactNode
  actions?: ReactNode
  /** A back link or breadcrumb shown above the title. */
  above?: ReactNode
}) {
  return (
    <div className="space-y-2">
      {above && <div className="text-sm">{above}</div>}
      <div className="flex flex-wrap items-start justify-between gap-x-6 gap-y-3">
        <div className="min-w-0 space-y-1">
          <h1 className="text-2xl font-semibold tracking-tight wrap-anywhere">
            {title}
          </h1>
          {description && (
            <p className="max-w-prose text-sm text-muted">{description}</p>
          )}
        </div>
        {actions && <div className="flex flex-wrap gap-3">{actions}</div>}
      </div>
    </div>
  )
}
