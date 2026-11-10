import { screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import type { components } from '../api/schema'
import {
  fakeApi,
  json,
  META,
  problem,
  session,
  type RecordedRequest,
} from '../test/fakeApi'
import { renderApp } from '../test/renderApp'

type Entry = components['schemas']['AuditEntryResponse']

const ENTRIES: Entry[] = [
  {
    id: 2,
    occurredAt: '2026-11-09T10:15:00Z',
    actorUserId: 'admin-1',
    actorEmail: 'admin@example.org',
    action: 'ROLE_CHANGED',
    resourceType: 'USER',
    resourceId: 'user-7',
    outcome: 'SUCCESS',
    ipAddress: '10.0.0.4',
    details: { from: 'STUDENT', to: 'FACULTY' },
  },
  {
    id: 1,
    occurredAt: '2026-11-09T10:00:00Z',
    actorEmail: 'someone@example.org',
    action: 'LOGIN_FAILURE',
    outcome: 'DENIED',
    ipAddress: '10.0.0.9',
    details: { reason: 'credentials' },
  },
]

function auditApi(list: (request: RecordedRequest) => Response) {
  return fakeApi({
    'GET /api/v1/meta': () => json(META),
    'POST /api/v1/auth/refresh': () => json(session('ADMIN')),
    'GET /api/v1/admin/audit': list,
  })
}

function page(items: Entry[]) {
  return { items, page: 0, size: 50, totalItems: items.length, totalPages: 1 }
}

describe('AdminAuditPage', () => {
  it('lists the entries in words with their details', async () => {
    auditApi(() => json(page(ENTRIES)))

    renderApp('/admin/audit')

    const rows = await screen.findAllByRole('row')
    expect(rows).toHaveLength(3)
    expect(within(rows[1]).getByText('Role changed')).toBeVisible()
    expect(within(rows[1]).getAllByText('FACULTY')[0]).toBeInTheDocument()
    expect(within(rows[2]).getByText('Sign-in failed')).toBeVisible()
    expect(within(rows[2]).getByText('Refused')).toBeVisible()
    expect(screen.getByText('Page 1 of 1, 2 entries')).toBeVisible()
  })

  it('sends the filters to the server, the last day included', async () => {
    const server = auditApi(() => json(page([])))
    const { user } = renderApp('/admin/audit')

    await user.click(await screen.findByRole('button', { name: /Action/ }))
    await user.click(
      await screen.findByRole('option', { name: 'Sign-in failed' }),
    )
    await user.type(
      screen.getByRole('searchbox', { name: 'Who (email)' }),
      'someone',
    )
    await user.type(screen.getByLabelText('From'), '2026-11-01')
    await user.type(screen.getByLabelText('To (included)'), '2026-11-09')

    expect(
      await screen.findByText('No entries match these filters.'),
    ).toBeVisible()
    const params = server.to('GET', '/api/v1/admin/audit').at(-1)!.url
      .searchParams
    expect(params.get('action')).toBe('LOGIN_FAILURE')
    expect(params.get('actor')).toBe('someone')
    expect(params.get('from')).toBe(new Date(2026, 10, 1).toISOString())
    expect(params.get('to')).toBe(new Date(2026, 10, 10).toISOString())
  })

  it('shows an error with a way to try again', async () => {
    auditApi(() => problem(500, 'INTERNAL', 'An unexpected error occurred.'))

    renderApp('/admin/audit')

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'An unexpected error occurred.',
    )
    expect(screen.getByRole('button', { name: 'Try again' })).toBeVisible()
  })
})
