import { screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import type { components } from '../api/schema'
import { fakeApi, json, META, session } from '../test/fakeApi'
import { renderApp } from '../test/renderApp'

type Analytics = components['schemas']['AnalyticsResponse']

const DAYS = ['2026-11-07', '2026-11-08', '2026-11-09']

function analytics(withActivity: boolean): Analytics {
  const n = (value: number) => (withActivity ? value : 0)
  return {
    period: {
      from: DAYS[0],
      to: DAYS[2],
      zone: 'Asia/Kolkata',
      firstRecordedAt: withActivity ? '2026-11-07T04:00:00Z' : undefined,
    },
    totals: {
      questions: n(4),
      answered: n(3),
      noAnswer: n(1),
      failedQuestions: 0,
      insufficientEvidenceRate: withActivity ? 0.25 : undefined,
      searches: n(2),
      summaries: n(1),
      comparisons: 0,
      modelCalls: n(4),
      inputTokens: n(4800),
      cachedInputTokens: 0,
      outputTokens: n(320),
      costUsd: withActivity ? 0.00064 : 0,
    },
    questionsPerDay: DAYS.map((date, i) => ({
      date,
      answered: i === 2 ? n(3) : 0,
      noAnswer: i === 2 ? n(1) : 0,
      failed: 0,
    })),
    queriesPerDay: DAYS.map((date, i) => ({
      date,
      keywordSearches: 0,
      semanticSearches: 0,
      hybridSearches: i === 2 ? n(2) : 0,
      questions: i === 2 ? n(4) : 0,
      summaries: i === 2 ? n(1) : 0,
      comparisons: 0,
    })),
    costPerDay: DAYS.map((date, i) => ({
      date,
      modelCalls: i === 2 ? n(4) : 0,
      inputTokens: i === 2 ? n(4800) : 0,
      cachedInputTokens: 0,
      outputTokens: i === 2 ? n(320) : 0,
      costUsd: i === 2 && withActivity ? 0.00064 : 0,
    })),
    uploadsPerDay: DAYS.map((date) => ({ date, count: 0 })),
    latency: withActivity
      ? [
          {
            type: 'ASK',
            count: 4,
            totalP50: 1840,
            totalP95: 3020,
            retrievalP50: 130,
            retrievalP95: 160,
            generationP50: 1700,
            generationP95: 2900,
          },
        ]
      : [],
    documentsByStatus: [
      { name: 'UPLOADED', count: 0 },
      { name: 'PROCESSING', count: 0 },
      { name: 'INDEXED', count: 5 },
      { name: 'FAILED', count: 0 },
    ],
    failuresByCode: [],
    usersByRole: [
      { name: 'ADMIN', count: 1 },
      { name: 'FACULTY', count: 2 },
      { name: 'STUDENT', count: 2 },
    ],
    mostCitedDocuments: withActivity
      ? [{ documentId: 'doc-1', title: 'Lab exam instructions', citations: 3 }]
      : [],
    system: {
      chatModel: 'gpt-6-luna',
      embeddingModel: 'qwen3-embedding:0.6b',
      embeddingDimensions: 1024,
      documents: 5,
      chunks: 46,
    },
  }
}

function analyticsApi(body: Analytics) {
  return fakeApi({
    'GET /api/v1/meta': () => json(META),
    'POST /api/v1/auth/refresh': () => json(session('ADMIN')),
    'GET /api/v1/admin/analytics': () => json(body),
  })
}

describe('AdminAnalyticsPage', () => {
  it('shows the server’s figures, in the reader’s zone', async () => {
    const server = analyticsApi(analytics(true))

    renderApp('/admin/analytics')

    const questions = (await screen.findByText('Questions', { selector: 'dt' }))
      .parentElement!
    expect(questions).toHaveTextContent('4')
    expect(questions).toHaveTextContent('3 answered, 1 with no answer found')
    expect(screen.getByText('25%')).toBeVisible()
    expect(
      screen.getByText('AI cost', { selector: 'dt' }).parentElement,
    ).toHaveTextContent('$0.000640')
    expect(
      screen.getByRole('link', { name: 'Lab exam instructions' }),
    ).toBeVisible()
    expect(screen.getByText('1840 ms / 3020 ms')).toBeVisible()
    expect(screen.getByText('gpt-6-luna')).toBeVisible()
    const request = server.to('GET', '/api/v1/admin/analytics')[0]
    expect(request.url.searchParams.get('days')).toBe('30')
    expect(request.url.searchParams.get('zone')).toBe(
      Intl.DateTimeFormat().resolvedOptions().timeZone,
    )
  })

  it('has every chart’s numbers as a table', async () => {
    analyticsApi(analytics(true))
    renderApp('/admin/analytics')

    const table = await screen.findByRole('table', {
      name: 'Questions per day by outcome',
    })
    const today = within(table).getAllByRole('row').at(-1)!
    expect(today).toHaveTextContent('9 Nov')
    expect(today).toHaveTextContent('310')
  })

  it('says when there is nothing yet, with no made-up numbers', async () => {
    analyticsApi(analytics(false))
    renderApp('/admin/analytics')

    expect(
      await screen.findByText('No questions in this period.'),
    ).toBeVisible()
    expect(screen.getByText('No model calls in this period.')).toBeVisible()
    expect(screen.getByText('No uploads in this period.')).toBeVisible()
    expect(
      screen.getByText(/No question or search has been recorded yet/),
    ).toBeVisible()
    expect(screen.getByText('—')).toBeVisible()
  })
})
