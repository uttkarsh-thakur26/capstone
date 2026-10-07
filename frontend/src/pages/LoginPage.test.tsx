import { screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { fakeApi, json, META, problem, session } from '../test/fakeApi'
import { renderApp } from '../test/renderApp'

function signedOutApi(extra = {}) {
  return fakeApi({
    'GET /api/v1/meta': () => json(META),
    'POST /api/v1/auth/refresh': () =>
      problem(401, 'SESSION_EXPIRED', 'The session has ended.'),
    ...extra,
  })
}

describe('LoginPage', () => {
  it('checks the fields before calling the server', async () => {
    const server = signedOutApi()
    const { user } = renderApp('/login')

    await user.click(await screen.findByRole('button', { name: 'Sign in' }))

    expect(await screen.findByText('Enter your email.')).toBeVisible()
    expect(screen.getByText('Enter your password.')).toBeVisible()
    expect(screen.getByLabelText('Email')).toHaveAttribute(
      'aria-invalid',
      'true',
    )
    expect(server.to('POST', '/api/v1/auth/login')).toHaveLength(0)
  })

  it('signs in and opens the role-aware shell', async () => {
    const server = signedOutApi({
      'POST /api/v1/auth/login': () => json(session('STUDENT')),
    })
    const { user, router } = renderApp('/login')

    await user.type(
      await screen.findByLabelText('Email'),
      ' student@example.org ',
    )
    await user.type(screen.getByLabelText('Password'), 'correct horse battery')
    await user.click(screen.getByRole('button', { name: 'Sign in' }))

    expect(
      await screen.findByRole('heading', { name: 'Welcome, Test student' }),
    ).toBeVisible()
    expect(router.state.location.pathname).toBe('/')
    expect(server.to('POST', '/api/v1/auth/login')[0].body).toEqual({
      email: 'student@example.org',
      password: 'correct horse battery',
    })
  })

  it('shows the one generic message the server gives for any failed sign-in', async () => {
    signedOutApi({
      'POST /api/v1/auth/login': () =>
        problem(
          401,
          'BAD_CREDENTIALS',
          'The email or password is not correct.',
        ),
    })
    const { user } = renderApp('/login')

    await user.type(await screen.findByLabelText('Email'), 'a@example.org')
    await user.type(screen.getByLabelText('Password'), 'wrong password')
    await user.click(screen.getByRole('button', { name: 'Sign in' }))

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'The email or password is not correct.',
    )
  })

  it('returns to the page the visitor first asked for', async () => {
    signedOutApi({
      'POST /api/v1/auth/login': () => json(session('STUDENT')),
    })
    const { user, router } = renderApp('/some/page')

    await user.type(await screen.findByLabelText('Email'), 'a@example.org')
    await user.type(screen.getByLabelText('Password'), 'correct horse battery')
    await user.click(screen.getByRole('button', { name: 'Sign in' }))

    expect(
      await screen.findByRole('heading', { name: 'Page not found' }),
    ).toBeVisible()
    expect(router.state.location.pathname).toBe('/some/page')
  })
})
