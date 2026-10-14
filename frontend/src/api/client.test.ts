import { afterEach, describe, expect, it, vi } from 'vitest'
import { fakeApi, json, OTHER_USER_ID, problem, session } from '../test/fakeApi'
import {
  api,
  ApiError,
  onSessionEnded,
  refreshSession,
  setSession,
} from './client'

afterEach(() => onSessionEnded(null))

describe('api client', () => {
  it('sends the access token to the API but never to the auth endpoints', async () => {
    const server = fakeApi({
      'GET /api/v1/auth/me': () => json({}),
      'POST /api/v1/auth/login': () => json(session()),
    })
    setSession(session('ADMIN', 'token-1'))

    await api('/api/v1/auth/me')
    await api('/api/v1/auth/login', { method: 'POST', body: {} })

    expect(server.requests[0].headers.get('Authorization')).toBe(
      'Bearer token-1',
    )
    expect(server.requests[1].headers.get('Authorization')).toBeNull()
  })

  it('refreshes once on a 401 and repeats the request with the new token', async () => {
    const server = fakeApi({
      'GET /api/v1/admin/users': (request) =>
        request.headers.get('Authorization') === 'Bearer fresh'
          ? json({ items: [] })
          : problem(401, 'UNAUTHENTICATED', 'Authentication is required.'),
      'POST /api/v1/auth/refresh': () => json(session('ADMIN', 'fresh')),
    })
    setSession(session('ADMIN', 'expired'))

    await expect(api('/api/v1/admin/users')).resolves.toEqual({ items: [] })

    expect(server.to('POST', '/api/v1/auth/refresh')).toHaveLength(1)
    expect(server.to('GET', '/api/v1/admin/users')).toHaveLength(2)
  })

  it('ends the session when the refresh fails', async () => {
    fakeApi({
      'GET /api/v1/auth/me': () =>
        problem(401, 'UNAUTHENTICATED', 'Authentication is required.'),
      'POST /api/v1/auth/refresh': () =>
        problem(401, 'SESSION_EXPIRED', 'The session has ended.'),
    })
    const ended = vi.fn()
    onSessionEnded(ended)
    setSession(session('ADMIN', 'expired'))

    const error = await api('/api/v1/auth/me').catch((e: unknown) => e)

    expect(error).toBeInstanceOf(ApiError)
    expect((error as ApiError).status).toBe(401)
    expect(ended).toHaveBeenCalledOnce()
  })

  it('ends the session when the refresh returns another user, and never sends their token', async () => {
    const server = fakeApi({
      'GET /api/v1/auth/me': () =>
        problem(401, 'UNAUTHENTICATED', 'Authentication is required.'),
      // Someone else signed in in another tab; the browser's one cookie is theirs now.
      'POST /api/v1/auth/refresh': () =>
        json(session('ADMIN', 'other-token', { id: OTHER_USER_ID })),
    })
    const ended = vi.fn()
    onSessionEnded(ended)
    setSession(session('STUDENT', 'expired'))

    const error = await api('/api/v1/auth/me').catch((e: unknown) => e)

    expect((error as ApiError).status).toBe(401)
    expect(ended).toHaveBeenCalledOnce()
    // The request is not repeated with the other user's token.
    expect(server.to('GET', '/api/v1/auth/me')).toHaveLength(1)
  })

  it('lets overlapping requests share one refresh', async () => {
    let release: () => void = () => {}
    const server = fakeApi({
      'GET /api/v1/auth/me': (request) =>
        request.headers.get('Authorization') === 'Bearer fresh'
          ? json({})
          : problem(401, 'UNAUTHENTICATED', 'Authentication is required.'),
      'POST /api/v1/auth/refresh': () =>
        new Promise((resolve) => {
          release = () => resolve(json(session('STUDENT', 'fresh')))
        }),
    })
    setSession(session('ADMIN', 'expired'))

    const both = Promise.all([api('/api/v1/auth/me'), api('/api/v1/auth/me')])
    await vi.waitFor(() =>
      expect(server.to('POST', '/api/v1/auth/refresh')).toHaveLength(1),
    )
    release()
    await both

    expect(server.to('POST', '/api/v1/auth/refresh')).toHaveLength(1)
  })

  it('serializes refreshes across tabs with a Web Lock when the browser has one', async () => {
    fakeApi({ 'POST /api/v1/auth/refresh': () => json(session()) })
    const request = vi.fn((_name: string, task: () => Promise<unknown>) =>
      task(),
    )
    vi.stubGlobal('navigator', { ...navigator, locks: { request } })

    await refreshSession()

    expect(request).toHaveBeenCalledWith(
      'knowledge-platform-refresh',
      expect.any(Function),
    )
  })

  it('turns an error response into an ApiError with its Problem Details', async () => {
    fakeApi({
      'PATCH /api/v1/admin/users/1': () =>
        problem(
          409,
          'LAST_ADMIN',
          'The last enabled admin cannot be demoted or disabled.',
        ),
    })

    const error = (await api('/api/v1/admin/users/1', {
      method: 'PATCH',
      body: { role: 'STUDENT' },
    }).catch((e: unknown) => e)) as ApiError

    expect(error.status).toBe(409)
    expect(error.code).toBe('LAST_ADMIN')
    expect(error.message).toBe(
      'The last enabled admin cannot be demoted or disabled.',
    )
  })
})
