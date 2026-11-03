import { screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { documentPage, documentSummary } from '../test/documents'
import { fakeApi, json, META, problem, session } from '../test/fakeApi'
import { renderApp } from '../test/renderApp'

const CONVERSATION_ID = '0199a1b2-0000-7000-8000-0000000c0001'
const CONVERSATION = {
  id: CONVERSATION_ID,
  title: 'How long is the lab exam?',
  createdAt: '2026-11-02T10:00:00Z',
  updatedAt: '2026-11-02T10:00:01Z',
}

function page<T>(items: T[]) {
  return {
    items,
    page: 0,
    size: 5,
    totalItems: items.length,
    totalPages: items.length ? 1 : 0,
  }
}

describe('HomePage', () => {
  it('lists the newest readable documents and the latest conversations', async () => {
    const server = fakeApi({
      'GET /api/v1/meta': () => json(META),
      'POST /api/v1/auth/refresh': () => json(session('STUDENT')),
      'GET /api/v1/documents': () =>
        json(
          documentPage([
            documentSummary('Lab exam instructions', { status: 'INDEXED' }),
          ]),
        ),
      'GET /api/v1/conversations': () => json(page([CONVERSATION])),
    })

    renderApp('/')

    const documents = await screen.findByRole('region', {
      name: 'Recent documents',
    })
    expect(
      await within(documents).findByRole('link', {
        name: 'Lab exam instructions',
      }),
    ).toHaveAttribute('href', '/documents/id-lab-exam-instructions')
    expect(within(documents).getByText('Ready')).toBeVisible()
    const conversations = screen.getByRole('region', {
      name: 'Recent conversations',
    })
    expect(
      await within(conversations).findByRole('link', {
        name: 'How long is the lab exam?',
      }),
    ).toHaveAttribute('href', `/conversations/${CONVERSATION_ID}`)
    // The same list endpoint as the documents page, so the server's permission filter applies.
    const [request] = server.to('GET', '/api/v1/documents')
    expect(request.url.searchParams.get('size')).toBe('5')
    expect(request.url.searchParams.get('archived')).toBe('false')
  })

  it('tells a student when nothing is shared yet, without offering an upload', async () => {
    fakeApi({
      'GET /api/v1/meta': () => json(META),
      'POST /api/v1/auth/refresh': () => json(session('STUDENT')),
      'GET /api/v1/documents': () => json(documentPage([])),
      'GET /api/v1/conversations': () => json(page([])),
    })

    renderApp('/')

    expect(
      await screen.findByText('Nothing has been shared with you yet.'),
    ).toBeVisible()
    expect(
      await screen.findByText(
        'None yet. Your questions and their answers are kept here.',
      ),
    ).toBeVisible()
    expect(
      screen.queryByRole('link', { name: 'Upload a document' }),
    ).not.toBeInTheDocument()
  })

  it('offers faculty an upload when they have no documents', async () => {
    fakeApi({
      'GET /api/v1/meta': () => json(META),
      'POST /api/v1/auth/refresh': () => json(session('FACULTY')),
      'GET /api/v1/documents': () => json(documentPage([])),
      'GET /api/v1/conversations': () => json(page([])),
    })

    renderApp('/')

    const documents = await screen.findByRole('region', {
      name: 'Recent documents',
    })
    expect(
      await within(documents).findByRole('link', { name: 'Upload a document' }),
    ).toHaveAttribute('href', '/documents/upload')
  })

  it('shows a failed list with a way to try again', async () => {
    let calls = 0
    fakeApi({
      'GET /api/v1/meta': () => json(META),
      'POST /api/v1/auth/refresh': () => json(session('STUDENT')),
      'GET /api/v1/documents': () =>
        ++calls === 1
          ? problem(503, 'UNAVAILABLE', 'Try again later.')
          : json(documentPage([documentSummary('Shared policy')])),
      'GET /api/v1/conversations': () => json(page([])),
    })
    const { user } = renderApp('/')

    const documents = await screen.findByRole('region', {
      name: 'Recent documents',
    })
    expect(await within(documents).findByText('Try again later.')).toBeVisible()
    await user.click(
      within(documents).getByRole('button', { name: 'Try again' }),
    )

    expect(
      await within(documents).findByRole('link', { name: 'Shared policy' }),
    ).toBeVisible()
  })

  it('asks a quick question in a new conversation and opens it', async () => {
    const server = fakeApi({
      'GET /api/v1/meta': () => json(META),
      'POST /api/v1/auth/refresh': () => json(session('STUDENT')),
      'GET /api/v1/documents': () => json(documentPage([])),
      'GET /api/v1/conversations': () => json(page([])),
      'POST /api/v1/conversations': () =>
        json({ ...CONVERSATION, title: undefined }, 201),
      [`POST /api/v1/conversations/${CONVERSATION_ID}/messages`]: () =>
        json({
          messageId: 'a1',
          outcome: 'ANSWERED',
          answer: 'It lasts 55 minutes. [1]',
          citations: [],
        }),
      [`GET /api/v1/conversations/${CONVERSATION_ID}`]: () =>
        json({ ...CONVERSATION, messages: [] }),
    })
    const { user, router } = renderApp('/')

    await user.type(
      await screen.findByRole('textbox', { name: 'Your question' }),
      'How long is the lab exam?',
    )
    await user.click(screen.getByRole('button', { name: 'Ask' }))

    expect(
      await screen.findByRole('heading', { name: 'How long is the lab exam?' }),
    ).toBeVisible()
    expect(router.state.location.pathname).toBe(
      `/conversations/${CONVERSATION_ID}`,
    )
    expect(
      server.to('POST', `/api/v1/conversations/${CONVERSATION_ID}/messages`)[0]
        .body,
    ).toEqual({ question: 'How long is the lab exam?' })
  })
})
