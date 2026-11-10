import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { useState, type ReactNode } from 'react'
import { Link } from 'react-router'
import { api } from '../api/client'
import type { components } from '../api/schema'
import { BarChart } from '../components/BarChart'
import { Card } from '../components/Card'
import { FilterSelect } from '../components/FilterSelect'
import { messageOf } from '../components/formErrors'
import { PageHeader } from '../components/PageHeader'
import { EmptyState, ErrorState, LoadingState } from '../components/states'
import { CARD, LINK, TABLE_HEAD, TABLE_ROW } from '../components/ui'
import { formatDateTime, STATUS_LABELS } from '../documents/format'

type Analytics = components['schemas']['AnalyticsResponse']
type Count = components['schemas']['Count']

const PERIODS = [
  { id: '7', label: 'Last 7 days' },
  { id: '30', label: 'Last 30 days' },
  { id: '90', label: 'Last 90 days' },
] as const
type Period = (typeof PERIODS)[number]['id']

const TYPE_LABELS: Record<string, string> = {
  ASK: 'Questions',
  SEARCH_HYBRID: 'Searches, best match',
  SEARCH_SEMANTIC: 'Searches by meaning',
  SEARCH_KEYWORD: 'Searches by keywords',
  SUMMARIZE: 'Summaries',
  COMPARE: 'Comparisons',
}

/** The reader's own zone, so a day on the charts is the reader's day. */
function readerZone(): string {
  return Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC'
}

function formatUsd(value: number): string {
  if (value === 0) return '$0'
  return value < 0.01 ? `$${value.toFixed(6)}` : `$${value.toFixed(4)}`
}

function percent(value: number | undefined): string {
  return value === undefined || value === null
    ? '—'
    : `${Math.round(value * 100)}%`
}

function millis(value: number | undefined): string {
  return value === undefined || value === null ? '—' : `${Math.round(value)} ms`
}

/**
 * Analytics for admins (14.3). Every number comes from the server, which reads it from the query
 * log, the audit log and the current tables; nothing on this page is estimated or made up. Days
 * are the reader's calendar days.
 */
export function AdminAnalyticsPage() {
  const [period, setPeriod] = useState<Period>('30')
  const zone = readerZone()
  const analytics = useQuery({
    queryKey: ['admin-analytics', period, zone],
    queryFn: ({ signal }) =>
      api<Analytics>(
        `/api/v1/admin/analytics?${new URLSearchParams({ days: period, zone })}`,
        { signal },
      ),
    placeholderData: keepPreviousData,
  })
  const data = analytics.data

  return (
    <section className="space-y-6">
      <PageHeader
        title="Analytics"
        description="What people asked and searched, what it cost, and the state of the documents. Every figure is counted from the database."
        actions={
          <FilterSelect
            label="Period"
            value={period}
            onChange={setPeriod}
            options={[...PERIODS]}
          />
        }
      />

      {analytics.isPending && <LoadingState>Counting…</LoadingState>}
      {analytics.isError && (
        <ErrorState onRetry={() => void analytics.refetch()}>
          {messageOf(analytics.error, 'The figures could not be loaded.')}
        </ErrorState>
      )}
      {data && <Figures data={data} zone={zone} />}
    </section>
  )
}

