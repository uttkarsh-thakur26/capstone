import type { DocumentStatus } from '../api/types'

/** A file size in the units people read: bytes, KB or MB. */
export function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} bytes`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(0)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

/**
 * A date-time from the API, an instant in UTC written with its Z, in the reader's own time zone
 * (ADR-016). The zone is shown with it, such as "GMT+5:30", so a browser set to another zone is
 * plain to see (ADR-040).
 *
 * @param timeZone for tests; the browser's own zone when absent
 */
export function formatDateTime(value: string, timeZone?: string): string {
  return new Date(value).toLocaleString(undefined, {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
    hour: 'numeric',
    minute: '2-digit',
    timeZoneName: 'short',
    timeZone,
  })
}

/** A calendar date such as a document date, which has no time and no zone. */
export function formatDate(value: string): string {
  const [year, month, day] = value.split('-').map(Number)
  return new Date(year, month - 1, day).toLocaleDateString(undefined, {
    dateStyle: 'medium',
  })
}

/** What each status means to a reader (DOC-12). */
export const STATUS_LABELS: Record<DocumentStatus, string> = {
  UPLOADED: 'Waiting for processing',
  PROCESSING: 'Processing',
  INDEXED: 'Ready',
  FAILED: 'Failed',
}

/** How often a page asks again while a document is queued or processed (DOC-12, UI-4). */
export const STATUS_POLL_MS = 3000

/** Whether the document's status will still change without anyone doing anything. */
export function isInProgress(status: DocumentStatus | undefined): boolean {
  return status === 'UPLOADED' || status === 'PROCESSING'
}
