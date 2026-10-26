import type { DocumentStatus } from '../api/types'
import { STATUS_LABELS } from './format'

const TONES: Record<DocumentStatus, string> = {
  UPLOADED:
    'bg-slate-100 text-slate-800 ring-slate-300 dark:bg-slate-800 dark:text-slate-100 dark:ring-slate-600',
  PROCESSING:
    'bg-amber-50 text-amber-900 ring-amber-300 dark:bg-amber-950 dark:text-amber-100 dark:ring-amber-700',
  INDEXED:
    'bg-emerald-50 text-emerald-900 ring-emerald-300 dark:bg-emerald-950 dark:text-emerald-100 dark:ring-emerald-700',
  FAILED:
    'bg-red-50 text-red-900 ring-red-300 dark:bg-red-950 dark:text-red-100 dark:ring-red-700',
}

const BADGE =
  'inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium ring-1 ring-inset'

/** The processing status in words, never by color alone (UI-8). */
export function StatusBadge({ status }: { status: DocumentStatus }) {
  return (
    <span className={`${BADGE} ${TONES[status]}`}>{STATUS_LABELS[status]}</span>
  )
}

export function ArchivedBadge() {
  return (
    <span
      className={`${BADGE} bg-white text-slate-700 ring-slate-400 dark:bg-slate-900 dark:text-slate-200 dark:ring-slate-500`}
    >
      Archived
    </span>
  )
}
