import { screen, within } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type {
  CitationResponse,
  ConversationDetail,
  MessageResponse,
} from '../api/types'
import { DOC_ID } from '../test/documents'
import {
  fakeApi,
  json,
  META,
  problem,
  session,
  type RecordedRequest,
} from '../test/fakeApi'
import { renderApp } from '../test/renderApp'

const CONVERSATION_ID = '0199a1b2-0000-7000-8000-0000000c0001'
const PATH = `/api/v1/conversations/${CONVERSATION_ID}`

function citation(overrides: Partial<CitationResponse> = {}): CitationResponse {
  return {
    ordinal: 1,
    available: true,
    documentId: DOC_ID,
    documentTitle: 'Lab exam instructions',
    pageStart: 1,
    pageEnd: 1,
    snippet: 'The lab exam will be of 55 minutes duration.',
    ...overrides,
  }
}

function question(content: string, id = 'q1'): MessageResponse {
  return {
    id,
    role: 'USER',
    content,
    createdAt: '2026-11-02T10:00:00Z',
    citations: [],
  }
}

function answer(overrides: Partial<MessageResponse> = {}): MessageResponse {
  return {
    id: 'a1',
    role: 'ASSISTANT',
    content: 'The lab exam lasts 55 minutes [1].',
    outcome: 'ANSWERED',
    createdAt: '2026-11-02T10:00:01Z',
    citations: [citation()],
    ...overrides,
  }
}

function detail(messages: MessageResponse[]): ConversationDetail {
  return {
    id: CONVERSATION_ID,
    title: 'How long is the lab exam?',
    createdAt: '2026-11-02T10:00:00Z',
    updatedAt: '2026-11-02T10:00:01Z',
    messages,
  }
}

