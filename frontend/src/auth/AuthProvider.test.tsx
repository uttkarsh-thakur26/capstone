import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { act, render, screen } from '@testing-library/react'
import { StrictMode } from 'react'
import { describe, expect, it } from 'vitest'
import { api } from '../api/client'
import {
  fakeApi,
  json,
  noContent,
  OTHER_USER_ID,
  problem,
  session,
} from '../test/fakeApi'
import { AuthProvider } from './AuthProvider'
import type { Auth } from './authContext'
import { useAuth } from './useAuth'

function renderProvider(queryClient = new QueryClient()) {
  let auth: Auth | undefined
  function Probe() {
    auth = useAuth()
    return (
      <p data-testid="status">
        {auth.state.status}
        {auth.state.status === 'authenticated' && ` ${auth.state.user.role}`}
      </p>
    )
  }
  render(
    <StrictMode>
      <QueryClientProvider client={queryClient}>
        <AuthProvider>
          <Probe />
        </AuthProvider>
      </QueryClientProvider>
    </StrictMode>,
  )
  return { auth: () => auth!, queryClient }
}

describe('AuthProvider', () => {
  it('restores the session from the refresh cookie when the app starts, with one request', async () => {
    const server = fakeApi({
      'POST /api/v1/auth/refresh': () => json(session('FACULTY')),
    })

    renderProvider()

    expect(await screen.findByText('authenticated FACULTY')).toBeVisible()
    // StrictMode runs the effect twice; the refresh is shared, so the cookie is used once.
    expect(server.to('POST', '/api/v1/auth/refresh')).toHaveLength(1)
  })

  it('is anonymous when there is no session to restore', async () => {
    fakeApi({
      'POST /api/v1/auth/refresh': () =>
        problem(401, 'SESSION_EXPIRED', 'The session has ended.'),
    })

    renderProvider()

    expect(await screen.findByText('anonymous')).toBeVisible()
  })

  it('signs in and then sends the new access token', async () => {
    const server = fakeApi({
      'POST /api/v1/auth/refresh': () =>
        problem(401, 'SESSION_EXPIRED', 'The session has ended.'),
      'POST /api/v1/auth/login': () => json(session('ADMIN', 'admin-token')),
      'GET /api/v1/auth/me': () => json({}),
    })
    const { auth } = renderProvider()
    await screen.findByText('anonymous')

    await act(() => auth().login('admin@example.org', 'a long password'))
    await api('/api/v1/auth/me')

    expect(screen.getByText('authenticated ADMIN')).toBeVisible()
    expect(server.to('POST', '/api/v1/auth/login')[0].body).toEqual({
      email: 'admin@example.org',
      password: 'a long password',
    })
    expect(
      server.to('GET', '/api/v1/auth/me')[0].headers.get('Authorization'),
    ).toBe('Bearer admin-token')
  })

  it('ends the session when a silent refresh comes back as another user', async () => {
    let refreshes = 0
    const server = fakeApi({
      // The first refresh restores this tab's session. By the second, someone else has signed
      // in in another tab, and the browser's one refresh cookie now belongs to them.
      'POST /api/v1/auth/refresh': () => {
        refreshes += 1
        return json(
          refreshes === 1
            ? session('STUDENT')
            : session('ADMIN', 'other-token', { id: OTHER_USER_ID }),
        )
      },
      'GET /api/v1/auth/me': (request) =>
        request.headers.get('Authorization') === 'Bearer other-token'
          ? json({})
          : problem(401, 'UNAUTHENTICATED', 'Authentication is required.'),
    })
    const { queryClient } = renderProvider()
    await screen.findByText('authenticated STUDENT')
    queryClient.setQueryData(['private'], 'data of the student')

    await act(() => api('/api/v1/auth/me').catch(() => undefined))

    expect(screen.getByText('anonymous')).toBeVisible()
    expect(queryClient.getQueryData(['private'])).toBeUndefined()
    expect(
      server
        .to('GET', '/api/v1/auth/me')
        .map((request) => request.headers.get('Authorization')),
    ).not.toContain('Bearer other-token')
  })

  it('signs out on the server and drops every cached query', async () => {
    const server = fakeApi({
      'POST /api/v1/auth/refresh': () => json(session('STUDENT')),
      'POST /api/v1/auth/logout': () => noContent(),
    })
    const { auth, queryClient } = renderProvider()
    await screen.findByText('authenticated STUDENT')
    queryClient.setQueryData(['private'], 'data of the signed-in user')

    await act(() => auth().logout())

    expect(screen.getByText('anonymous')).toBeVisible()
    expect(server.to('POST', '/api/v1/auth/logout')).toHaveLength(1)
    expect(queryClient.getQueryData(['private'])).toBeUndefined()
  })
})
