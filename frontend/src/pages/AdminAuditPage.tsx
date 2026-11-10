import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { useDeferredValue, useState } from 'react'
import { Input, Label, SearchField, TextField } from 'react-aria-components'
import { Link } from 'react-router'
import { api } from '../api/client'
import type { components } from '../api/schema'
import { FilterSelect } from '../components/FilterSelect'
import { messageOf } from '../components/formErrors'
import { PageHeader } from '../components/PageHeader'
import { Pager } from '../components/Pager'
import { EmptyState, ErrorState, LoadingState } from '../components/states'
import {
  CARD,
  FIELD,
  LABEL,
  LINK,
  TABLE_HEAD,
  TABLE_ROW,
} from '../components/ui'
import { formatDateTime } from '../documents/format'

type AuditPage = components['schemas']['PageResponseAuditEntryResponse']
type AuditEntry = components['schemas']['AuditEntryResponse']
type Action = AuditEntry['action']
type Outcome = AuditEntry['outcome']

const PAGE_SIZE = 50

/** Each recorded action of 14.2 in words. */
const ACTION_LABELS: Record<Action, string> = {
  LOGIN_SUCCESS: 'Signed in',
  LOGIN_FAILURE: 'Sign-in failed',
  LOGOUT: 'Signed out',
  USER_REGISTERED: 'Registered',
  ROLE_CHANGED: 'Role changed',
  USER_ENABLED: 'Account enabled',
  USER_DISABLED: 'Account disabled',
  PASSWORD_CHANGED: 'Changed own password',
  PASSWORD_RESET: 'Password set by an admin',
  DOCUMENT_UPLOADED: 'Document uploaded',
  DOCUMENT_DOWNLOADED: 'Document opened or downloaded',
  DOCUMENT_UPDATED: 'Document details edited',
  DOCUMENT_DELETED: 'Document deleted',
  DOCUMENT_REPROCESSED: 'Document processed again',
  DOCUMENT_ARCHIVED: 'Document archived or restored',
  GRANTS_CHANGED: 'Sharing changed',
  ACCESS_DENIED: 'Access denied',
  AI_QUERY: 'Question asked',
  SUMMARY_REQUESTED: 'Summary requested',
  COMPARISON_REQUESTED: 'Comparison requested',
  REINDEX_STARTED: 'Re-index started',
}

const OUTCOME_LABELS: Record<Outcome, string> = {
  SUCCESS: 'Done',
  DENIED: 'Refused',
  FAILURE: 'Failed',
}

const OUTCOME_STYLES: Record<Outcome, string> = {
  SUCCESS: 'bg-subtle text-fg',
  DENIED: 'bg-amber-100 text-amber-900 dark:bg-amber-950 dark:text-amber-200',
  FAILURE: 'bg-red-100 text-red-900 dark:bg-red-950 dark:text-red-200',
}

/** The start of a calendar day in the reader's zone, as an instant. */
function startOfDay(date: string): string {
  const [year, month, day] = date.split('-').map(Number)
  return new Date(year, month - 1, day).toISOString()
}

function listPath(filters: {
  actor: string
  action: Action | 'ALL'
  outcome: Outcome | 'ALL'
  from: string
  to: string
  page: number
}): string {
  const params = new URLSearchParams({
    page: String(filters.page),
    size: String(PAGE_SIZE),
  })
  if (filters.actor) params.set('actor', filters.actor)
  if (filters.action !== 'ALL') params.set('action', filters.action)
  if (filters.outcome !== 'ALL') params.set('outcome', filters.outcome)
  if (filters.from) params.set('from', startOfDay(filters.from))
  if (filters.to) {
    // The last day is included: the period ends at the start of the next one.
    const [year, month, day] = filters.to.split('-').map(Number)
    params.set('to', new Date(year, month - 1, day + 1).toISOString())
  }
  return `/api/v1/admin/audit?${params}`
}

/**
 * The audit log for admins (AUD-3): who did what, when, from where, and how it ended, newest
 * first. It shows what was recorded and nothing more: entries never hold passwords, tokens,
 * document text, questions or answers (AUD-2).
 */
