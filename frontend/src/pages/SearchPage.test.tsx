import { screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import type { SearchResult } from '../api/types'
import { DOC_ID, documentDetail } from '../test/documents'
import {
  fakeApi,
  json,
  META,
  problem,
  session,
  type RecordedRequest,
} from '../test/fakeApi'
import { renderApp } from '../test/renderApp'

const PASSAGE =
  'Candidates who are late by more than thirty minutes are not admitted to the examination hall.'

function result(overrides: Partial<SearchResult> = {}): SearchResult {
  return {
    documentId: DOC_ID,
    title: 'Examination Policy 2026',
    pageStart: 4,
    pageEnd: 4,
    text: PASSAGE,
    highlights: [],
    score: 0.8123,
    ...overrides,
  }
}

function api(
  search: (request: RecordedRequest) => Response,
  routes: Record<string, (request: RecordedRequest) => Response> = {},
) {
  return fakeApi({
    'GET /api/v1/meta': () => json(META),
    'POST /api/v1/auth/refresh': () => json(session('STUDENT')),
    'POST /api/v1/search': search,
    ...routes,
  })
}

describe('SearchPage', () => {
  it('searches by meaning and shows title, page, passage and similarity, linking to the page', async () => {
    const server = api(() =>
      json({
        mode: 'SEMANTIC',
        results: [result(), result({ pageStart: 7, pageEnd: 8, score: 0.5 })],
      }),
    )
    const { user, router } = renderApp('/search')

    expect(
      await screen.findByText(/Type a question or some words/),
    ).toBeVisible()
    expect(screen.getByRole('radio', { name: 'Meaning' })).toBeChecked()
    await user.type(
      screen.getByRole('searchbox', {
        name: 'Search the documents you can read',
      }),
      '  can I enter the exam late?  ',
    )
    await user.click(screen.getByRole('button', { name: 'Search' }))

    expect(
      await screen.findByText('2 passages, best match first.'),
    ).toBeVisible()
    expect(server.to('POST', '/api/v1/search')[0].body).toEqual({
      query: 'can I enter the exam late?',
      mode: 'SEMANTIC',
    })
    expect(router.state.location.search).toBe('?q=can+I+enter+the+exam+late%3F')
    const items = screen.getAllByRole('listitem')
    expect(items).toHaveLength(2)
    expect(within(items[0]).getByText('Page 4 · Similarity 0.81')).toBeVisible()
    expect(within(items[0]).getByText(PASSAGE)).toBeVisible()
    expect(
      within(items[0]).getByRole('link', { name: 'Examination Policy 2026' }),
    ).toHaveAttribute('href', `/documents/${DOC_ID}?page=4`)
    expect(
      within(items[1]).getByText('Pages 7–8 · Similarity 0.50'),
    ).toBeVisible()
    expect(items[0].querySelector('mark')).toBeNull()
  })

  it('searches by keywords from the address and marks the matched words', async () => {
    const start = PASSAGE.indexOf('late')
    const server = api(() =>
      json({
        mode: 'KEYWORD',
        results: [
          result({
            highlights: [{ start, end: start + 4 }],
            score: 0.1,
          }),
        ],
      }),
    )

    renderApp('/search?q=late&mode=keywords')

    expect(
      await screen.findByText('1 passage, best match first.'),
    ).toBeVisible()
    expect(screen.getByRole('radio', { name: 'Keywords' })).toBeChecked()
    expect(
      screen.getByText(/Best for exact terms, codes and numbers/),
    ).toBeVisible()
    expect(screen.getByText('Page 4 · Keyword rank 0.10')).toBeVisible()
    const marks = document.querySelectorAll('mark')
    expect([...marks].map((mark) => mark.textContent)).toEqual(['late'])
    expect(server.to('POST', '/api/v1/search')[0].body).toEqual({
      query: 'late',
      mode: 'KEYWORD',
    })
  })

  it('runs the same query again when the mode changes', async () => {
    const server = api((request) =>
      json({
        mode: (request.body as { mode: string }).mode,
        results: [result()],
      }),
    )
    const { user, router } = renderApp('/search?q=late')
    await screen.findByText('1 passage, best match first.')

    await user.click(screen.getByRole('radio', { name: 'Keywords' }))

    expect(await screen.findByText(/Keyword rank/)).toBeVisible()
    expect(server.to('POST', '/api/v1/search').map((r) => r.body)).toEqual([
      { query: 'late', mode: 'SEMANTIC' },
      { query: 'late', mode: 'KEYWORD' },
    ])
    expect(router.state.location.search).toBe('?q=late&mode=keywords')
  })

  it('shortens a long passage and shows all of it on request', async () => {
    const long = `${'Opening words of the rule. '.repeat(20)}The final sentence.`
    api(() => json({ mode: 'SEMANTIC', results: [result({ text: long })] }))
    const { user } = renderApp('/search?q=rule')

    const show = await screen.findByRole('button', {
      name: 'Show whole passage',
    })
    expect(screen.queryByText(/The final sentence\./)).not.toBeInTheDocument()
    expect(show).toHaveAttribute('aria-expanded', 'false')

    await user.click(show)

    expect(screen.getByText(/The final sentence\./)).toBeVisible()
    expect(screen.getByRole('button', { name: 'Show less' })).toHaveAttribute(
      'aria-expanded',
      'true',
    )
  })

  it('says when keyword search finds nothing', async () => {
    api(() => json({ mode: 'KEYWORD', results: [] }))

    renderApp('/search?q=zebra&mode=keywords')

    expect(
      await screen.findByText(/No passage contains all of these words/),
    ).toBeVisible()
    expect(screen.queryByRole('listitem')).not.toBeInTheDocument()
  })

  it('shows the server message when the search fails, and tries again', async () => {
    let calls = 0
    api(() => {
      calls += 1
      return calls === 1
        ? problem(
            503,
            'MODEL_UNAVAILABLE',
            'Search by meaning is not available right now. Try again later, or search by keywords.',
          )
        : json({ mode: 'SEMANTIC', results: [result()] })
    })
    const { user } = renderApp('/search?q=late')

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Search by meaning is not available right now',
    )
    await user.click(screen.getByRole('button', { name: 'Try again' }))

    expect(
      await screen.findByText('1 passage, best match first.'),
    ).toBeVisible()
  })

  it('searches one document when the address names it, and can widen to all', async () => {
    const server = api(() => json({ mode: 'SEMANTIC', results: [result()] }), {
      [`GET /api/v1/documents/${DOC_ID}`]: () =>
        json(documentDetail({ status: 'INDEXED' })),
    })
    const { user, router } = renderApp(`/search?q=late&documentId=${DOC_ID}`)

    expect(
      await screen.findByText('Examination Policy 2026', {
        selector: 'strong',
      }),
    ).toBeVisible()
    await screen.findByText('1 passage, best match first.')
    expect(server.to('POST', '/api/v1/search')[0].body).toEqual({
      query: 'late',
      mode: 'SEMANTIC',
      documentIds: [DOC_ID],
    })

    await user.click(
      screen.getByRole('button', { name: 'Search all documents' }),
    )

    expect(router.state.location.search).toBe('?q=late')
    await screen.findByText('1 passage, best match first.')
    expect(server.to('POST', '/api/v1/search')[1].body).toEqual({
      query: 'late',
      mode: 'SEMANTIC',
    })
  })

  it('says only that the chosen document is not available when the server answers 404', async () => {
    api(
      () => problem(404, 'NOT_FOUND', 'The requested resource does not exist.'),
      {
        [`GET /api/v1/documents/${DOC_ID}`]: () =>
          problem(404, 'NOT_FOUND', 'The requested resource does not exist.'),
      },
    )

    renderApp(`/search?q=late&documentId=${DOC_ID}`)

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'The document to search in is not available.',
    )
    expect(screen.getByText('a document that is not available')).toBeVisible()
  })

  it('is reachable from the main navigation', async () => {
    api(() => json({ mode: 'SEMANTIC', results: [] }))
    const { user, router } = renderApp('/')

    await user.click(await screen.findByRole('link', { name: 'Search' }))

    expect(router.state.location.pathname).toBe('/search')
    expect(await screen.findByRole('heading', { name: 'Search' })).toBeVisible()
  })
})