function api(
  routes: Record<
    string,
    (request: RecordedRequest) => Response | Promise<Response>
  >,
) {
  return fakeApi({
    'GET /api/v1/meta': () => json(META),
    'POST /api/v1/auth/refresh': () => json(session('STUDENT')),
    ...routes,
  })
}

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('ConversationPage', () => {
  it('shows an answer with numbered citation chips and opens the cited page with its snippet', async () => {
    api({
      [`GET ${PATH}`]: () =>
        json(detail([question('How long is the lab exam?'), answer()])),
      [`GET /api/v1/documents/${DOC_ID}/content`]: () =>
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
    const { user } = renderApp(`/conversations/${CONVERSATION_ID}`)

    const log = await screen.findByRole('log', { name: 'Conversation' })
    expect(within(log).getByText('How long is the lab exam?')).toBeVisible()
    expect(within(log).getByText(/The lab exam lasts 55 minutes/)).toBeVisible()
    expect(
      within(log).getByText(/Check them against the cited page/),
    ).toBeVisible()
    const chips = within(log).getAllByRole('button', {
      name: 'Source 1: Lab exam instructions, page 1',
    })
    expect(chips).toHaveLength(2)

    await user.click(chips[0])

    const panel = await screen.findByRole('region', {
      name: 'Source 1: Lab exam instructions',
    })
    expect(
      within(panel).getByText('The lab exam will be of 55 minutes duration.'),
    ).toBeVisible()
    expect(
      await within(panel).findByTitle('Lab exam instructions, page 1'),
    ).toHaveAttribute('src', 'blob:document#page=1')
    expect(
      within(panel).getByRole('link', { name: 'Open the document page' }),
    ).toHaveAttribute('href', `/documents/${DOC_ID}?page=1`)
  })

  it('shows a citation whose document is gone as no longer available, with no title or link', async () => {
    api({
      [`GET ${PATH}`]: () =>
        json(
          detail([
            question('How long is the lab exam?'),
            answer({
              citations: [
                {
                  ordinal: 1,
                  available: false,
                  documentId: undefined,
                  documentTitle: undefined,
                  pageStart: undefined,
                  pageEnd: undefined,
                  snippet: undefined,
                },
              ],
            }),
          ]),
        ),
    })
    renderApp(`/conversations/${CONVERSATION_ID}`)

    const log = await screen.findByRole('log', { name: 'Conversation' })
    expect(within(log).getByText(/The lab exam lasts 55 minutes/)).toBeVisible()
    expect(within(log).getByText('Source no longer available')).toBeVisible()
    expect(
      within(log).getAllByLabelText('Source 1, no longer available'),
    ).toHaveLength(2)
    expect(within(log).queryByRole('button', { name: /Source 1/ })).toBeNull()
    expect(within(log).queryByText(/Lab exam instructions/)).toBeNull()
  })

  it('shows insufficient evidence as a clearly different card, without sources', async () => {
    api({
      [`GET ${PATH}`]: () =>
        json(
          detail([
            question('What is the re-exam fee?'),
            answer({
              outcome: 'INSUFFICIENT_EVIDENCE',
              content:
                'Insufficient information was found in the available authorized documents to answer this question.',
              citations: [],
            }),
          ]),
        ),
    })
    renderApp(`/conversations/${CONVERSATION_ID}`)

    expect(await screen.findByText('No answer found')).toBeVisible()
    expect(screen.getByText(/Insufficient information was found/)).toBeVisible()
    expect(screen.queryByText('Sources')).toBeNull()
    expect(screen.queryByText(/Check them against the cited page/)).toBeNull()
  })

  it('renders model output as text, never as HTML (UI-6)', async () => {
    api({
      [`GET ${PATH}`]: () =>
        json(
          detail([
            question('<b>Bold?</b>'),
            answer({
              content:
                '<img src=x onerror="alert(1)"> <script>alert(2)</script> Fine [1].',
            }),
          ]),
        ),
    })
    renderApp(`/conversations/${CONVERSATION_ID}`)

    const log = await screen.findByRole('log', { name: 'Conversation' })
    expect(
      within(log).getByText(/<img src=x onerror="alert\(1\)">/),
    ).toBeVisible()
    expect(within(log).getByText('<b>Bold?</b>')).toBeVisible()
    expect(log.querySelector('img, script, b')).toBeNull()
  })

  it('asks, shows the question as pending, then the stored answer', async () => {
    let answered = false
    let release: () => void = () => {}
    const server = api({
      [`GET ${PATH}`]: () =>
        json(
          detail(
            answered ? [question('How long is the lab exam?'), answer()] : [],
          ),
        ),
      [`POST ${PATH}/messages`]: () =>
        new Promise<Response>((resolve) => {
          release = () => {
            answered = true
            resolve(
              json({
                messageId: 'a1',
                outcome: 'ANSWERED',
                answer: 'The lab exam lasts 55 minutes [1].',
                citations: [citation()],
              }),
            )
          }
        }),
    })
    const { user } = renderApp(`/conversations/${CONVERSATION_ID}`)

    expect(
      await screen.findByText('No questions yet. Ask one below.'),
    ).toBeVisible()
    await user.type(
      screen.getByRole('textbox', { name: 'Your question' }),
      '  How long is the lab exam?{Enter}',
    )

    expect(
      await screen.findByText(
        'Searching your documents and writing an answer…',
      ),
    ).toBeVisible()
    expect(server.to('POST', `${PATH}/messages`)[0].body).toEqual({
      question: 'How long is the lab exam?',
    })
    release()

    expect(
      await screen.findByText(/The lab exam lasts 55 minutes/),
    ).toBeVisible()
    expect(
      screen.queryByText('Searching your documents and writing an answer…'),
    ).toBeNull()
    expect(screen.getByRole('textbox', { name: 'Your question' })).toHaveValue(
      '',
    )
  })

  it('keeps the question and says why when the answer could not be produced', async () => {
    api({
      [`GET ${PATH}`]: () => json(detail([])),
      [`POST ${PATH}/messages`]: () =>
        problem(
          503,
          'MODEL_UNAVAILABLE',
          'The answer could not be produced right now. Try again later.',
        ),
    })
    const { user } = renderApp(`/conversations/${CONVERSATION_ID}`)

    const box = await screen.findByRole('textbox', { name: 'Your question' })
    await user.type(box, 'How long is the lab exam?')
    await user.click(screen.getByRole('button', { name: 'Ask' }))

    expect(
      await screen.findByText(
        'The answer could not be produced right now. Try again later.',
      ),
    ).toBeVisible()
    expect(box).toHaveValue('How long is the lab exam?')
  })

  it('says a conversation that is not the user’s does not exist', async () => {
    api({
      [`GET ${PATH}`]: () =>
        problem(404, 'NOT_FOUND', 'The requested resource does not exist.'),
    })
    renderApp(`/conversations/${CONVERSATION_ID}`)

    expect(
      await screen.findByRole('heading', { name: 'Conversation not found' }),
    ).toBeVisible()
  })
})
