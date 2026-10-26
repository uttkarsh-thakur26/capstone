import type { DocumentStatus } from '../api/types'

/** A file size in the units people read: bytes, KB or MB. */
export function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} bytes`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(0)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

/** A date-time from the API (UTC) in the reader's local time (ADR-016). */
export function formatDateTime(value: string): string {
  return new Date(value).toLocaleString(undefined, {
    dateStyle: 'medium',
    timeStyle: 'short',
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
