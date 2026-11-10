import { screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import type { AdminUserResponse } from '../api/types'
import {
  fakeApi,
  json,
  META,
  problem,
  session,
  type RecordedRequest,
} from '../test/fakeApi'
import { renderApp } from '../test/renderApp'

function account(
  name: string,
  role: AdminUserResponse['role'],
  enabled = true,
): AdminUserResponse {
  const slug = name.toLowerCase().replace(/\s+/g, '.')
  return {
    id: `id-${slug}`,
    email: `${slug}@example.org`,
    displayName: name,
    role,
    enabled,
    createdAt: '2026-10-01T08:00:00Z',
  }
}

function page(items: AdminUserResponse[]) {
  return {
    items,
    page: 0,
    size: 20,
    totalItems: items.length,
    totalPages: items.length ? 1 : 0,
  }
}

function adminApi(
  list: (request: RecordedRequest) => Response,
  extra: Record<
    string,
    (request: RecordedRequest) => Response | Promise<Response>
  > = {},
) {
  return fakeApi({
    'GET /api/v1/meta': () => json(META),
    'POST /api/v1/auth/refresh': () => json(session('ADMIN')),
    'GET /api/v1/admin/users': list,
    ...extra,
  })
}

describe('AdminUsersPage', () => {
  it('lists the users with their roles and account state', async () => {
    adminApi(() =>
      json(
        page([
          account('Ada Admin', 'ADMIN'),
          account('Sam Student', 'STUDENT'),
        ]),
      ),
    )

    renderApp('/admin/users')

    const rows = await screen.findAllByRole('row')
    expect(rows).toHaveLength(3)
    expect(within(rows[2]).getByText('sam.student@example.org')).toBeVisible()
    expect(
      within(rows[2]).getByRole('button', { name: /Role of Sam Student/ }),
    ).toHaveTextContent('STUDENT')
    expect(
      within(rows[2]).getByRole('switch', {
        name: 'Account of Sam Student enabled',
      }),
    ).toBeChecked()
    expect(screen.getByText('Page 1 of 1, 2 users')).toBeVisible()
  })

  it('promotes a student to faculty and reloads the list', async () => {
    let promoted = false
    const server = adminApi(
      () =>
        json(page([account('Sam Student', promoted ? 'FACULTY' : 'STUDENT')])),
      {
        'PATCH /api/v1/admin/users/id-sam.student': () => {
          promoted = true
          return json(account('Sam Student', 'FACULTY'))
        },
      },
    )
    const { user } = renderApp('/admin/users')

    await user.click(
      await screen.findByRole('button', { name: /Role of Sam Student/ }),
    )
    await user.click(await screen.findByRole('option', { name: 'FACULTY' }))

    expect(await screen.findByRole('status')).toHaveTextContent(
      'Sam Student is now FACULTY and enabled.',
    )
    expect(
      server.to('PATCH', '/api/v1/admin/users/id-sam.student')[0].body,
    ).toEqual({ role: 'FACULTY' })
    expect(
      await screen.findByRole('button', { name: /Role of Sam Student/ }),
    ).toHaveTextContent('FACULTY')
  })

  it('shows the server refusing to demote the last admin', async () => {
    adminApi(() => json(page([account('Ada Admin', 'ADMIN')])), {
      'PATCH /api/v1/admin/users/id-ada.admin': () =>
        problem(
          409,
          'LAST_ADMIN',
          'The last enabled admin cannot be demoted or disabled.',
        ),
    })
    const { user } = renderApp('/admin/users')

    await user.click(
      await screen.findByRole('button', { name: /Role of Ada Admin/ }),
    )
    await user.click(await screen.findByRole('option', { name: 'STUDENT' }))

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'The last enabled admin cannot be demoted or disabled.',
    )
  })

  it('disables an account with the switch', async () => {
    const server = adminApi(
      () => json(page([account('Sam Student', 'STUDENT')])),
      {
        'PATCH /api/v1/admin/users/id-sam.student': () =>
          json(account('Sam Student', 'STUDENT', false)),
      },
    )
    const { user } = renderApp('/admin/users')

    await user.click(
      await screen.findByRole('switch', {
        name: 'Account of Sam Student enabled',
      }),
    )

    expect(await screen.findByRole('status')).toHaveTextContent(
      'Sam Student is now STUDENT and disabled.',
    )
    expect(
      server.to('PATCH', '/api/v1/admin/users/id-sam.student')[0].body,
    ).toEqual({ enabled: false })
  })

  it('searches on the server and says when nothing matches', async () => {
    const server = adminApi((request) =>
      request.url.searchParams.get('query') === 'nobody'
        ? json(page([]))
        : json(page([account('Sam Student', 'STUDENT')])),
    )
    const { user } = renderApp('/admin/users')

    await user.type(
      await screen.findByLabelText('Search by name or email'),
      'nobody',
    )

    expect(await screen.findByText('No users match this search.')).toBeVisible()
    const last = server.to('GET', '/api/v1/admin/users').at(-1)!
    expect(last.url.searchParams.get('query')).toBe('nobody')
    expect(last.url.searchParams.get('page')).toBe('0')
  })

  it('says so when the list cannot be loaded', async () => {
    adminApi(() => new Response(null, { status: 503 }))

    renderApp('/admin/users')

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'The users could not be loaded.',
    )
    expect(screen.getByRole('button', { name: 'Try again' })).toBeVisible()
  })

  it('is not shown to a student, and the list is never requested', async () => {
    const server = fakeApi({
      'GET /api/v1/meta': () => json(META),
      'POST /api/v1/auth/refresh': () => json(session('STUDENT')),
    })

    renderApp('/admin/users')

    expect(
      await screen.findByRole('heading', { name: 'Not allowed' }),
    ).toBeVisible()
    expect(server.to('GET', '/api/v1/admin/users')).toHaveLength(0)
  })

  it('sets a new password for another account, never for the admin’s own', async () => {
    const own = {
      ...account('Ada Admin', 'ADMIN'),
      id: '0199a1b2-0000-7000-8000-000000000001',
    }
    const server = adminApi(
      () => json(page([own, account('Sam Student', 'STUDENT')])),
      {
        'POST /api/v1/admin/users/id-sam.student/password': () =>
          new Response(null, { status: 204 }),
      },
    )
    const { user } = renderApp('/admin/users')

    await user.click(
      await screen.findByRole('button', {
        name: 'Set a new password for Sam Student',
      }),
    )
    expect(
      screen.queryByRole('button', {
        name: 'Set a new password for Ada Admin',
      }),
    ).toBeNull()
    const dialog = await screen.findByRole('dialog')
    await user.type(within(dialog).getByLabelText('New password'), 'short')
    await user.type(
      within(dialog).getByLabelText('New password again'),
      'short',
    )
    await user.click(
      within(dialog).getByRole('button', { name: 'Set password' }),
    )
    expect(
      await within(dialog).findByText('Use at least 10 characters.'),
    ).toBeVisible()

    await user.clear(within(dialog).getByLabelText('New password'))
    await user.clear(within(dialog).getByLabelText('New password again'))
    await user.type(
      within(dialog).getByLabelText('New password'),
      'a long new passphrase',
    )
    await user.type(
      within(dialog).getByLabelText('New password again'),
      'a long new passphrase',
    )
    await user.click(
      within(dialog).getByRole('button', { name: 'Set password' }),
    )

    expect(await screen.findByRole('status')).toHaveTextContent(
      'Sam Student has a new password and was signed out everywhere.',
    )
    expect(
      server.to('POST', '/api/v1/admin/users/id-sam.student/password')[0].body,
    ).toEqual({ newPassword: 'a long new passphrase' })
  })

  it('keeps the password dialog open while the password is being saved', async () => {
    let release: () => void = () => {}
    adminApi(() => json(page([account('Sam Student', 'STUDENT')])), {
      'POST /api/v1/admin/users/id-sam.student/password': () =>
        new Promise<Response>((resolve) => {
          release = () => resolve(new Response(null, { status: 204 }))
        }),
    })
    const { user } = renderApp('/admin/users')

    await user.click(
      await screen.findByRole('button', {
        name: 'Set a new password for Sam Student',
      }),
    )
    const dialog = await screen.findByRole('dialog')
    await user.type(
      within(dialog).getByLabelText('New password'),
      'a long new passphrase',
    )
    await user.type(
      within(dialog).getByLabelText('New password again'),
      'a long new passphrase',
    )
    await user.click(
      within(dialog).getByRole('button', { name: 'Set password' }),
    )
    // Focus inside the dialog, where Escape would normally close it.
    screen.getByRole('dialog').focus()
    await user.keyboard('{Escape}')

    // The result belongs to this dialog: it cannot be closed until it arrives.
    expect(screen.getByRole('dialog')).toBeVisible()
    release()
    expect(await screen.findByRole('status')).toHaveTextContent(
      'Sam Student has a new password',
    )
    expect(screen.queryByRole('dialog')).toBeNull()
  })
})
