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
let sessionEndedListener: (() => void) | null = null
let refreshInFlight: Promise<SessionResponse | null> | null = null

export function setAccessToken(token: string | null): void {
  accessToken = token
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
 */
export function refreshSession(): Promise<SessionResponse | null> {
  if (!refreshInFlight) {
    refreshInFlight = oneTabAtATime(async () => {
      const response = await fetch('/api/v1/auth/refresh', {
        method: 'POST',
        headers: { Accept: 'application/json' },
      })
      if (!response.ok) {
        accessToken = null
        return null
      }
      const session = (await response.json()) as SessionResponse
      accessToken = session.accessToken
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
      accessToken = null
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
