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
    // The notice is said once, under the question box, not after every answer.
    expect(within(log).queryByText(/Check the cited page/)).toBeNull()
    expect(screen.getAllByText(/Check the cited page/)).toHaveLength(1)
    const chips = within(log).getAllByRole('button', {
      name: 'Source 1: Lab exam instructions, page 1',
    })
    expect(chips).toHaveLength(2)

    await user.click(chips[0])

    const panel = await screen.findByRole('dialog', {
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

  it('shows insufficient evidence as a clearly different line, without sources', async () => {
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
    expect(screen.queryByRole('button', { name: /^Sources? \d/ })).toBeNull()
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

  it('shows bold labels, headings and lists as such, with HTML inside them still as text (UI-6)', async () => {
    api({
      [`GET ${PATH}`]: () =>
        json(
          detail([
            question('What are the parts of the exam?'),
            answer({
              content: [
                'The exam has two parts. [1]',
                '',
                '## Parts',
                '- **Write-up:** 5 marks [1]',
                '- **Performance:** 10 marks <b>exactly</b> [1]',
              ].join('\n'),
            }),
          ]),
        ),
    })
    renderApp(`/conversations/${CONVERSATION_ID}`)

    const log = await screen.findByRole('log', { name: 'Conversation' })
    expect(
      await within(log).findByRole('heading', { name: 'Parts' }),
    ).toBeVisible()
    const list = within(log).getByText('Write-up:').closest('ul')!
    const items = within(list).getAllByRole('listitem')
    expect(items).toHaveLength(2)
    expect(within(items[0]).getByText('Write-up:').tagName).toBe('STRONG')
    expect(within(items[1]).getByText(/<b>exactly<\/b>/)).toBeVisible()
    expect(log.querySelector('b')).toBeNull()
    // The marker repeated on every item is shown once, at the end of the list.
    expect(
      within(items[0]).queryByRole('button', { name: /^Source 1/ }),
    ).toBeNull()
    expect(
      within(items[1]).getByRole('button', { name: /^Source 1/ }),
    ).toBeVisible()
  })

  it('shows two passages of the same page as one source chip and both in the panel', async () => {
    api({
      [`GET ${PATH}`]: () =>
        json(
          detail([
            question('How long is the lab exam?'),
            answer({
              content: 'It lasts 55 minutes [1], written part first [2].',
              citations: [
                citation(),
                citation({
                  ordinal: 2,
                  snippet: 'Complete the writing part in the first 15 minutes.',
                }),
              ],
            }),
          ]),
        ),
      [`GET /api/v1/documents/${DOC_ID}/content`]: () =>
        new Response(new Blob(['%PDF-1.7'])),
    })
    vi.stubGlobal(
      'URL',
      Object.assign(URL, {
        createObjectURL: vi.fn(() => 'blob:document'),
        revokeObjectURL: vi.fn(),
      }),
    )
    const { user } = renderApp(`/conversations/${CONVERSATION_ID}`)

    const source = await screen.findByRole('button', {
      name: 'Sources 1 and 2: Lab exam instructions, page 1',
    })
    expect(
      screen.getAllByRole('button', { name: /Lab exam instructions/ }),
    ).toHaveLength(3)
    await user.click(source)

    const panel = await screen.findByRole('dialog', {
      name: 'Sources 1 and 2: Lab exam instructions',
    })
    expect(
      within(panel).getByText('The lab exam will be of 55 minutes duration.'),
    ).toBeVisible()
    expect(
      within(panel).getByText(
        'Complete the writing part in the first 15 minutes.',
      ),
    ).toBeVisible()
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
    expect(screen.getByRole('textbox', { name: 'Your question' })).toHaveValue(
      '',
    )
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

  it('keeps a new draft typed while the answer was on its way', async () => {
    let release: () => void = () => {}
    api({
      [`GET ${PATH}`]: () => json(detail([])),
      [`POST ${PATH}/messages`]: () =>
        new Promise<Response>((resolve) => {
          release = () =>
            resolve(
              json({
                messageId: 'a1',
                outcome: 'ANSWERED',
                answer: 'The lab exam lasts 55 minutes [1].',
                citations: [citation()],
              }),
            )
        }),
    })
    const { user } = renderApp(`/conversations/${CONVERSATION_ID}`)

    const box = await screen.findByRole('textbox', { name: 'Your question' })
    await user.type(box, 'How long is the lab exam?{Enter}')
    await screen.findByText('Searching your documents and writing an answer…')
    await user.clear(box)
    await user.type(box, 'And how are its marks divided?')
    release()

    await vi.waitFor(() =>
      expect(
        screen.queryByText('Searching your documents and writing an answer…'),
      ).toBeNull(),
    )
    expect(box).toHaveValue('And how are its marks divided?')
  })

  it('does not show a question pending in one conversation in another', async () => {
    const OTHER = '0199a1b2-0000-7000-8000-0000000c0002'
    api({
      [`GET ${PATH}`]: () => json(detail([])),
      [`POST ${PATH}/messages`]: () => new Promise<Response>(() => {}),
      [`GET /api/v1/conversations/${OTHER}`]: () =>
        json({ ...detail([]), id: OTHER, title: 'Another conversation' }),
    })
    const { user, router } = renderApp(`/conversations/${CONVERSATION_ID}`)

    await user.type(
      await screen.findByRole('textbox', { name: 'Your question' }),
      'How long is the lab exam?{Enter}',
    )
    await screen.findByText('Searching your documents and writing an answer…')

    await router.navigate(`/conversations/${OTHER}`)

    expect(
      await screen.findByRole('heading', { name: 'Another conversation' }),
    ).toBeVisible()
    expect(screen.queryByText('How long is the lab exam?')).toBeNull()
    expect(
      screen.queryByText('Searching your documents and writing an answer…'),
    ).toBeNull()
    expect(screen.getByRole('button', { name: 'Ask' })).toBeVisible()
  })

  it('moves focus into the panel when it opens and back to the chip when it closes (UI-8)', async () => {
    api({
      [`GET ${PATH}`]: () =>
        json(detail([question('How long is the lab exam?'), answer()])),
      [`GET /api/v1/documents/${DOC_ID}/content`]: () =>
        new Response(new Blob(['%PDF-1.7'])),
    })
    vi.stubGlobal(
      'URL',
      Object.assign(URL, {
        createObjectURL: vi.fn(() => 'blob:document'),
        revokeObjectURL: vi.fn(),
      }),
    )
    const { user } = renderApp(`/conversations/${CONVERSATION_ID}`)

    const chip = (
      await screen.findAllByRole('button', {
        name: 'Source 1: Lab exam instructions, page 1',
      })
    )[1]
    await user.click(chip)
    expect(
      await screen.findByRole('heading', {
        name: 'Source 1: Lab exam instructions',
      }),
    ).toHaveFocus()

    await user.click(screen.getByRole('button', { name: 'Close' }))

    await vi.waitFor(() =>
      expect(screen.queryByRole('dialog', { name: /Source 1/ })).toBeNull(),
    )
    // React Aria hands focus back on the next animation frame.
    await vi.waitFor(() => expect(chip).toHaveFocus())
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
