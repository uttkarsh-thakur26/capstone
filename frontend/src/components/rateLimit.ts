import { useEffect, useState } from 'react'
import { ApiError } from '../api/client'

/**
 * The 429 of a rate limit (SEC-8, ADR-041): the server says why and in how many seconds the next
 * request is accepted (`retryAfterSeconds`). The screens count that down, keep the button off
 * until it reaches zero, and then say the request may be tried again.
 */

/** Seconds to wait, when this error is a rate limit; null for any other error. */
export function retryAfterOf(error: unknown): number | null {
  if (
    error instanceof ApiError &&
    error.status === 429 &&
    typeof error.problem?.retryAfterSeconds === 'number'
  ) {
    return Math.max(0, Math.ceil(error.problem.retryAfterSeconds))
  }
  return null
}

/** The wait in words, like the server writes it: seconds, then minutes, then hours. */
export function waitInWords(seconds: number): string {
  if (seconds < 120) {
    return `${seconds} ${seconds === 1 ? 'second' : 'seconds'}`
  }
  const minutes = Math.ceil(seconds / 60)
  if (minutes < 120) {
    return `${minutes} minutes`
  }
  return `${Math.ceil(minutes / 60)} hours`
}

/**
 * The server's reason with the wait that is left, for example "Too many requests. Try again in 12
 * seconds."; once the wait is over, the reason with "You can try again now."
 */
export function rateLimitText(error: unknown, secondsLeft: number): string {
  const detail = error instanceof ApiError ? error.problem?.detail : undefined
  const reason =
    detail?.split(' Try again in ')[0]?.trim() || 'Too many requests.'
  return secondsLeft > 0
    ? `${reason} Try again in ${waitInWords(secondsLeft)}.`
    : `${reason} You can try again now.`
}

/**
 * Counts down the wait of a rate-limit error, a second at a time. Null when the error is not a rate
 * limit; 0 once the wait is over.
 */
export function useRetryCountdown(error: unknown): number | null {
  const total = retryAfterOf(error)
  const [left, setLeft] = useState<{ error: unknown; seconds: number } | null>(
    null,
  )

  useEffect(() => {
    if (total === null || total === 0) return
    const deadline = Date.now() + total * 1000
    const timer = window.setInterval(() => {
      const seconds = Math.max(0, Math.ceil((deadline - Date.now()) / 1000))
      setLeft({ error, seconds })
      if (seconds === 0) window.clearInterval(timer)
    }, 1000)
    return () => window.clearInterval(timer)
  }, [error, total])

  if (total === null) return null
  return left !== null && left.error === error ? left.seconds : total
}
