import type { ProblemResponse, SessionResponse } from './types'

/**
 * The one way the SPA calls the API. It adds the access token, turns error responses into
 * {@link ApiError}, and on a 401 tries one silent refresh before giving up (UI-1).
 */

/** An error response, with the Problem Details body when the server sent one (15.1). */
export class ApiError extends Error {
  readonly status: number
  readonly problem?: ProblemResponse

  constructor(status: number, problem?: ProblemResponse) {
    super(problem?.detail ?? `The request failed with status ${status}.`)
    this.name = 'ApiError'
    this.status = status
    this.problem = problem
  }

  get code(): string | undefined {
    return this.problem?.code
  }
}

/** Kept in memory only, never in localStorage or a cookie that scripts can read (AUTH-6). */
let accessToken: string | null = null
/**
 * Whose access token it is. The browser keeps one refresh cookie for every tab, so after someone
 * else signs in in another tab, a refresh here returns their session, not this tab's.
 */
let sessionUserId: string | null = null
let sessionEndedListener: (() => void) | null = null
let refreshInFlight: Promise<SessionResponse | null> | null = null

/** Starts using a session from sign-in or refresh, or forgets the current one with null. */
export function setSession(session: SessionResponse | null): void {
  accessToken = session?.accessToken ?? null
  sessionUserId = session?.user.id ?? null
}

/** Called when a request needed a session and none could be restored. */
export function onSessionEnded(listener: (() => void) | null): void {
  sessionEndedListener = listener
}

/** Endpoints that never carry the bearer token and never trigger a refresh. */
const AUTH_ENDPOINTS = [
  '/api/v1/auth/login',
  '/api/v1/auth/register',
  '/api/v1/auth/refresh',
  '/api/v1/auth/logout',
]

/**
 * Exchanges the refresh cookie for a new access token. Calls that overlap share one request, and
 * across tabs the Web Locks API lets only one tab refresh at a time: two requests with the same
 * cookie at once look like a stolen token to the server, which then ends the sign-in (ADR-020).
 *
 * A refresh that returns a different user than the one this tab signed in as ends the session
 * instead: carrying on would act as that user while the page still shows the previous one and
 * their cached data (UI-1).
 */
export function refreshSession(): Promise<SessionResponse | null> {
  if (!refreshInFlight) {
    refreshInFlight = oneTabAtATime(async () => {
      const response = await fetch('/api/v1/auth/refresh', {
        method: 'POST',
        headers: { Accept: 'application/json' },
      })
      if (!response.ok) {
        setSession(null)
        return null
      }
      const session = (await response.json()) as SessionResponse
      if (sessionUserId !== null && session.user.id !== sessionUserId) {
        setSession(null)
        return null
      }
      setSession(session)
      return session
    }).finally(() => {
      refreshInFlight = null
    })
  }
  return refreshInFlight
}

function oneTabAtATime<T>(task: () => Promise<T>): Promise<T> {
  const locks = typeof navigator === 'undefined' ? undefined : navigator.locks
  return locks ? locks.request('knowledge-platform-refresh', task) : task()
}

export interface ApiRequest {
  method?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE'
  /** Sent as JSON. */
  body?: unknown
  signal?: AbortSignal
}

export async function api<T>(
  path: string,
  request: ApiRequest = {},
): Promise<T> {
  const isAuthEndpoint = AUTH_ENDPOINTS.includes(path)
  let response = await send(path, request, !isAuthEndpoint)
  if (response.status === 401 && !isAuthEndpoint) {
    const session = await refreshSession()
    if (session) {
      response = await send(path, request, true)
    }
    if (response.status === 401) {
      setSession(null)
      sessionEndedListener?.()
    }
  }
  if (!response.ok) {
    throw new ApiError(response.status, await problemOf(response))
  }
  if (response.status === 204) {
    return undefined as T
  }
  return (await response.json()) as T
}

function send(
  path: string,
  request: ApiRequest,
  withToken: boolean,
): Promise<Response> {
  const headers: Record<string, string> = { Accept: 'application/json' }
  if (request.body !== undefined) {
    headers['Content-Type'] = 'application/json'
  }
  if (withToken && accessToken) {
    headers.Authorization = `Bearer ${accessToken}`
  }
  return fetch(path, {
    method: request.method ?? 'GET',
    headers,
    body: request.body === undefined ? undefined : JSON.stringify(request.body),
    signal: request.signal,
  })
}

async function problemOf(
  response: Response,
): Promise<ProblemResponse | undefined> {
  const type = response.headers.get('Content-Type') ?? ''
  if (!type.includes('json')) {
    return undefined
  }
  try {
    return (await response.json()) as ProblemResponse
  } catch {
    return undefined
  }
}
