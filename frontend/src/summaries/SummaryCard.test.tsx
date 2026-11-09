import { screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import type { SummaryResponse } from '../api/types'
import { DOC_ID, documentDetail } from '../test/documents'
import { fakeApi, json, META, problem, session } from '../test/fakeApi'
import { renderApp } from '../test/renderApp'

const DOC_PATH = `/api/v1/documents/${DOC_ID}`

const READY: SummaryResponse = {
  documentId: DOC_ID,
  status: 'READY',
  model: 'gpt-6-luna',
  promptVersion: 'summary-v1',
  path: 'MAP_REDUCE',
  modelCalls: 4,
  finishedAt: '2026-11-08T07:00:00Z',
  summary: {
    overview: 'The policy covers exams and attendance [1][2].',
    keyPoints: [
      'Attendance must be 75 percent [2].',
      'Attendance is counted per subject [2].',
    ],
    rules: ['Phones must be switched off [1].'],
    dates: ['Results are published on 9 October 2026 [3].'],
    citations: [
      {
        ordinal: 1,
        available: true,
        documentId: DOC_ID,
        documentTitle: 'Examination Policy 2026',
        pageStart: 2,
        pageEnd: 2,
        snippet: 'Phones must be switched off during the exam.',
      },
      {
        ordinal: 2,
        available: true,
        documentId: DOC_ID,
        documentTitle: 'Examination Policy 2026',
        pageStart: 4,
        pageEnd: 4,
        snippet: 'Attendance of 75 percent is required.',
      },
      {
        ordinal: 3,
        available: true,
        documentId: DOC_ID,
        documentTitle: 'Examination Policy 2026',
        pageStart: 9,
        pageEnd: 9,
        snippet: 'Results are published on 9 October 2026.',
      },
    ],
  },
}

function api(summary: () => Response, routes = {}) {
  return fakeApi({
    'GET /api/v1/meta': () => json(META),
    'POST /api/v1/auth/refresh': () => json(session('STUDENT')),
    [`GET ${DOC_PATH}`]: () =>
      json(documentDetail({ status: 'INDEXED', canManage: false })),
    [`GET ${DOC_PATH}/summary`]: summary,
    [`GET ${DOC_PATH}/content`]: () => new Response(new Blob(['%PDF-1.7'])),
    ...routes,
  })
}

describe('SummaryCard', () => {
  it('starts a summary, waits while it is made, then shows its sections with source chips', async () => {
    let made = false
    const server = api(
      () => json(made ? READY : { documentId: DOC_ID, status: 'NONE' }),
      {
        [`POST ${DOC_PATH}/summary`]: () => {
          made = true
          return json({ documentId: DOC_ID, status: 'PENDING' }, 202)
        },
      },
    )
    const { user } = renderApp(`/documents/${DOC_ID}`)

    expect(
      await screen.findByText(/Get an overview, the key points/),
    ).toBeVisible()
    await user.click(screen.getByRole('button', { name: 'Summarize' }))

    expect(await screen.findByText(/^Summarizing…/)).toBeVisible()
    expect(server.to('POST', `${DOC_PATH}/summary`)).toHaveLength(1)
    // The card asks again while the summary is being made (SUM-5).
    expect(
      await screen.findByText(
        /The policy covers exams and attendance/,
        undefined,
        {
          timeout: 6000,
        },
      ),
    ).toBeVisible()
    for (const heading of [
      'Overview',
      'Key points',
      'Rules and requirements',
      'Important dates',
    ]) {
      expect(screen.getByRole('heading', { name: heading })).toBeVisible()
    }
    expect(
      screen.getByText(/Results are published on 9 October 2026/),
    ).toBeVisible()
    expect(screen.getByText(/part by part in 4 steps/)).toBeVisible()
    // Two consecutive items citing the same source each show its chip.
    const keyPoints = screen
      .getByRole('heading', { name: 'Key points' })
      .closest('section') as HTMLElement
    for (const item of within(keyPoints).getAllByRole('listitem')) {
      expect(
        within(item).getByRole('button', {
          name: 'Source 2: Examination Policy 2026, page 4',
        }),
      ).toBeVisible()
    }

    // The chip in the text and the one in the sources row open the same panel.
    await user.click(
      screen.getAllByRole('button', {
        name: 'Source 3: Examination Policy 2026, page 9',
      })[0],
    )
    const panel = await screen.findByRole('dialog', {
      name: /Source 3: Examination Policy 2026/,
    })
    expect(
      within(panel).getByText('Results are published on 9 October 2026.'),
    ).toBeVisible()
  }, 15_000)

  it('stops asking again and shows only the error when a read fails while the summary is made', async () => {
    let reads = 0
    const server = api(() => {
      reads += 1
      return reads === 1
        ? json({ documentId: DOC_ID, status: 'PENDING' })
        : problem(404, 'NOT_FOUND', 'The requested resource does not exist.')
    })
    renderApp(`/documents/${DOC_ID}`)

    expect(await screen.findByText(/^Summarizing…/)).toBeVisible()
    expect(
      await screen.findByText(
        'The requested resource does not exist.',
        undefined,
        {
          timeout: 6000,
        },
      ),
    ).toBeVisible()
    expect(screen.queryByText(/^Summarizing…/)).not.toBeInTheDocument()
    const after = server.to('GET', `${DOC_PATH}/summary`).length
    await new Promise((resolve) => setTimeout(resolve, 2500))
    expect(server.to('GET', `${DOC_PATH}/summary`)).toHaveLength(after)
  }, 15_000)

  it('hides the old failure while a new summary is being started', async () => {
    api(
      () =>
        json({
          documentId: DOC_ID,
          status: 'FAILED',
          failureMessage: 'The chat model gave no answer.',
        }),
      {
        // The request never answers, so the card stays in its starting state.
        [`POST ${DOC_PATH}/summary`]: () => new Promise<Response>(() => {}),
      },
    )
    const { user } = renderApp(`/documents/${DOC_ID}`)

    await user.click(await screen.findByRole('button', { name: 'Try again' }))

    expect(await screen.findByText(/^Summarizing…/)).toBeVisible()
    expect(
      screen.queryByText(/The summary could not be made/),
    ).not.toBeInTheDocument()
  })

  it('says why a summary failed and offers to try again', async () => {
    api(() =>
      json({
        documentId: DOC_ID,
        status: 'FAILED',
        failureMessage: 'The chat model gave no answer.',
      }),
    )

    renderApp(`/documents/${DOC_ID}`)

    expect(
      await screen.findByText(
        /The summary could not be made\. The chat model gave no answer\./,
      ),
    ).toBeVisible()
    expect(screen.getByRole('button', { name: 'Try again' })).toBeVisible()
  })

  it('shows the server refusing a document that is too long, in its own words', async () => {
    api(() => json({ documentId: DOC_ID, status: 'NONE' }), {
      [`POST ${DOC_PATH}/summary`]: () =>
        problem(
          422,
          'DOCUMENT_TOO_LARGE',
          'This document has 41 pages. Summaries are limited to 40 pages.',
        ),
    })
    const { user } = renderApp(`/documents/${DOC_ID}`)

    await user.click(await screen.findByRole('button', { name: 'Summarize' }))

    expect(
      await screen.findByText(
        'This document has 41 pages. Summaries are limited to 40 pages.',
      ),
    ).toBeVisible()
  })

  it('is not offered before the document is processed', async () => {
    fakeApi({
      'GET /api/v1/meta': () => json(META),
      'POST /api/v1/auth/refresh': () => json(session('STUDENT')),
      [`GET ${DOC_PATH}`]: () =>
        json(documentDetail({ status: 'UPLOADED', canManage: false })),
    })

    renderApp(`/documents/${DOC_ID}`)

    expect(
      await screen.findByRole('heading', { name: 'Examination Policy 2026' }),
    ).toBeVisible()
    expect(
      screen.queryByRole('heading', { name: 'Summary' }),
    ).not.toBeInTheDocument()
  })
})
