import { screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import type { components } from '../api/schema'
import {
  fakeApi,
  json,
  META,
  session,
  type RecordedRequest,
} from '../test/fakeApi'
import { renderApp } from '../test/renderApp'

type AdminDocument = components['schemas']['AdminDocumentResponse']

function document(
  title: string,
  status: AdminDocument['status'],
  extra: Partial<AdminDocument> = {},
): AdminDocument {
  return {
    id: `id-${title.toLowerCase().replace(/\s+/g, '-')}`,
    title,
    owner: { id: 'owner-1', displayName: 'Faculty A' },
    status,
    archived: false,
    pageCount: 2,
    chunkCount: 5,
    processingAttempts: 0,
    sizeBytes: 1024,
    createdAt: '2026-11-01T08:00:00Z',
    ...extra,
  }
}

function page(items: AdminDocument[]) {
  return { items, page: 0, size: 20, totalItems: items.length, totalPages: 1 }
}

function adminApi(
  list: (request: RecordedRequest) => Response,
  extra: Record<string, (request: RecordedRequest) => Response> = {},
) {
  return fakeApi({
    'GET /api/v1/meta': () => json(META),
    'POST /api/v1/auth/refresh': () => json(session('ADMIN')),
    'GET /api/v1/admin/documents': list,
    ...extra,
  })
}

describe('AdminDocumentsPage', () => {
  it('lists every document with the reason one failed', async () => {
    adminApi(() =>
      json(
        page([
          document('Lab rules', 'INDEXED'),
          document('Scanned notice', 'FAILED', {
            failureCode: 'NO_TEXT_LAYER',
            failureMessage: 'The PDF has no text layer.',
            chunkCount: undefined,
          }),
        ]),
      ),
    )

    renderApp('/admin/documents')

    const rows = await screen.findAllByRole('row')
    expect(rows).toHaveLength(3)
    expect(
      within(rows[2]).getByText('The PDF has no text layer.'),
    ).toBeVisible()
    expect(
      within(rows[1]).getByRole('link', { name: 'Lab rules' }),
    ).toHaveAttribute('href', '/documents/id-lab-rules')
    expect(screen.getByText('Page 1 of 1, 2 documents')).toBeVisible()
  })

  it('filters by status', async () => {
    const server = adminApi(() => json(page([])))
    const { user } = renderApp('/admin/documents')

    await user.click(await screen.findByRole('button', { name: /Status/ }))
    await user.click(await screen.findByRole('option', { name: 'Failed' }))

    expect(
      await screen.findByText('No documents match these filters.'),
    ).toBeVisible()
    expect(
      server
        .to('GET', '/api/v1/admin/documents')
        .at(-1)
        ?.url.searchParams.get('status'),
    ).toBe('FAILED')
  })

  it('re-indexes everything after a confirmation and says how many', async () => {
    const server = adminApi(
      () => json(page([document('Lab rules', 'INDEXED')])),
      {
        'POST /api/v1/admin/reindex': () =>
          json({ queued: 4, skipped: 1 }, 202),
      },
    )
    const { user } = renderApp('/admin/documents')

    await user.click(
      await screen.findByRole('button', { name: 'Re-index all' }),
    )
    const dialog = await screen.findByRole('alertdialog')
    expect(dialog).toHaveTextContent('search and the assistant cannot use it')
    await user.click(
      within(dialog).getByRole('button', { name: 'Re-index all' }),
    )

    expect(await screen.findByRole('status')).toHaveTextContent(
      '4 documents are waiting to be processed again; 1 was already waiting or being processed.',
    )
    expect(server.to('POST', '/api/v1/admin/reindex')).toHaveLength(1)
  })

  it('reprocesses one document and deletes another after a confirmation', async () => {
    const server = adminApi(
      () =>
        json(
          page([
            document('Lab rules', 'INDEXED'),
            document('Waiting', 'UPLOADED', { chunkCount: undefined }),
          ]),
        ),
      {
        'POST /api/v1/documents/id-lab-rules/reprocess': () =>
          json(document('Lab rules', 'UPLOADED')),
        'DELETE /api/v1/documents/id-waiting': () =>
          new Response(null, { status: 204 }),
      },
    )
    const { user } = renderApp('/admin/documents')

    await user.click(
      await screen.findByRole('button', { name: 'Process “Lab rules” again' }),
    )
    expect(await screen.findByRole('status')).toHaveTextContent(
      '“Lab rules” is waiting to be processed again.',
    )
    // A document that is already waiting cannot be sent again.
    expect(
      screen.queryByRole('button', { name: 'Process “Waiting” again' }),
    ).toBeNull()

    await user.click(screen.getByRole('button', { name: 'Delete “Waiting”' }))
    await user.click(
      within(await screen.findByRole('alertdialog')).getByRole('button', {
        name: 'Delete',
      }),
    )
    expect(await screen.findByText('“Waiting” was deleted.')).toBeVisible()
    expect(server.to('DELETE', '/api/v1/documents/id-waiting')).toHaveLength(1)
  })

  it('goes back to the last page that exists after deleting the only document of the last page', async () => {
    const first = Array.from({ length: 20 }, (_, i) =>
      document(`Doc ${i + 1}`, 'INDEXED'),
    )
    let deleted = false
    const server = adminApi(
      (request) => {
        const page = Number(request.url.searchParams.get('page'))
        const total = deleted ? 20 : 21
        const items =
          page === 0 ? first : deleted ? [] : [document('Last one', 'INDEXED')]
        return json({
          items,
          page,
          size: 20,
          totalItems: total,
          totalPages: Math.ceil(total / 20),
        })
      },
      {
        'DELETE /api/v1/documents/id-last-one': () => {
          deleted = true
          return new Response(null, { status: 204 })
        },
      },
    )
    const { user } = renderApp('/admin/documents')

    await user.click(await screen.findByRole('button', { name: /Next/ }))
    await user.click(
      await screen.findByRole('button', { name: 'Delete “Last one”' }),
    )
    await user.click(
      within(await screen.findByRole('alertdialog')).getByRole('button', {
        name: 'Delete',
      }),
    )

    expect(await screen.findByText('Page 1 of 1, 20 documents')).toBeVisible()
    expect(
      screen.queryByText('No documents have been uploaded yet.'),
    ).toBeNull()
    expect(
      server
        .to('GET', '/api/v1/admin/documents')
        .at(-1)
        ?.url.searchParams.get('page'),
    ).toBe('0')
  })
})
