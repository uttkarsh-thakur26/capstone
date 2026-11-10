/**
 * The class names every screen shares (UI-9), so a field, a card or a link looks the same
 * everywhere. Colors come from the tokens in index.css.
 */

/** Focus ring for React Aria components, which mark keyboard focus with data-focus-visible. */
export const FOCUS_RING =
  'outline-none data-[focus-visible]:outline-2 data-[focus-visible]:outline-offset-2 data-[focus-visible]:outline-focus'

/** Focus ring for plain elements such as router links. */
export const FOCUS_RING_NATIVE =
  'outline-none focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-focus'

/** Text inputs and text areas. 16 px on phones, so iOS does not zoom in on focus. */
export const FIELD =
  'w-full rounded-md border border-line-strong bg-surface px-3 py-2 text-base text-fg placeholder:text-muted outline-none data-[focused]:outline-2 data-[focused]:-outline-offset-1 data-[focused]:outline-focus data-[invalid]:border-red-600 data-[disabled]:opacity-50 sm:text-sm'

export const LABEL = 'text-sm font-medium'

export const HINT = 'text-sm text-muted'

export const FIELD_ERROR = 'text-sm text-red-700 dark:text-red-400'

export const LINK = `rounded-sm font-medium text-link underline-offset-2 hover:underline ${FOCUS_RING_NATIVE}`

export const CARD = 'rounded-lg border border-line bg-surface'

/** The button of a select, styled like a field. */
export const SELECT_TRIGGER = `flex min-w-44 items-center justify-between gap-2 rounded-md border border-line-strong bg-surface px-3 py-2 text-sm text-fg data-[disabled]:opacity-50 ${FOCUS_RING}`

/** The chosen value inside a select's button: one line, from the left, cut with an ellipsis. */
export const SELECT_VALUE = 'min-w-0 flex-1 truncate text-left'

/**
 * The list of a select or combo box. A long list, such as the 22 audit actions, scrolls inside the
 * popover instead of running off the screen: at most 18rem, and never taller than what is visible.
 */
export const POPOVER =
  'min-w-(--trigger-width) max-h-[min(18rem,calc(var(--visual-viewport-height,100dvh)-2rem))] overflow-y-auto overscroll-contain rounded-md border border-line bg-surface shadow-lg'

export const LIST_ITEM =
  'cursor-default rounded px-3 py-1.5 text-sm outline-none data-[focused]:bg-accent-soft data-[selected]:font-semibold'

export const MODAL_OVERLAY =
  'fixed inset-0 z-30 flex items-center justify-center bg-slate-950/50 p-4'

export const MODAL =
  'w-full max-w-md rounded-lg border border-line bg-surface p-6 text-fg shadow-xl'

/** Data tables: the header row and each body row. */
export const TABLE_HEAD =
  'border-b border-line text-xs font-medium tracking-wide text-muted uppercase'

export const TABLE_ROW = 'border-b border-line last:border-0'
