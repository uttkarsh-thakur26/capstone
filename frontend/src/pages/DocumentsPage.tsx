import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { useDeferredValue, useState } from 'react'
import {
  Button as AriaButton,
  Input,
  Label,
  ListBox,
  ListBoxItem,
  Popover,
  SearchField,
  Select,
  SelectValue,
} from 'react-aria-components'
import { Link, useLocation } from 'react-router'
import { api } from '../api/client'
import type { DocumentPage, DocumentStatus } from '../api/types'
import { DOCUMENT_STATUSES } from '../api/types'
import { useAuth } from '../auth/useAuth'
import { Alert } from '../components/Alert'
import { ButtonLink } from '../components/Button'
import { PageHeader } from '../components/PageHeader'
import { Pager } from '../components/Pager'
import { EmptyState, ErrorState, LoadingState } from '../components/states'
import { Checkbox } from '../components/Checkbox'
import { messageOf } from '../components/formErrors'
import {
  formatBytes,
  formatDateTime,
  isInProgress,
  STATUS_LABELS,
  STATUS_POLL_MS,
} from '../documents/format'
import { ArchivedBadge, StatusBadge } from '../documents/StatusBadge'
import {
  CARD,
  FIELD,
  LABEL,
  LINK,
  LIST_ITEM,
  POPOVER,
  SELECT_TRIGGER,
  TABLE_HEAD,
  TABLE_ROW,
} from '../components/ui'
import { categoryLabel } from '../documents/metadata'

const PAGE_SIZE = 20

type ArchivedFilter = 'current' | 'archived' | 'all'
type StatusFilter = DocumentStatus | 'any'

interface Filters {
  search: string
  status: StatusFilter
  archived: ArchivedFilter
  mine: boolean
}

function listPath(filters: Filters, page: number): string {
  const params = new URLSearchParams({
    page: String(page),
    size: String(PAGE_SIZE),
  })
  if (filters.search) params.set('query', filters.search)
  if (filters.status !== 'any') params.set('status', filters.status)
  if (filters.archived !== 'all')
    params.set('archived', String(filters.archived === 'archived'))
  if (filters.mine) params.set('mine', 'true')
  return `/api/v1/documents?${params}`
}

/**
 * The documents the user can read (DOC-4). The server filters by permission inside its query, so
 * this list and its count never include anything else (ACL-E2).
 */
