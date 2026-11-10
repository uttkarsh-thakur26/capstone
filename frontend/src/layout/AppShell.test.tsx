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
      screen.queryByRole('link', { name: 'Upload' }),
    ).not.toBeInTheDocument()
    expect(
      within(screen.getByRole('complementary')).getByText('Student'),
    ).toBeVisible()
  })

  it('offers faculty the upload, without user administration', async () => {
    signedInAs('FACULTY')

    renderApp('/')

    const nav = await screen.findByRole('navigation', { name: 'Main' })
    expect(screen.getByRole('link', { name: 'Upload' })).toHaveAttribute(
      'href',
      '/documents/upload',
    )
    expect(
      within(nav).queryByRole('link', { name: 'Users' }),
    ).not.toBeInTheDocument()
  })

  it('opens the menu as a dialog on small screens, closes it with Escape and returns focus', async () => {
    signedInAs('STUDENT')
    const { user } = renderApp('/')

    const open = await screen.findByRole('button', { name: 'Open menu' })
    await user.click(open)
    const menu = await screen.findByRole('dialog', { name: 'Menu' })
    expect(
      within(menu).getByRole('link', { name: 'Conversations' }),
    ).toBeVisible()

    await user.keyboard('{Escape}')

    await vi.waitFor(() =>
      expect(
        screen.queryByRole('dialog', { name: 'Menu' }),
      ).not.toBeInTheDocument(),
    )
    // React Aria hands focus back on the next animation frame.
    await vi.waitFor(() => expect(open).toHaveFocus())
  })

  it('closes the menu when a link in it is followed', async () => {
    signedInAs('STUDENT')
    const { user, router } = renderApp('/')

    await user.click(await screen.findByRole('button', { name: 'Open menu' }))
    const menu = await screen.findByRole('dialog', { name: 'Menu' })
    await user.click(within(menu).getByRole('link', { name: 'Assistant' }))

    // The page is a lazy route: the address changes once its code is loaded.
    await vi.waitFor(() =>
      expect(router.state.location.pathname).toBe('/assistant'),
    )
    await vi.waitFor(() =>
      expect(
        screen.queryByRole('dialog', { name: 'Menu' }),
      ).not.toBeInTheDocument(),
    )
  })

  it('switches to the dark theme and remembers it', async () => {
    signedInAs('STUDENT')
    const { user } = renderApp('/')

    const sidebar = await screen.findByRole('complementary')
    await user.click(within(sidebar).getByRole('radio', { name: 'Dark' }))

    expect(document.documentElement.dataset.theme).toBe('dark')
    expect(window.localStorage.getItem('theme')).toBe('dark')

    await user.click(within(sidebar).getByRole('radio', { name: 'System' }))

    expect(document.documentElement.dataset.theme).toBe('light')
    expect(window.localStorage.getItem('theme')).toBeNull()
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

  it('starts the next person at home, not on the page the previous user had open', async () => {
    fakeApi({
      'GET /api/v1/meta': () => json(META),
      'POST /api/v1/auth/refresh': () => json(session('ADMIN')),
      'POST /api/v1/auth/logout': () => noContent(),
      'GET /api/v1/admin/users': () =>
        json({ items: [], page: 0, size: 20, totalItems: 0, totalPages: 0 }),
      'POST /api/v1/auth/login': () => json(session('FACULTY')),
    })
    const { user, router } = renderApp('/admin/users')

    await user.click(await screen.findByRole('button', { name: 'Sign out' }))
    await user.type(
      await screen.findByLabelText('Email'),
      'faculty@example.org',
    )
    await user.type(screen.getByLabelText('Password'), 'correct horse battery')
    await user.click(screen.getByRole('button', { name: 'Sign in' }))

    expect(
      await screen.findByRole('heading', { name: 'Welcome, Test faculty' }),
    ).toBeVisible()
    expect(router.state.location.pathname).toBe('/')
  })

  it('shows the not-found page for an unknown address', async () => {
    signedInAs('STUDENT')

    renderApp('/nowhere')

    expect(
      await screen.findByRole('heading', { name: 'Page not found' }),
    ).toBeVisible()
  })

  describe('sidebar on large screens', () => {
    it('hides for reading room, comes back on the edge, and stays open again with its button', async () => {
      window.localStorage.removeItem('sidebar')
      signedInAs('STUDENT')
      const { user } = renderApp('/')
      const sidebar = await screen.findByRole('complementary', {
        name: 'Sidebar',
      })
      const content = screen.getByRole('main').parentElement!
      expect(content).toHaveClass('lg:pl-64')

      const toggle = screen.getByRole('button', { name: 'Hide sidebar' })
      // The button is not part of the sidebar: it stays in one place when the sidebar moves.
      expect(sidebar).not.toContainElement(toggle)
      await user.click(toggle)

      expect(sidebar).toHaveClass('-translate-x-full')
      // Off the screen, it is out of the tab order and hidden from screen readers.
      expect(sidebar).toHaveAttribute('inert')
      expect(content).not.toHaveClass('lg:pl-64')
      expect(window.localStorage.getItem('sidebar')).toBe('hidden')

      const show = screen.getByRole('button', { name: 'Keep sidebar open' })
      expect(show).toBe(toggle)
      // Right after Hide the pointer is still on the button; it comes back to it later.
      await user.hover(screen.getByRole('main'))
      await user.hover(show)
      expect(sidebar).toHaveClass('translate-x-0', 'shadow-xl')
      expect(content).not.toHaveClass('lg:pl-64')
      // Leaving hides it again. Moving from the button onto the sidebar keeps it: jsdom cannot
      // show that, so the Playwright test of the admin path checks it in Chromium.
      await user.hover(screen.getByRole('main'))
      expect(sidebar).toHaveClass('-translate-x-full')

      await user.click(show)
      expect(sidebar).toHaveClass('translate-x-0')
      expect(sidebar).not.toHaveAttribute('inert')
      expect(content).toHaveClass('lg:pl-64')
      expect(window.localStorage.getItem('sidebar')).toBe('shown')
    })

    it('starts hidden when it was hidden before', async () => {
      window.localStorage.setItem('sidebar', 'hidden')
      signedInAs('STUDENT')
      renderApp('/')

      const sidebar = await screen.findByRole('complementary', {
        hidden: true,
      })
      expect(sidebar).toHaveAttribute('inert')
      window.localStorage.removeItem('sidebar')
    })
  })
})
