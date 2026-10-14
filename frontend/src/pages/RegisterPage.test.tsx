import { screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { fakeApi, json, META, problem, user as account } from '../test/fakeApi'
import { renderApp } from '../test/renderApp'

function signedOutApi(extra = {}) {
  return fakeApi({
    'GET /api/v1/meta': () => json(META),
    'POST /api/v1/auth/refresh': () =>
      problem(401, 'SESSION_EXPIRED', 'The session has ended.'),
    ...extra,
  })
}

async function fill(
  user: ReturnType<typeof renderApp>['user'],
  values: { email: string; name: string; password: string },
) {
  await user.type(await screen.findByLabelText('Email'), values.email)
  await user.type(screen.getByLabelText('Name'), values.name)
  await user.type(screen.getByLabelText(/^Password/), values.password)
  await user.click(screen.getByRole('button', { name: 'Create account' }))
}

describe('RegisterPage', () => {
  it('registers and then asks the new user to sign in', async () => {
    const server = signedOutApi({
      'POST /api/v1/auth/register': () =>
        json(account('STUDENT', { email: 'new@example.org' }), 201),
    })
    const { user, router } = renderApp('/register')

    await fill(user, {
      email: 'new@example.org',
      name: 'New Student',
      password: 'correct horse battery',
    })

    expect(await screen.findByRole('status')).toHaveTextContent(
      'Your account is ready',
    )
    expect(router.state.location.pathname).toBe('/login')
    expect(screen.getByLabelText('Email')).toHaveValue('new@example.org')
    // The form never sends a role: registration always creates a student (AUTH-1).
    expect(server.to('POST', '/api/v1/auth/register')[0].body).toEqual({
      email: 'new@example.org',
      displayName: 'New Student',
      password: 'correct horse battery',
    })
  })

  it('counts password length in characters, as the server does', async () => {
    const server = signedOutApi()
    const { user } = renderApp('/register')

    // Nine emoji are eighteen UTF-16 units but only nine characters.
    await fill(user, {
      email: 'a@example.org',
      name: 'A',
      password: '\u{1F600}'.repeat(9),
    })

    expect(await screen.findByText('Use 10 to 128 characters.')).toBeVisible()
    expect(server.to('POST', '/api/v1/auth/register')).toHaveLength(0)
  })

  it('shows a taken email next to the email field', async () => {
    signedOutApi({
      'POST /api/v1/auth/register': () =>
        problem(
          409,
          'EMAIL_TAKEN',
          'An account with this email already exists.',
        ),
    })
    const { user } = renderApp('/register')

    await fill(user, {
      email: 'taken@example.org',
      name: 'Someone',
      password: 'correct horse battery',
    })

    expect(
      await screen.findByText('An account with this email already exists.'),
    ).toBeVisible()
    expect(screen.getByLabelText('Email')).toHaveAttribute(
      'aria-invalid',
      'true',
    )
  })

  it('places the field errors of a validation problem on their fields', async () => {
    signedOutApi({
      'POST /api/v1/auth/register': () =>
        problem(400, 'VALIDATION_FAILED', 'The request is not valid.', [
          { field: 'displayName', message: 'size must be between 0 and 100' },
        ]),
    })
    const { user } = renderApp('/register')

    await fill(user, {
      email: 'a@example.org',
      name: 'A',
      password: 'correct horse battery',
    })

    expect(
      await screen.findByText('size must be between 0 and 100'),
    ).toBeVisible()
    expect(screen.getByLabelText('Name')).toHaveAttribute(
      'aria-invalid',
      'true',
    )
  })
})
