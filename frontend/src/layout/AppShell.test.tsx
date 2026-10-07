import { screen, within } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { fakeApi, json, META, noContent, session } from '../test/fakeApi'
import { renderApp } from '../test/renderApp'

function signedInAs(role: 'ADMIN' | 'FACULTY' | 'STUDENT') {
  return fakeApi({
    'GET /api/v1/meta': () => json(META),
    'POST /api/v1/auth/refresh': () => json(session(role)),
    'POST /api/v1/auth/logout': () => noContent(),
  })
}

describe('AppShell', () => {
  it('shows a student the student navigation, without user administration', async () => {
    signedInAs('STUDENT')

    renderApp('/')

    const nav = await screen.findByRole('navigation', { name: 'Main' })
    expect(within(nav).getByRole('link', { name: 'Home' })).toBeVisible()
    expect(
      within(nav).queryByRole('link', { name: 'Users' }),
    ).not.toBeInTheDocument()
    expect(
      within(screen.getByRole('banner')).getByText('STUDENT'),
    ).toBeVisible()
  })

  it('shows an admin the link to user administration', async () => {
    signedInAs('ADMIN')

    renderApp('/')

    const nav = await screen.findByRole('navigation', { name: 'Main' })
    expect(within(nav).getByRole('link', { name: 'Users' })).toHaveAttribute(
      'href',
      '/admin/users',
    )
  })

  it('signs out on the server and returns to sign-in', async () => {
    const server = signedInAs('FACULTY')
    const { user, router } = renderApp('/')
    await screen.findByText('Version 1.2.3')
    expect(server.to('GET', '/api/v1/meta')).toHaveLength(1)

    await user.click(await screen.findByRole('button', { name: 'Sign out' }))

    expect(
      await screen.findByRole('heading', { name: 'Sign in' }),
    ).toBeVisible()
    expect(router.state.location.pathname).toBe('/login')
    expect(server.to('POST', '/api/v1/auth/logout')).toHaveLength(1)
    // The product name, cached forever otherwise, is fetched again: the cache was cleared (UI-1).
    await vi.waitFor(() =>
      expect(server.to('GET', '/api/v1/meta')).toHaveLength(2),
    )
  })

  it('shows the not-found page for an unknown address', async () => {
    signedInAs('STUDENT')

    renderApp('/nowhere')

    expect(
      await screen.findByRole('heading', { name: 'Page not found' }),
    ).toBeVisible()
  })
})
