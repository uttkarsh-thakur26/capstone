import type { ReactNode } from 'react'
import { Button } from './Button'

/** Previous and next for a paged list, with where the user is in words between them. */
export function Pager({
  page,
  totalPages,
  onPage,
  children,
}: {
  page: number
  totalPages: number
  onPage: (page: number) => void
  children: ReactNode
}) {
  return (
    <nav
      aria-label="Pages"
      className="flex flex-wrap items-center gap-3 text-sm text-muted"
    >
      <Button
        variant="secondary"
        size="sm"
        isDisabled={page === 0}
        onPress={() => onPage(page - 1)}
      >
        Previous
      </Button>
      <span>{children}</span>
      <Button
        variant="secondary"
        size="sm"
        isDisabled={page + 1 >= totalPages}
        onPress={() => onPage(page + 1)}
      >
        Next
      </Button>
    </nav>
  )
}