export function DocumentsPage() {
  const { state } = useAuth()
  const location = useLocation()
  const notice = (location.state as { notice?: string } | null)?.notice
  const [search, setSearch] = useState('')
  const [status, setStatus] = useState<StatusFilter>('any')
  const [archived, setArchived] = useState<ArchivedFilter>('current')
  const [mine, setMine] = useState(false)
  const [page, setPage] = useState(0)
  const filters: Filters = {
    search: useDeferredValue(search.trim()),
    status,
    archived,
    mine,
  }

  const documents = useQuery({
    queryKey: ['documents', filters, page],
    queryFn: ({ signal }) =>
      api<DocumentPage>(listPath(filters, page), { signal }),
    placeholderData: keepPreviousData,
    // DOC-12: while a listed document is still queued or processed, ask again.
    refetchInterval: (query) =>
      query.state.data?.items.some((item) => isInProgress(item.status))
        ? STATUS_POLL_MS
        : false,
  })

  if (state.status !== 'authenticated') return null
  const canUpload = state.user.role !== 'STUDENT'
  const filtered =
    filters.search !== '' || status !== 'any' || archived !== 'current' || mine
  const resetPage =
    <T,>(set: (value: T) => void) =>
    (value: T) => {
      set(value)
      setPage(0)
    }

  return (
    <section className="space-y-6">
      <PageHeader
        title="Documents"
        description={
          state.user.role === 'ADMIN'
            ? 'Every document, whoever uploaded it.'
            : canUpload
              ? 'Your uploads and the documents shared with you.'
              : 'The documents shared with you.'
        }
        actions={
          canUpload && (
            <ButtonLink to="/documents/upload">Upload a document</ButtonLink>
          )
        }
      />

      {notice && <Alert tone="success">{notice}</Alert>}

      <div className="flex flex-wrap items-end gap-4">
        <SearchField
          value={search}
          onChange={resetPage(setSearch)}
          className="flex w-full flex-col gap-1 sm:max-w-xs"
        >
          <Label className={LABEL}>Search titles and descriptions</Label>
          <Input className={FIELD} />
        </SearchField>
        <FilterSelect
          label="Status"
          value={status}
          onChange={resetPage(setStatus)}
          options={[
            { id: 'any', label: 'Any status' },
            ...DOCUMENT_STATUSES.map((s) => ({
              id: s,
              label: STATUS_LABELS[s],
            })),
          ]}
        />
        <FilterSelect
          label="Archived"
          value={archived}
          onChange={resetPage(setArchived)}
          options={[
            { id: 'current', label: 'Current only' },
            { id: 'archived', label: 'Archived only' },
            { id: 'all', label: 'Current and archived' },
          ]}
        />
        {canUpload && (
          <div className="pb-2.5">
            <Checkbox isSelected={mine} onChange={resetPage(setMine)}>
              Only my uploads
            </Checkbox>
          </div>
        )}
      </div>

      {documents.isPending && (
        <LoadingState lines={5}>Loading documents…</LoadingState>
      )}
      {documents.isError && (
        <ErrorState onRetry={() => void documents.refetch()}>
          {messageOf(documents.error, 'The documents could not be loaded.')}
        </ErrorState>
      )}
      {documents.data && documents.data.items.length === 0 && (
        <EmptyState
          action={
            canUpload &&
            !filtered && (
              <ButtonLink to="/documents/upload" variant="secondary" size="sm">
                Upload a document
              </ButtonLink>
            )
          }
        >
          {filtered
            ? 'No documents match these filters.'
            : canUpload
              ? 'There are no documents yet. Upload the first one.'
              : 'No documents have been shared with you yet.'}
        </EmptyState>
      )}
      {documents.data && documents.data.items.length > 0 && (
        <>
          <div className={`${CARD} overflow-x-auto`}>
            <table className="w-full text-left text-sm">
              <caption className="sr-only">
                Documents, page {documents.data.page + 1} of{' '}
                {documents.data.totalPages}
              </caption>
              <thead className={TABLE_HEAD}>
                <tr>
                  <th scope="col" className="min-w-48 px-4 py-3">
                    Title
                  </th>
                  <th scope="col" className="hidden px-4 py-3 md:table-cell">
                    Category
                  </th>
                  <th scope="col" className="px-4 py-3">
                    Status
                  </th>
                  <th scope="col" className="hidden px-4 py-3 sm:table-cell">
                    Uploaded by
                  </th>
                  <th scope="col" className="hidden px-4 py-3 xl:table-cell">
                    Uploaded
                  </th>
                  <th scope="col" className="hidden px-4 py-3 md:table-cell">
                    Size
                  </th>
                </tr>
              </thead>
              <tbody>
                {documents.data.items.map((document) => (
                  <tr key={document.id} className={TABLE_ROW}>
                    <td className="px-4 py-3">
                      <Link
                        to={`/documents/${document.id}`}
                        className={`${LINK} wrap-anywhere`}
                      >
                        {document.title}
                      </Link>
                    </td>
                    <td className="hidden px-4 py-3 text-muted md:table-cell">
                      {categoryLabel(document.category)}
                    </td>
                    <td className="px-4 py-3">
                      <span className="flex flex-wrap gap-1">
                        <StatusBadge status={document.status} />
                        {document.archived && <ArchivedBadge />}
                      </span>
                    </td>
                    <td className="hidden px-4 py-3 sm:table-cell">
                      {document.owner.displayName}
                    </td>
                    <td className="hidden px-4 py-3 whitespace-nowrap text-muted xl:table-cell">
                      {formatDateTime(document.createdAt)}
                    </td>
                    <td className="hidden px-4 py-3 whitespace-nowrap text-muted md:table-cell">
                      {formatBytes(document.sizeBytes)}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <Pager
            page={page}
            totalPages={documents.data.totalPages}
            onPage={setPage}
          >
            Page {documents.data.page + 1} of{' '}
            {Math.max(documents.data.totalPages, 1)},{' '}
            {documents.data.totalItems} documents
          </Pager>
        </>
      )}
    </section>
  )
}

function FilterSelect<T extends string>({
  label,
  value,
  onChange,
  options,
}: {
  label: string
  value: T
  onChange: (value: T) => void
  options: { id: T; label: string }[]
}) {
  return (
    <Select
      value={value}
      onChange={(key) => {
        if (key !== null) onChange(key as T)
      }}
      className="flex flex-col gap-1"
    >
      <Label className={LABEL}>{label}</Label>
      <AriaButton className={SELECT_TRIGGER}>
        <SelectValue />
        <span aria-hidden="true">▾</span>
      </AriaButton>
      <Popover className={POPOVER}>
        <ListBox className="p-1 outline-none">
          {options.map((option) => (
            <ListBoxItem key={option.id} id={option.id} className={LIST_ITEM}>
              {option.label}
            </ListBoxItem>
          ))}
        </ListBox>
      </Popover>
    </Select>
  )
}
