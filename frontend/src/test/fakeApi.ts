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
) {
  return {
    accessToken: token,
    tokenType: 'Bearer',
    expiresAt: '2026-10-07T12:15:00Z',
    user: user(role),
  }
}
