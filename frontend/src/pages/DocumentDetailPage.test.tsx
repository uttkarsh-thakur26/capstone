import { screen, within } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import {
  DOC_ID,
  documentDetail,
  documentPage,
  personGrant,
  STUDENT_GRANT,
} from '../test/documents'
import {
  fakeApi,
  json,
  META,
  noContent,
  problem,
  session,
  type RecordedRequest,
} from '../test/fakeApi'
import { renderApp } from '../test/renderApp'

const DOC_PATH = `/api/v1/documents/${DOC_ID}`

function api(
  role: 'ADMIN' | 'FACULTY' | 'STUDENT',
  routes: Record<string, (request: RecordedRequest) => Response> = {},
) {
  return fakeApi({
    'GET /api/v1/meta': () => json(META),
    'POST /api/v1/auth/refresh': () => json(session(role)),
    [`GET ${DOC_PATH}`]: () => json(documentDetail()),
    [`GET ${DOC_PATH}/grants`]: () => json({ grants: [STUDENT_GRANT] }),
    ...routes,
  })
}

describe('DocumentDetailPage', () => {
  it('shows the details, the status and the download to a reader who cannot manage it', async () => {
    const server = api('STUDENT', {
      [`GET ${DOC_PATH}`]: () => json(documentDetail({ canManage: false })),
    })

    renderApp(`/documents/${DOC_ID}`)

    expect(
      await screen.findByRole('heading', { name: 'Examination Policy 2026' }),
    ).toBeVisible()
    expect(screen.getByText('Waiting for processing')).toBeVisible()
    expect(screen.getByText(/Processing is not available yet/)).toBeVisible()
    expect(screen.getByText('exam-policy.pdf, 240 KB, 12 pages')).toBeVisible()
    expect(screen.getByText('Fatima Faculty')).toBeVisible()
    expect(screen.getByRole('button', { name: 'Download' })).toBeVisible()
    for (const name of ['Edit details', 'Archive', 'Delete']) {
      expect(screen.queryByRole('button', { name })).not.toBeInTheDocument()
    }
    expect(
      screen.queryByRole('heading', { name: 'Sharing' }),
    ).not.toBeInTheDocument()
    expect(server.to('GET', `${DOC_PATH}/grants`)).toHaveLength(0)
  })

  it('says only that the document is not found when the server answers 404', async () => {
    api('STUDENT', {
      [`GET ${DOC_PATH}`]: () =>
        problem(404, 'NOT_FOUND', 'The requested resource does not exist.'),
    })

    renderApp(`/documents/${DOC_ID}`)

    expect(
      await screen.findByRole('heading', { name: 'Document not found' }),
    ).toBeVisible()
    expect(
      screen.getByText('There is no document at this address.'),
    ).toBeVisible()
  })

  it('shows why processing failed', async () => {
    api('FACULTY', {
      [`GET ${DOC_PATH}`]: () =>
        json(
          documentDetail({
            status: 'FAILED',
            failureCode: 'NO_TEXT_LAYER',
            failureMessage: 'The PDF has no text layer.',
          }),
        ),
    })

    renderApp(`/documents/${DOC_ID}`)

    expect(
      await screen.findByText('Processing failed: The PDF has no text layer.'),
    ).toBeVisible()
  })

  it('lets the owner change the sharing and saves the whole set', async () => {
    const server = api('FACULTY', {
      'GET /api/v1/users/search': () =>
        json({
          items: [personGrant('Sam Student').user],
          page: 0,
          size: 10,
          totalItems: 1,
          totalPages: 1,
        }),
      [`PUT ${DOC_PATH}/grants`]: (request) =>
        json({
          grants: [personGrant('Sam Student')],
          echo: request.body,
        }),
    })
    const { user } = renderApp(`/documents/${DOC_ID}`)

    const students = await screen.findByRole('checkbox', {
      name: 'All students',
    })
    expect(students).toBeChecked()
    expect(screen.getByRole('button', { name: 'Save sharing' })).toBeDisabled()

    await user.click(students)
    await user.type(
      screen.getByRole('combobox', { name: 'Add a person' }),
      'sam',
    )
    await user.click(await screen.findByRole('option', { name: /Sam Student/ }))
    expect(screen.getByText('sam.student@example.org')).toBeVisible()
    await user.click(screen.getByRole('button', { name: 'Save sharing' }))

    expect(await screen.findByText('Sharing was saved.')).toBeVisible()
    expect(server.to('PUT', `${DOC_PATH}/grants`)[0].body).toEqual({
      grants: [{ type: 'USER', userId: 'id-sam.student' }],
    })
    expect(
      server.to('GET', '/api/v1/users/search')[0].url.searchParams.get('query'),
    ).toBe('sam')
  })

  it('archives the document', async () => {
    const server = api('FACULTY', {
      [`PATCH ${DOC_PATH}`]: () =>
        json(documentDetail({ archived: true, status: 'INDEXED' })),
    })
    const { user } = renderApp(`/documents/${DOC_ID}`)

    await user.click(await screen.findByRole('button', { name: 'Archive' }))

    expect(await screen.findByText('The document is archived.')).toBeVisible()
    expect(server.to('PATCH', DOC_PATH)[0].body).toEqual({ archived: true })
    expect(
      screen.getByRole('button', { name: 'Bring back from archive' }),
    ).toBeVisible()
  })

  it('deletes after a confirmation and returns to the list', async () => {
    const server = api('FACULTY', {
      [`DELETE ${DOC_PATH}`]: () => noContent(),
      'GET /api/v1/documents': () => json(documentPage([])),
    })
    const { user, router } = renderApp(`/documents/${DOC_ID}`)

    await user.click(await screen.findByRole('button', { name: 'Delete' }))
    const dialog = await screen.findByRole('alertdialog', {
      name: 'Delete this document?',
    })
    expect(server.to('DELETE', DOC_PATH)).toHaveLength(0)
    await user.click(within(dialog).getByRole('button', { name: 'Delete' }))

    expect(
      await screen.findByText('“Examination Policy 2026” was deleted.'),
    ).toBeVisible()
    expect(router.state.location.pathname).toBe('/documents')
    expect(server.to('DELETE', DOC_PATH)).toHaveLength(1)
  })

  it('downloads the file with the access token', async () => {
    const server = api('STUDENT', {
      [`GET ${DOC_PATH}`]: () => json(documentDetail({ canManage: false })),
      [`GET ${DOC_PATH}/content`]: () =>
        new Response(new Blob(['%PDF-1.7']), {
          headers: { 'Content-Type': 'application/pdf' },
        }),
    })
    const createObjectURL = vi.fn(() => 'blob:document')
    vi.stubGlobal(
      'URL',
      Object.assign(URL, { createObjectURL, revokeObjectURL: vi.fn() }),
    )
    const click = vi
      .spyOn(HTMLAnchorElement.prototype, 'click')
      .mockImplementation(() => {})
    const { user } = renderApp(`/documents/${DOC_ID}`)

    await user.click(await screen.findByRole('button', { name: 'Download' }))

    await vi.waitFor(() => expect(click).toHaveBeenCalledTimes(1))
    const request = server.to('GET', `${DOC_PATH}/content`)[0]
    expect(request.headers.get('Authorization')).toBe('Bearer token-STUDENT')
    expect((click.mock.contexts[0] as HTMLAnchorElement).download).toBe(
      'exam-policy.pdf',
    )
    click.mockRestore()
  })
})
