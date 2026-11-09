import { screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import type { ComparisonResponse } from '../api/types'
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

const OLD = documentSummary('Exam notice 2025', { status: 'INDEXED' })
const NEW = documentSummary('Exam notice 2026', { status: 'INDEXED' })

function side(documentId: string, title: string, page: number, quote: string) {
  return {
    quote,
    citation: {
      ordinal: 1,
      available: true,
      documentId,
      documentTitle: title,
      pageStart: page,
      pageEnd: page,
      snippet: quote,
    },
  }
}

const RESULT: ComparisonResponse = {
  left: { id: OLD.id, title: OLD.title },
  right: { id: NEW.id, title: NEW.title },
  passagesCompared: 3,
  identicalPassages: 1,
  differencesDropped: 2,
  modelCalls: 1,
  model: 'gpt-6-luna',
  promptVersion: 'compare-v1',
  differences: [
    {
      category: 'CHANGED_DATE',
      explanation: 'Results move from 9 October 2026 to 12 October 2026.',
      left: side(OLD.id, OLD.title, 3, 'published on 9 October 2026'),
      right: side(NEW.id, NEW.title, 3, 'published on 12 October 2026'),
    },
    {
      category: 'ADDED',
      explanation: 'Calculators are now allowed.',
      right: side(NEW.id, NEW.title, 4, 'Calculators of any kind are allowed.'),
    },
  ],
}

function api(
  compare: (request: RecordedRequest) => Response,
  items = [OLD, NEW],
) {
  return fakeApi({
    'GET /api/v1/meta': () => json(META),
    'POST /api/v1/auth/refresh': () => json(session('STUDENT')),
    'GET /api/v1/documents': () => json(documentPage(items)),
    'POST /api/v1/documents/compare': compare,
    [`GET /api/v1/documents/${NEW.id}/content`]: () =>
      new Response(new Blob(['%PDF-1.7'])),
  })
}

describe('ComparePage', () => {
  it('compares two picked documents and shows each difference with its page on both sides', async () => {
    const server = api(() => json(RESULT))
    const { user, router } = renderApp('/compare')

    const compare = await screen.findByRole('button', { name: 'Compare' })
    expect(compare).toBeDisabled()
    await user.click(
      screen.getByRole('combobox', { name: 'Earlier or reference document' }),
    )
    await user.click(
      await screen.findByRole('option', { name: 'Exam notice 2025' }),
    )
    await user.type(
      screen.getByRole('combobox', { name: 'Document to compare with it' }),
      '2026',
    )
    await user.click(
      await screen.findByRole('option', { name: 'Exam notice 2026' }),
    )
    expect(router.state.location.search).toBe(`?left=${OLD.id}&right=${NEW.id}`)
    // Nothing is compared until asked: it calls the model.
    expect(server.to('POST', '/api/v1/documents/compare')).toHaveLength(0)
    await user.click(compare)

    expect(
      await screen.findByText('2 differences found.', { exact: false }),
    ).toBeVisible()
    expect(server.to('POST', '/api/v1/documents/compare')[0].body).toEqual({
      leftId: OLD.id,
      rightId: NEW.id,
    })
    expect(screen.getByText(/1 passage reads the same in both/)).toBeVisible()
    const dates = screen
      .getByRole('heading', { name: /Changed dates/ })
      .closest('section') as HTMLElement
    expect(
      within(dates).getByText(
        'Results move from 9 October 2026 to 12 October 2026.',
      ),
    ).toBeVisible()
    expect(within(dates).getByText('published on 9 October 2026')).toBeVisible()
    expect(
      within(dates).getByText('published on 12 October 2026'),
    ).toBeVisible()
    expect(
      within(dates).getByRole('button', {
        name: 'Open Exam notice 2025, page 3',
      }),
    ).toBeVisible()
    const added = screen
      .getByRole('heading', { name: /Added/ })
      .closest('section') as HTMLElement
    expect(within(added).getByText('Not in this document.')).toBeVisible()

    await user.click(
      within(added).getByRole('button', {
        name: 'Open Exam notice 2026, page 4',
      }),
    )
    const panel = await screen.findByRole('dialog', {
      name: /Exam notice 2026/,
    })
    expect(
      within(panel).getByText('Calculators of any kind are allowed.'),
    ).toBeVisible()
  })

  it('does not compare a document from the address that the pickers do not list', async () => {
    const server = api(() => json(RESULT))
    renderApp(`/compare?left=not-listed&right=${NEW.id}`)

    const compare = await screen.findByRole('button', { name: 'Compare' })

    expect(compare).toBeDisabled()
    expect(server.to('POST', '/api/v1/documents/compare')).toHaveLength(0)
  })

  it('swaps the two sides', async () => {
    api(() => json(RESULT))
    const { user, router } = renderApp(
      `/compare?left=${OLD.id}&right=${NEW.id}`,
    )

    await user.click(await screen.findByRole('button', { name: 'Swap sides' }))

    expect(router.state.location.search).toBe(`?left=${NEW.id}&right=${OLD.id}`)
  })

  it("shows the server's reason when the documents are too large", async () => {
    api(() =>
      problem(
        422,
        'DOCUMENT_TOO_LARGE',
        '"Exam notice 2026" has 31 pages, too large to compare: the limit is 30 pages per document.',
      ),
    )
    const { user } = renderApp(`/compare?left=${OLD.id}&right=${NEW.id}`)

    await user.click(await screen.findByRole('button', { name: 'Compare' }))

    expect(
      await screen.findByText(/too large to compare: the limit is 30 pages/),
    ).toBeVisible()
    expect(
      screen.queryByRole('button', { name: 'Try again' }),
    ).not.toBeInTheDocument()
  })

  it('says a document is not available when the server answers 404', async () => {
    api(() =>
      problem(404, 'NOT_FOUND', 'The requested resource does not exist.'),
    )
    const { user } = renderApp(`/compare?left=${OLD.id}&right=${NEW.id}`)

    await user.click(await screen.findByRole('button', { name: 'Compare' }))

    expect(
      await screen.findByText(/One of these documents is not available/),
    ).toBeVisible()
  })

  it('says when there is nothing to compare', async () => {
    api(() => json(RESULT), [OLD])

    renderApp('/compare')

    expect(
      await screen.findByText(/Comparing needs two processed documents/),
    ).toBeVisible()
  })

  it('says when the documents read the same', async () => {
    api(() =>
      json({
        ...RESULT,
        differences: [],
        passagesCompared: 0,
        identicalPassages: 4,
        modelCalls: 0,
      }),
    )
    const { user } = renderApp(`/compare?left=${OLD.id}&right=${NEW.id}`)

    await user.click(await screen.findByRole('button', { name: 'Compare' }))

    expect(
      await screen.findByText('The two documents read the same.'),
    ).toBeVisible()
  })
})
