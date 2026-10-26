import { vi } from 'vitest'

/** A request the fake API received, with its JSON body parsed. */
export interface RecordedRequest {
  method: string
  path: string
  url: URL
  headers: Headers
  body: unknown
}

type Handler = (request: RecordedRequest) => Response | Promise<Response>

/**
 * Replaces `fetch` with handlers keyed by "METHOD /path" (no query string). Unknown routes answer
 * 404, so a test fails loudly when the code calls something unexpected.
 */
export function fakeApi(routes: Record<string, Handler>) {
  const requests: RecordedRequest[] = []
  const fetchMock = vi.fn(
    async (input: RequestInfo | URL, init: RequestInit = {}) => {
      const url = new URL(String(input), 'http://localhost:3000')
      const method = (init.method ?? 'GET').toUpperCase()
      const request: RecordedRequest = {
        method,
        path: url.pathname,
        url,
        headers: new Headers(init.headers),
        body: typeof init.body === 'string' ? JSON.parse(init.body) : undefined,
      }
      requests.push(request)
      const handler = routes[`${method} ${url.pathname}`]
      return handler
        ? handler(request)
        : problem(404, 'NOT_FOUND', `No fake for ${method} ${url.pathname}`)
    },
  )
  vi.stubGlobal('fetch', fetchMock)
  return {
    requests,
    to: (method: string, path: string) =>
      requests.filter((r) => r.method === method && r.path === path),
  }
}

/** An upload the fake XMLHttpRequest received. */
export interface RecordedUpload {
  path: string
  headers: Record<string, string>
  form: FormData
}

/**
 * Replaces XMLHttpRequest, which the client uses for uploads because it reports progress. Each
 * upload reports half of the form as sent, waits for the handler, then reports all of it.
 */
export function fakeUploads(
  handler: (upload: RecordedUpload) => Response | Promise<Response>,
) {
  const uploads: RecordedUpload[] = []
  type Listener = (event: {
    lengthComputable: boolean
    loaded: number
    total: number
  }) => void

  class FakeXhr {
    status = 0
    responseText = ''
    private path = ''
    private requestHeaders: Record<string, string> = {}
    private responseHeaders = new Headers()
    private listeners: Record<string, (() => void)[]> = {}
    private progress: Listener[] = []
    readonly upload = {
      addEventListener: (type: string, listener: Listener) => {
        if (type === 'progress') this.progress.push(listener)
      },
    }

    open(_method: string, path: string) {
      this.path = path
    }

    setRequestHeader(name: string, value: string) {
      this.requestHeaders[name] = value
    }

    getResponseHeader(name: string) {
      return this.responseHeaders.get(name)
    }

    addEventListener(type: string, listener: () => void) {
      ;(this.listeners[type] ??= []).push(listener)
    }

    abort() {
      this.listeners.abort?.forEach((listener) => listener())
    }

    async send(form: FormData) {
      const upload = { path: this.path, headers: this.requestHeaders, form }
      uploads.push(upload)
      this.progress.forEach((l) =>
        l({ lengthComputable: true, loaded: 1, total: 2 }),
      )
      const response = await handler(upload)
      this.status = response.status
      this.responseText = await response.text()
      this.responseHeaders = response.headers
      this.progress.forEach((l) =>
        l({ lengthComputable: true, loaded: 2, total: 2 }),
      )
      this.listeners.load?.forEach((listener) => listener())
    }
  }

  vi.stubGlobal('XMLHttpRequest', FakeXhr)
  return uploads
}

export function json(body: unknown, status = 200): Response {
  return Response.json(body, { status })
}

export function noContent(): Response {
  return new Response(null, { status: 204 })
}

export function problem(
  status: number,
  code: string,
  detail: string,
  errors?: { field: string; message: string }[],
): Response {
  return new Response(
    JSON.stringify({
      type: 'about:blank',
      title: 'Error',
      status,
      detail,
      code,
      errors,
    }),
    { status, headers: { 'Content-Type': 'application/problem+json' } },
  )
}

export const META = { name: 'Test platform', version: '1.2.3' }

export function user(
  role: 'ADMIN' | 'FACULTY' | 'STUDENT' = 'STUDENT',
  overrides: Partial<{ id: string; email: string; displayName: string }> = {},
) {
  return {
    id: overrides.id ?? '0199a1b2-0000-7000-8000-000000000001',
    email: overrides.email ?? `${role.toLowerCase()}@example.org`,
    displayName: overrides.displayName ?? `Test ${role.toLowerCase()}`,
    role,
  }
}

export function session(
  role: 'ADMIN' | 'FACULTY' | 'STUDENT' = 'STUDENT',
  token = `token-${role}`,
  userOverrides: Parameters<typeof user>[1] = {},
) {
  return {
    accessToken: token,
    tokenType: 'Bearer',
    expiresAt: '2026-10-07T12:15:00Z',
    user: user(role, userOverrides),
  }
}

/** The id of a second account, for sessions that must belong to someone else. */
export const OTHER_USER_ID = '0199a1b2-0000-7000-8000-000000000002'
