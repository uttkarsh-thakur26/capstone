import type { FieldPath, FieldValues, UseFormSetError } from 'react-hook-form'
import { ApiError } from '../api/client'

/**
 * Shows a server error on the form: field errors of a validation problem next to their fields,
 * anything else as one message above the form. Returns false if no field took an error.
 */
export function showServerErrors<T extends FieldValues>(
  error: unknown,
  fields: readonly FieldPath<T>[],
  setError: UseFormSetError<T>,
): boolean {
  if (!(error instanceof ApiError)) {
    return false
  }
  let shown = false
  for (const fieldError of error.problem?.errors ?? []) {
    const field = fields.find((name) => name === fieldError.field)
    if (field) {
      setError(field, { type: 'server', message: fieldError.message })
      shown = true
    }
  }
  return shown
}

/** The text for a failure that is not about one field. */
export function messageOf(error: unknown, fallback: string): string {
  if (error instanceof ApiError && error.problem?.detail) {
    return error.problem.detail
  }
  return fallback
}
