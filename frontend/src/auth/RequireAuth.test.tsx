import { render, screen } from '@testing-library/react'
import { createMemoryRouter, RouterProvider, useLocation } from 'react-router'
import { describe, expect, it } from 'vitest'
import { user } from '../test/fakeApi'
import { AuthContext, type Auth, type AuthState } from './authContext'
import { RequireAuth } from './RequireAuth'

function LoginStub() {
  const location = useLocation()
  const from = (location.state as { from?: { pathname: string } } | null)?.from
  return <p>Sign-in page, return to {from?.pathname}</p>
}

function renderGuarded(state: AuthState) {
  const auth: Auth = {
    state,
    login: () => Promise.reject(new Error('unused')),
    register: () => Promise.reject(new Error('unused')),
    logout: () => Promise.resolve(),
  }
  const router = createMemoryRouter(
    [
      { path: '/login', element: <LoginStub /> },
      {
        path: '/admin/users',
        element: (
          <RequireAuth roles={['ADMIN']}>
            <p>Admin content</p>
          </RequireAuth>
        ),
      },
    ],
    { initialEntries: ['/admin/users'] },
  )
  render(
    <AuthContext.Provider value={auth}>
      <RouterProvider router={router} />
    </AuthContext.Provider>,
  )
}

describe('RequireAuth', () => {
  it('sends an anonymous visitor to sign-in and remembers where they wanted to go', () => {
    renderGuarded({ status: 'anonymous' })

    expect(
      screen.getByText('Sign-in page, return to /admin/users'),
    ).toBeVisible()
    expect(screen.queryByText('Admin content')).not.toBeInTheDocument()
  })

  it('does not carry a return address across a deliberate sign-out', () => {
    renderGuarded({ status: 'anonymous', signedOut: true })

    expect(screen.getByText(/^Sign-in page, return to\s*$/)).toBeVisible()
  })

  it('waits while the session is being restored', () => {
    renderGuarded({ status: 'loading' })

    expect(screen.getByRole('status')).toHaveTextContent('Loading')
    expect(screen.queryByText('Admin content')).not.toBeInTheDocument()
  })

  it('shows the not-allowed page to a signed-in user without the role', () => {
    renderGuarded({ status: 'authenticated', user: user('FACULTY') })

    expect(screen.getByRole('heading', { name: 'Not allowed' })).toBeVisible()
    expect(screen.queryByText('Admin content')).not.toBeInTheDocument()
  })

  it('shows the page to a user with the role', () => {
    renderGuarded({ status: 'authenticated', user: user('ADMIN') })

    expect(screen.getByText('Admin content')).toBeVisible()
  })
})