export function AdminAuditPage() {
  const [actorInput, setActorInput] = useState('')
  const [action, setAction] = useState<Action | 'ALL'>('ALL')
  const [outcome, setOutcome] = useState<Outcome | 'ALL'>('ALL')
  const [from, setFrom] = useState('')
  const [to, setTo] = useState('')
  const [page, setPage] = useState(0)
  const actor = useDeferredValue(actorInput.trim())
  const reversed = from !== '' && to !== '' && from > to

  const entries = useQuery({
    queryKey: ['admin-audit', actor, action, outcome, from, to, page],
    queryFn: ({ signal }) =>
      api<AuditPage>(listPath({ actor, action, outcome, from, to, page }), {
        signal,
      }),
    placeholderData: keepPreviousData,
    enabled: !reversed,
  })
  const filtered =
    <T,>(setter: (value: T) => void) =>
    (value: T) => {
      setter(value)
      setPage(0)
    }

  return (
    <section className="space-y-6">
      <PageHeader
        title="Audit log"
        description="Every sign-in, account change, document change, refusal and AI request, as it was recorded. Passwords, tokens, document text, questions and answers are never recorded."
      />

      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-5">
        <SearchField
          value={actorInput}
          onChange={(value) => {
            setActorInput(value)
            setPage(0)
          }}
          className="flex flex-col gap-1 lg:col-span-1"
        >
          <Label className={LABEL}>Who (email)</Label>
          <Input className={FIELD} />
        </SearchField>
        <FilterSelect
          label="Action"
          value={action}
          onChange={filtered(setAction)}
          options={[
            { id: 'ALL' as const, label: 'All actions' },
            ...(Object.keys(ACTION_LABELS) as Action[]).map((id) => ({
              id,
              label: ACTION_LABELS[id],
            })),
          ]}
        />
        <FilterSelect
          label="Outcome"
          value={outcome}
          onChange={filtered(setOutcome)}
          options={[
            { id: 'ALL' as const, label: 'All outcomes' },
            ...(Object.keys(OUTCOME_LABELS) as Outcome[]).map((id) => ({
              id,
              label: OUTCOME_LABELS[id],
            })),
          ]}
        />
        <TextField
          type="date"
          value={from}
          onChange={filtered(setFrom)}
          className="flex flex-col gap-1"
        >
          <Label className={LABEL}>From</Label>
          <Input className={FIELD} />
        </TextField>
        <TextField
          type="date"
          value={to}
          onChange={filtered(setTo)}
          className="flex flex-col gap-1"
        >
          <Label className={LABEL}>To (included)</Label>
          <Input className={FIELD} />
        </TextField>
      </div>

      {reversed && (
        <ErrorState>The first day must not be after the last one.</ErrorState>
      )}
      {!reversed && entries.isPending && (
        <LoadingState>Loading the audit log…</LoadingState>
      )}
      {!reversed && entries.isError && (
        <ErrorState onRetry={() => void entries.refetch()}>
          {messageOf(entries.error, 'The audit log could not be loaded.')}
        </ErrorState>
      )}
      {!reversed && entries.data && entries.data.items.length === 0 && (
        <EmptyState>No entries match these filters.</EmptyState>
      )}
      {!reversed && entries.data && entries.data.items.length > 0 && (
        <>
          <div className={`${CARD} overflow-x-auto`}>
            <table className="w-full text-left text-sm">
              <caption className="sr-only">
                Audit entries, page {entries.data.page + 1} of{' '}
                {entries.data.totalPages}
              </caption>
              <thead className={TABLE_HEAD}>
                <tr>
                  <th scope="col" className="px-4 py-3">
                    When
                  </th>
                  <th scope="col" className="px-4 py-3">
                    What
                  </th>
                  <th scope="col" className="hidden px-4 py-3 md:table-cell">
                    Who
                  </th>
                  <th scope="col" className="hidden px-4 py-3 lg:table-cell">
                    Details
                  </th>
                </tr>
              </thead>
              <tbody>
                {entries.data.items.map((entry) => (
                  <Row key={entry.id} entry={entry} />
                ))}
              </tbody>
            </table>
          </div>
          <Pager
            page={page}
            totalPages={entries.data.totalPages}
            onPage={setPage}
          >
            Page {entries.data.page + 1} of{' '}
            {Math.max(entries.data.totalPages, 1)}, {entries.data.totalItems}{' '}
            entries
          </Pager>
        </>
      )}
    </section>
  )
}

function Row({ entry }: { entry: AuditEntry }) {
  const who = entry.actorEmail ?? 'The system'
  return (
    <tr className={`${TABLE_ROW} align-top`}>
      <td className="px-4 py-3 whitespace-nowrap text-muted">
        {formatDateTime(entry.occurredAt)}
      </td>
      <td className="px-4 py-3">
        <span className="flex flex-wrap items-center gap-2">
          <span className="font-medium">{ACTION_LABELS[entry.action]}</span>
          <span
            className={`rounded px-1.5 py-0.5 text-xs font-medium ${OUTCOME_STYLES[entry.outcome]}`}
          >
            {OUTCOME_LABELS[entry.outcome]}
          </span>
        </span>
        <Resource entry={entry} />
        {/* On narrow screens the hidden columns' facts go here. */}
        <span className="mt-1 block text-xs text-muted wrap-anywhere md:hidden">
          {who}
        </span>
        <Details entry={entry} className="mt-1 lg:hidden" />
      </td>
      <td className="hidden px-4 py-3 md:table-cell">
        <span className="block wrap-anywhere">{who}</span>
        {entry.ipAddress && (
          <span className="block text-xs text-muted">{entry.ipAddress}</span>
        )}
      </td>
      <td className="hidden px-4 py-3 lg:table-cell">
        <Details entry={entry} />
      </td>
    </tr>
  )
}

function Resource({ entry }: { entry: AuditEntry }) {
  if (!entry.resourceType || !entry.resourceId) return null
  if (
    entry.resourceType === 'DOCUMENT' &&
    entry.action !== 'DOCUMENT_DELETED'
  ) {
    return (
      <Link
        className={`${LINK} mt-1 block text-xs`}
        to={`/documents/${entry.resourceId}`}
      >
        Open the document
      </Link>
    )
  }
  return (
    <span className="mt-1 block text-xs text-muted wrap-anywhere">
      {entry.resourceType.toLowerCase()} {entry.resourceId}
    </span>
  )
}

/** The recorded facts as "name: value", in the order they were stored. */
function Details({
  entry,
  className = '',
}: {
  entry: AuditEntry
  className?: string
}) {
  const facts = Object.entries(entry.details)
  if (facts.length === 0) {
    return className ? null : <span className="text-muted">—</span>
  }
  return (
    <dl className={`space-y-0.5 text-xs ${className}`}>
      {facts.map(([key, value]) => (
        <div key={key} className="flex flex-wrap gap-x-1">
          <dt className="text-muted">{key}:</dt>
          <dd className="wrap-anywhere">
            {typeof value === 'string' ? value : JSON.stringify(value)}
          </dd>
        </div>
      ))}
    </dl>
  )
}
