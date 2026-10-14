import { screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { fakeApi, json, META, problem } from './test/fakeApi'
import { renderApp } from './test/renderApp'

const NO_SESSION = () =>
  problem(401, 'SESSION_EXPIRED', 'The session has ended.')

describe('App', () => {
  it('shows the name and version that /api/v1/meta reports', async () => {
    fakeApi({
      'GET /api/v1/meta': () => json(META),
      'POST /api/v1/auth/refresh': NO_SESSION,
    })

    renderApp('/login')

    expect(await screen.findByText('Test platform')).toBeVisible()
    expect(await screen.findByText('Version 1.2.3')).toBeVisible()
    expect(document.title).toBe('Test platform')
  })

  it('shows a loading state until the name arrives', async () => {
    fakeApi({
      'GET /api/v1/meta': () => new Promise<Response>(() => {}),
      'POST /api/v1/auth/refresh': NO_SESSION,
    })

    renderApp('/login')

    expect(
      await screen.findByRole('heading', { name: 'Sign in' }),
    ).toBeVisible()
    expect(screen.getByText('Loading…')).toBeVisible()
  })

  it('says so when the backend cannot be reached', async () => {
    fakeApi({
      'GET /api/v1/meta': () => new Response(null, { status: 503 }),
      'POST /api/v1/auth/refresh': NO_SESSION,
    })

    renderApp('/login')

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'The server could not be reached',
    )
  })

  it('sends a visitor without a session to sign-in', async () => {
    fakeApi({
      'GET /api/v1/meta': () => json(META),
      'POST /api/v1/auth/refresh': NO_SESSION,
    })

    const { router } = renderApp('/')

    expect(
      await screen.findByRole('heading', { name: 'Sign in' }),
    ).toBeVisible()
    expect(router.state.location.pathname).toBe('/login')
  })
})