function Figures({ data, zone }: { data: Analytics; zone: string }) {
  const { totals } = data
  const indexed =
    data.documentsByStatus.find((c) => c.name === 'INDEXED')?.count ?? 0
  const anyQueries =
    totals.questions + totals.searches + totals.summaries + totals.comparisons >
    0

  return (
    <div className="space-y-6">
      <p className="text-sm text-muted">
        {data.period.from === data.period.to
          ? `Today`
          : `From ${data.period.from} to ${data.period.to}`}
        , days in {zone}.{' '}
        {data.period.firstRecordedAt
          ? `Questions and searches are recorded since ${formatDateTime(data.period.firstRecordedAt)}.`
          : 'No question or search has been recorded yet.'}
      </p>

      <dl className="grid grid-cols-2 gap-4 lg:grid-cols-4">
        <Tile label="Questions" value={String(totals.questions)}>
          {totals.answered} answered, {totals.noAnswer} with no answer found
          {totals.failedQuestions > 0 && `, ${totals.failedQuestions} failed`}
        </Tile>
        <Tile
          label="No answer found"
          value={percent(totals.insufficientEvidenceRate)}
        >
          of the questions answered or not
        </Tile>
        <Tile label="AI cost" value={formatUsd(totals.costUsd)}>
          {totals.modelCalls} model calls,{' '}
          {(totals.inputTokens + totals.outputTokens).toLocaleString('en-GB')}{' '}
          tokens
        </Tile>
        <Tile label="Documents ready" value={String(indexed)}>
          {data.system.chunks.toLocaleString('en-GB')} passages for search
        </Tile>
      </dl>

      <div className="grid gap-6 xl:grid-cols-2">
        <Card title="Questions per day">
          {totals.questions === 0 ? (
            <EmptyState>No questions in this period.</EmptyState>
          ) : (
            <BarChart
              label="Questions per day by outcome"
              data={data.questionsPerDay.map((d) => ({
                date: d.date,
                values: [d.answered, d.noAnswer, d.failed],
              }))}
              series={[
                { label: 'Answered', color: 'var(--series-1)' },
                { label: 'No answer found', color: 'var(--series-2)' },
                { label: 'Failed', color: 'var(--series-3)' },
              ]}
            />
          )}
        </Card>
        <Card title="AI cost per day">
          {totals.modelCalls === 0 ? (
            <EmptyState>No model calls in this period.</EmptyState>
          ) : (
            <>
              <BarChart
                label="AI cost per day in US dollars"
                data={data.costPerDay.map((d) => ({
                  date: d.date,
                  values: [d.costUsd],
                }))}
                series={[{ label: 'Cost', color: 'var(--series-1)' }]}
                format={formatUsd}
              />
              <p className="mt-2 text-xs text-muted">
                From the tokens of every answer, summary and comparison, at the
                prices configured when each call ran. Embeddings run on this
                machine and cost nothing.
              </p>
            </>
          )}
        </Card>
        <Card title="Uploads per day">
          {data.uploadsPerDay.every((d) => d.count === 0) ? (
            <EmptyState>No uploads in this period.</EmptyState>
          ) : (
            <BarChart
              label="Uploads per day"
              data={data.uploadsPerDay.map((d) => ({
                date: d.date,
                values: [d.count],
              }))}
              series={[{ label: 'Uploads', color: 'var(--series-1)' }]}
            />
          )}
        </Card>
        <Card title="Queries by type">
          {!anyQueries ? (
            <EmptyState>
              Nothing was asked or searched in this period.
            </EmptyState>
          ) : (
            <QueryTable data={data} />
          )}
        </Card>
      </div>

      <Card title="Response times">
        {data.latency.length === 0 ? (
          <EmptyState>No timings in this period.</EmptyState>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm">
              <caption className="sr-only">
                Median and 95th percentile response times by type and stage
              </caption>
              <thead className={TABLE_HEAD}>
                <tr>
                  <th scope="col" className="px-3 py-2">
                    Type
                  </th>
                  <th scope="col" className="px-3 py-2 text-right">
                    Count
                  </th>
                  <th scope="col" className="px-3 py-2 text-right">
                    Total, median / 95%
                  </th>
                  <th
                    scope="col"
                    className="hidden px-3 py-2 text-right sm:table-cell"
                  >
                    Retrieval
                  </th>
                  <th
                    scope="col"
                    className="hidden px-3 py-2 text-right sm:table-cell"
                  >
                    Model
                  </th>
                </tr>
              </thead>
              <tbody>
                {data.latency.map((row) => (
                  <tr key={row.type} className={TABLE_ROW}>
                    <th scope="row" className="px-3 py-2 font-normal">
                      {TYPE_LABELS[row.type] ?? row.type}
                    </th>
                    <td className="px-3 py-2 text-right tabular-nums">
                      {row.count}
                    </td>
                    <td className="px-3 py-2 text-right whitespace-nowrap tabular-nums">
                      {millis(row.totalP50)} / {millis(row.totalP95)}
                    </td>
                    <td className="hidden px-3 py-2 text-right whitespace-nowrap tabular-nums sm:table-cell">
                      {millis(row.retrievalP50)} / {millis(row.retrievalP95)}
                    </td>
                    <td className="hidden px-3 py-2 text-right whitespace-nowrap tabular-nums sm:table-cell">
                      {millis(row.generationP50)} / {millis(row.generationP95)}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>

      <div className="grid gap-6 md:grid-cols-2 xl:grid-cols-4">
        <Card title="Documents by status">
          <Counts
            counts={data.documentsByStatus}
            label={(name) =>
              STATUS_LABELS[name as keyof typeof STATUS_LABELS] ?? name
            }
            empty="No documents yet."
          />
        </Card>
        <Card title="Failures by reason">
          <Counts
            counts={data.failuresByCode}
            label={(name) => name.replaceAll('_', ' ').toLowerCase()}
            empty="No document has failed."
          />
        </Card>
        <Card title="Users by role">
          <Counts
            counts={data.usersByRole}
            label={(name) => name.charAt(0) + name.slice(1).toLowerCase()}
            empty="No users yet."
          />
        </Card>
        <Card title="Most cited documents">
          {data.mostCitedDocuments.length === 0 ? (
            <p className="text-sm text-muted">No citations in this period.</p>
          ) : (
            <ol className="space-y-2 text-sm">
              {data.mostCitedDocuments.map((d) => (
                <li key={d.documentId} className="flex justify-between gap-3">
                  <Link
                    className={`${LINK} min-w-0 wrap-anywhere`}
                    to={`/documents/${d.documentId}`}
                  >
                    {d.title}
                  </Link>
                  <span className="shrink-0 tabular-nums">{d.citations}</span>
                </li>
              ))}
            </ol>
          )}
        </Card>
      </div>

      <Card title="System">
        <dl className="grid gap-x-6 gap-y-2 text-sm sm:grid-cols-2">
          <Pair label="Answers by">
            {data.system.chatModel ?? 'No chat model configured'}
          </Pair>
          <Pair label="Search by meaning">
            {data.system.embeddingModel}, {data.system.embeddingDimensions}{' '}
            dimensions
          </Pair>
          <Pair label="Documents">{data.system.documents}</Pair>
          <Pair label="Passages">
            {data.system.chunks.toLocaleString('en-GB')}
          </Pair>
        </dl>
      </Card>
    </div>
  )
}

function Tile({
  label,
  value,
  children,
}: {
  label: string
  value: string
  children: ReactNode
}) {
  return (
    <div className={`${CARD} p-4`}>
      <dt className="text-sm text-muted">{label}</dt>
      <dd className="mt-1 text-2xl font-semibold tabular-nums">{value}</dd>
      <dd className="mt-1 text-xs text-muted">{children}</dd>
    </div>
  )
}

function Pair({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="flex flex-wrap gap-x-2">
      <dt className="text-muted">{label}</dt>
      <dd className="font-medium wrap-anywhere">{children}</dd>
    </div>
  )
}

function Counts({
  counts,
  label,
  empty,
}: {
  counts: Count[]
  label: (name: string) => string
  empty: string
}) {
  if (counts.every((c) => c.count === 0)) {
    return <p className="text-sm text-muted">{empty}</p>
  }
  return (
    <ul className="space-y-1 text-sm">
      {counts.map((c) => (
        <li key={c.name} className="flex justify-between gap-3">
          <span>{label(c.name)}</span>
          <span className="tabular-nums">{c.count}</span>
        </li>
      ))}
    </ul>
  )
}

/** Queries per day by type (14.3): only the days with something in them, newest first. */
function QueryTable({ data }: { data: Analytics }) {
  const days = data.queriesPerDay
    .filter(
      (d) =>
        d.questions +
          d.keywordSearches +
          d.semanticSearches +
          d.hybridSearches +
          d.summaries +
          d.comparisons >
        0,
    )
    .reverse()
  return (
    <div className="max-h-72 overflow-auto">
      <table className="w-full text-left text-sm">
        <caption className="sr-only">Queries per day by type</caption>
        <thead className={TABLE_HEAD}>
          <tr>
            <th scope="col" className="px-2 py-2">
              Day
            </th>
            <th scope="col" className="px-2 py-2 text-right">
              Questions
            </th>
            <th scope="col" className="px-2 py-2 text-right">
              Searches
            </th>
            <th scope="col" className="px-2 py-2 text-right">
              Summaries
            </th>
            <th scope="col" className="px-2 py-2 text-right">
              Comparisons
            </th>
          </tr>
        </thead>
        <tbody>
          {days.map((d) => (
            <tr key={d.date} className={TABLE_ROW}>
              <th
                scope="row"
                className="px-2 py-2 font-normal whitespace-nowrap"
              >
                {d.date}
              </th>
              <td className="px-2 py-2 text-right tabular-nums">
                {d.questions}
              </td>
              <td
                className="px-2 py-2 text-right tabular-nums"
                title={`Best match ${d.hybridSearches}, meaning ${d.semanticSearches}, keywords ${d.keywordSearches}`}
              >
                {d.hybridSearches + d.semanticSearches + d.keywordSearches}
              </td>
              <td className="px-2 py-2 text-right tabular-nums">
                {d.summaries}
              </td>
              <td className="px-2 py-2 text-right tabular-nums">
                {d.comparisons}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
