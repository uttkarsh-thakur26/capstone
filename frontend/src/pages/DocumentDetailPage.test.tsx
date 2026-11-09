import { screen, waitFor, within } from '@testing-library/react'
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
    [`GET ${DOC_PATH}/summary`]: () =>
      json({ documentId: DOC_ID, status: 'NONE' }),
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
    expect(screen.getByText(/Waiting in the processing queue/)).toBeVisible()
    expect(screen.getByText('exam-policy.pdf, 240 KB, 12 pages')).toBeVisible()
    expect(screen.getByText('Fatima Faculty')).toBeVisible()
    expect(screen.getByRole('button', { name: 'Download' })).toBeVisible()
    for (const name of ['Edit details', 'Reprocess', 'Archive', 'Delete']) {
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

  it('asks again while the document is processed and stops once it is indexed', async () => {
    let calls = 0
    const server = api('FACULTY', {
      [`GET ${DOC_PATH}`]: () => {
        calls += 1
        return json(
          calls === 1
            ? documentDetail({ status: 'PROCESSING' })
            : documentDetail({
                status: 'INDEXED',
                chunkCount: 31,
                indexedAt: '2026-10-27T10:00:00Z',
              }),
        )
      },
    })

    renderApp(`/documents/${DOC_ID}`)

    expect(await screen.findByText(/Being processed/)).toBeVisible()
    expect(
      await screen.findByText(
        'The document is processed and used for search and answers.',
        {},
        { timeout: 5000 },
      ),
    ).toBeVisible()
    expect(screen.getByText(/^31 passages, indexed/)).toBeVisible()
    const asked = server.to('GET', DOC_PATH).length
    await new Promise((resolve) => setTimeout(resolve, 3500))
    expect(server.to('GET', DOC_PATH)).toHaveLength(asked)
  }, 15_000)

  it('sends a failed document back for processing', async () => {
    const server = api('FACULTY', {
      [`GET ${DOC_PATH}`]: () =>
        json(
          documentDetail({
            status: 'FAILED',
            failureCode: 'EMBEDDING_ERROR',
            failureMessage: 'The embedding service failed.',
          }),
        ),
      [`POST ${DOC_PATH}/reprocess`]: () => json(documentDetail()),
    })
    const { user } = renderApp(`/documents/${DOC_ID}`)

    await user.click(await screen.findByRole('button', { name: 'Reprocess' }))

    expect(
      await screen.findByText('The document is queued for processing again.'),
    ).toBeVisible()
    expect(server.to('POST', `${DOC_PATH}/reprocess`)).toHaveLength(1)
    expect(screen.getByText('Waiting for processing')).toBeVisible()
    expect(
      screen.queryByRole('button', { name: 'Reprocess' }),
    ).not.toBeInTheDocument()
  })

  it('says so when the document cannot be queued again', async () => {
    api('FACULTY', {
      [`GET ${DOC_PATH}`]: () => json(documentDetail({ status: 'INDEXED' })),
      [`POST ${DOC_PATH}/reprocess`]: () =>
        problem(409, 'INVALID_STATUS', 'This one is processing.'),
    })
    const { user } = renderApp(`/documents/${DOC_ID}`)

    await user.click(await screen.findByRole('button', { name: 'Reprocess' }))

    expect(await screen.findByText('This one is processing.')).toBeVisible()
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

  it('edits the details under the same rules as the upload, error next to the field', async () => {
    const server = api('FACULTY', {
      [`PATCH ${DOC_PATH}`]: (request) =>
        json(
          documentDetail({
            ...(request.body as object),
            category: 'COURSE_PLAN',
          }),
        ),
    })
    const { user } = renderApp(`/documents/${DOC_ID}`)
    expect(await screen.findByText('Policy')).toBeVisible()

    await user.click(screen.getByRole('button', { name: 'Edit details' }))
    const dialog = await screen.findByRole('dialog')
    const title = within(dialog).getByRole('textbox', { name: 'Title' })
    const save = within(dialog).getByRole('button', { name: 'Save' })
    await user.clear(title)
    await user.type(title, '1111')
    await user.tab()

    expect(
      await within(dialog).findByText(
        'Use at least 3 letters, not only digits or symbols.',
      ),
    ).toBeVisible()
    expect(title).toHaveValue('1111')
    expect(save).toBeDisabled()

    const date = within(dialog).getByLabelText('Effective date (optional)')
    await user.clear(date)
    await user.type(date, '2099-01-01')
    await user.tab()
    expect(await within(dialog).findByText(/^Pick a date from /)).toBeVisible()

    await user.clear(title)
    await user.type(title, ' Course   plan 2026 ')
    await user.clear(date)
    await user.click(within(dialog).getByRole('button', { name: /Category/ }))
    await user.click(await screen.findByRole('option', { name: 'Course plan' }))
    await waitFor(() => expect(save).toBeEnabled())
    await user.click(save)

    await waitFor(() => expect(server.to('PATCH', DOC_PATH)).toHaveLength(1))
    expect(server.to('PATCH', DOC_PATH)[0].body).toEqual({
      title: 'Course plan 2026',
      description: 'Rules for all examinations.',
      category: 'COURSE_PLAN',
    })
    expect(await screen.findByText('Course plan')).toBeVisible()
  })

  it('forgets a cancelled edit and its errors when the dialog opens again', async () => {
    api('FACULTY')
    const { user } = renderApp(`/documents/${DOC_ID}`)

    await user.click(
      await screen.findByRole('button', { name: 'Edit details' }),
    )
    let dialog = await screen.findByRole('dialog')
    const title = within(dialog).getByRole('textbox', { name: 'Title' })
    await user.clear(title)
    await user.type(title, '1111')
    await user.tab()
    expect(
      await within(dialog).findByText(
        'Use at least 3 letters, not only digits or symbols.',
      ),
    ).toBeVisible()
    await user.click(within(dialog).getByRole('button', { name: 'Cancel' }))
    await waitFor(() =>
      expect(screen.queryByRole('dialog')).not.toBeInTheDocument(),
    )

    await user.click(screen.getByRole('button', { name: 'Edit details' }))
    dialog = await screen.findByRole('dialog')

    expect(within(dialog).getByRole('textbox', { name: 'Title' })).toHaveValue(
      'Examination Policy 2026',
    )
    expect(
      within(dialog).queryByText(
        'Use at least 3 letters, not only digits or symbols.',
      ),
    ).not.toBeInTheDocument()
    expect(within(dialog).getByRole('button', { name: 'Save' })).toBeEnabled()
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

  it('opens the viewer at the page in the address, with the access token', async () => {
    const server = api('STUDENT', {
      [`GET ${DOC_PATH}`]: () =>
        json(documentDetail({ canManage: false, status: 'INDEXED' })),
      [`GET ${DOC_PATH}/content`]: () =>
        new Response(new Blob(['%PDF-1.7']), {
          headers: { 'Content-Type': 'application/pdf' },
        }),
    })
    vi.stubGlobal(
      'URL',
      Object.assign(URL, {
        createObjectURL: vi.fn(() => 'blob:document'),
        revokeObjectURL: vi.fn(),
      }),
    )

    renderApp(`/documents/${DOC_ID}?page=3`)

    expect(await screen.findByRole('heading', { name: 'Page 3' })).toBeVisible()
    const viewer = await screen.findByTitle('Examination Policy 2026, page 3')
    expect(viewer).toHaveAttribute('src', 'blob:document#page=3')
    expect(
      server.to('GET', `${DOC_PATH}/content`)[0].headers.get('Authorization'),
    ).toBe('Bearer token-STUDENT')
    expect(
      screen.getByRole('link', { name: 'Search in this document' }),
    ).toHaveAttribute('href', `/search?documentId=${DOC_ID}`)
  })

  it('loads the file only when the reader asks to view it, and keeps pages within the document', async () => {
    const server = api('STUDENT', {
      [`GET ${DOC_PATH}`]: () =>
        json(
          documentDetail({
            canManage: false,
            archived: true,
            status: 'INDEXED',
          }),
        ),
      [`GET ${DOC_PATH}/content`]: () =>
        new Response(new Blob(['%PDF-1.7']), {
          headers: { 'Content-Type': 'application/pdf' },
        }),
    })
    vi.stubGlobal(
      'URL',
      Object.assign(URL, {
        createObjectURL: vi.fn(() => 'blob:document'),
        revokeObjectURL: vi.fn(),
      }),
    )
    const { user } = renderApp(`/documents/${DOC_ID}`)

    const view = await screen.findByRole('button', {
      name: 'View the document',
    })
    expect(server.to('GET', `${DOC_PATH}/content`)).toHaveLength(0)
    // Archived: search leaves it out, so the page offers no search in it.
    expect(
      screen.queryByRole('link', { name: 'Search in this document' }),
    ).not.toBeInTheDocument()

    await user.click(view)

    expect(
      await screen.findByTitle('Examination Policy 2026, page 1'),
    ).toHaveAttribute('src', 'blob:document#page=1')
  })

  it('opens a page beyond the end at the last page', async () => {
    api('STUDENT', {
      [`GET ${DOC_PATH}`]: () => json(documentDetail({ canManage: false })),
      [`GET ${DOC_PATH}/content`]: () =>
        new Response(new Blob(['%PDF-1.7']), {
          headers: { 'Content-Type': 'application/pdf' },
        }),
    })
    vi.stubGlobal(
      'URL',
      Object.assign(URL, {
        createObjectURL: vi.fn(() => 'blob:document'),
        revokeObjectURL: vi.fn(),
      }),
    )

    renderApp(`/documents/${DOC_ID}?page=99`)

    expect(
      await screen.findByTitle('Examination Policy 2026, page 12'),
    ).toHaveAttribute('src', 'blob:document#page=12')
  })

  it('closes the viewer when another document is opened', async () => {
    const otherId = '0199a1b2-0000-7000-8000-00000000d0c2'
    const server = api('STUDENT', {
      [`GET ${DOC_PATH}`]: () => json(documentDetail({ canManage: false })),
      [`GET ${DOC_PATH}/content`]: () =>
        new Response(new Blob(['%PDF-1.7']), {
          headers: { 'Content-Type': 'application/pdf' },
        }),
      [`GET /api/v1/documents/${otherId}`]: () =>
        json(
          documentDetail({
            id: otherId,
            title: 'Hostel Rules',
            canManage: false,
          }),
        ),
    })
    vi.stubGlobal(
      'URL',
      Object.assign(URL, {
        createObjectURL: vi.fn(() => 'blob:document'),
        revokeObjectURL: vi.fn(),
      }),
    )
    const { router } = renderApp(`/documents/${DOC_ID}?page=2`)
    await screen.findByTitle('Examination Policy 2026, page 2')

    await router.navigate(`/documents/${otherId}`)

    expect(
      await screen.findByRole('heading', { name: 'Hostel Rules' }),
    ).toBeVisible()
    expect(
      screen.getByRole('button', { name: 'View the document' }),
    ).toBeVisible()
    expect(screen.queryByTitle(/page/)).not.toBeInTheDocument()
    expect(
      server.to('GET', `/api/v1/documents/${otherId}/content`),
    ).toHaveLength(0)
  })

  it('releases the file from memory when the viewer is left', async () => {
    api('STUDENT', {
      [`GET ${DOC_PATH}`]: () => json(documentDetail({ canManage: false })),
      [`GET ${DOC_PATH}/content`]: () =>
        new Response(new Blob(['%PDF-1.7']), {
          headers: { 'Content-Type': 'application/pdf' },
        }),
    })
    const revokeObjectURL = vi.fn()
    vi.stubGlobal(
      'URL',
      Object.assign(URL, {
        createObjectURL: vi.fn(() => 'blob:document'),
        revokeObjectURL,
      }),
    )
    const { router } = renderApp(`/documents/${DOC_ID}?page=2`)
    await screen.findByTitle('Examination Policy 2026, page 2')
    expect(revokeObjectURL).not.toHaveBeenCalled()

    await router.navigate('/search')

    await vi.waitFor(() =>
      expect(revokeObjectURL).toHaveBeenCalledWith('blob:document'),
    )
  })
})
