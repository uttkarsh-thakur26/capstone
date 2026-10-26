import { screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { documentPage, documentSummary } from '../test/documents'
import {
  fakeApi,
  json,
  META,
  problem,
  session,
  type RecordedRequest,
} from '../test/fakeApi'
import { renderApp } from '../test/renderApp'

function api(
  role: 'ADMIN' | 'FACULTY' | 'STUDENT',
  list: (request: RecordedRequest) => Response,
) {
  return fakeApi({
    'GET /api/v1/meta': () => json(META),
    'POST /api/v1/auth/refresh': () => json(session(role)),
    'GET /api/v1/documents': list,
  })
}

describe('DocumentsPage', () => {
  it('lists the readable documents with their status, owner and size', async () => {
    api('FACULTY', () =>
      json(
        documentPage([
          documentSummary('Examination Policy 2026'),
          documentSummary('Old Fee Schedule', {
            status: 'INDEXED',
            archived: true,
            sizeBytes: 3 * 1024 * 1024,
          }),
        ]),
      ),
    )

    renderApp('/documents')

    const rows = await screen.findAllByRole('row')
    expect(rows).toHaveLength(3)
    expect(
      within(rows[1]).getByRole('link', { name: 'Examination Policy 2026' }),
    ).toHaveAttribute('href', '/documents/id-examination-policy-2026')
    expect(within(rows[1]).getByText('Waiting for processing')).toBeVisible()
    expect(within(rows[2]).getByText('Ready')).toBeVisible()
    expect(within(rows[2]).getByText('Archived')).toBeVisible()
    expect(within(rows[2]).getByText('3.0 MB')).toBeVisible()
    expect(
      screen.getByRole('link', { name: 'Upload a document' }),
    ).toBeVisible()
  })

  it('asks the server for current documents only by default and sends the search', async () => {
    const server = api('FACULTY', () => json(documentPage([])))
    const { user } = renderApp('/documents')
    await screen.findByText('There are no documents yet. Upload the first one.')

    await user.type(
      screen.getByRole('searchbox', { name: 'Search titles and descriptions' }),
      'exam',
    )

    expect(
      await screen.findByText('No documents match these filters.'),
    ).toBeVisible()
    const last = server.to('GET', '/api/v1/documents').at(-1)!
    expect(last.url.searchParams.get('archived')).toBe('false')
    expect(last.url.searchParams.get('query')).toBe('exam')
  })

  it('shows students an empty state without an upload link', async () => {
    api('STUDENT', () => json(documentPage([])))

    renderApp('/documents')

    expect(
      await screen.findByText('No documents have been shared with you yet.'),
    ).toBeVisible()
    expect(
      screen.queryByRole('link', { name: 'Upload a document' }),
    ).not.toBeInTheDocument()
    expect(
      screen.queryByRole('checkbox', { name: 'Only my uploads' }),
    ).not.toBeInTheDocument()
  })

  it('shows an error that can be retried', async () => {
    let fail = true
    api('STUDENT', () =>
      fail
        ? problem(503, 'STORAGE_UNAVAILABLE', 'Try again later.')
        : json(documentPage([documentSummary('Shared policy')])),
    )
    const { user } = renderApp('/documents')

    expect(await screen.findByText('Try again later.')).toBeVisible()
    fail = false
    await user.click(screen.getByRole('button', { name: 'Try again' }))

    expect(
      await screen.findByRole('link', { name: 'Shared policy' }),
    ).toBeVisible()
  })
})
