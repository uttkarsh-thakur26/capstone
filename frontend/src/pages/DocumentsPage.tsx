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
import { Button } from '../components/Button'
import { Checkbox } from '../components/Checkbox'
import { messageOf } from '../components/formErrors'
import { formatBytes, formatDateTime, STATUS_LABELS } from '../documents/format'
import { ArchivedBadge, StatusBadge } from '../documents/StatusBadge'

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
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h1 className="text-2xl font-semibold">Documents</h1>
        {canUpload && (
          <Link
            to="/documents/upload"
            className="rounded-md bg-indigo-700 px-4 py-2 text-sm font-medium text-white outline-none hover:bg-indigo-800 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-indigo-600"
          >
            Upload a document
          </Link>
        )}
      </div>

      {notice && <Alert tone="success">{notice}</Alert>}

      <div className="flex flex-wrap items-end gap-4">
        <SearchField
          value={search}
          onChange={resetPage(setSearch)}
          className="flex w-full max-w-xs flex-col gap-1"
        >
          <Label className="text-sm font-medium">
            Search titles and descriptions
          </Label>
          <Input className="rounded-md border border-slate-400 bg-white px-3 py-2 outline-none data-[focused]:outline-2 data-[focused]:outline-indigo-600 dark:border-slate-500 dark:bg-slate-900" />
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
          <Checkbox isSelected={mine} onChange={resetPage(setMine)}>
            Only my uploads
          </Checkbox>
        )}
      </div>

      {documents.isPending && <p role="status">Loading documents…</p>}
      {documents.isError && (
        <div className="space-y-2">
          <Alert>
            {messageOf(documents.error, 'The documents could not be loaded.')}
          </Alert>
          <Button variant="secondary" onPress={() => void documents.refetch()}>
            Try again
          </Button>
        </div>
      )}
      {documents.data && documents.data.items.length === 0 && (
        <p>
          {filtered
            ? 'No documents match these filters.'
            : canUpload
              ? 'There are no documents yet. Upload the first one.'
              : 'No documents have been shared with you yet.'}
        </p>
      )}
      {documents.data && documents.data.items.length > 0 && (
        <>
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm">
              <caption className="sr-only">
                Documents, page {documents.data.page + 1} of{' '}
                {documents.data.totalPages}
              </caption>
              <thead className="border-b border-slate-300 dark:border-slate-700">
                <tr>
                  <th scope="col" className="py-2 pr-4">
                    Title
                  </th>
                  <th scope="col" className="py-2 pr-4">
                    Category
                  </th>
                  <th scope="col" className="py-2 pr-4">
                    Status
                  </th>
                  <th scope="col" className="py-2 pr-4">
                    Uploaded by
                  </th>
                  <th scope="col" className="py-2 pr-4">
                    Uploaded
                  </th>
                  <th scope="col" className="py-2">
                    Size
                  </th>
                </tr>
              </thead>
              <tbody>
                {documents.data.items.map((document) => (
                  <tr
                    key={document.id}
                    className="border-b border-slate-200 dark:border-slate-800"
                  >
                    <td className="py-2 pr-4">
                      <Link
                        to={`/documents/${document.id}`}
                        className="font-medium underline-offset-2 hover:underline"
                      >
                        {document.title}
                      </Link>
                    </td>
                    <td className="py-2 pr-4">{document.category ?? '—'}</td>
                    <td className="py-2 pr-4">
                      <span className="flex flex-wrap gap-1">
                        <StatusBadge status={document.status} />
                        {document.archived && <ArchivedBadge />}
                      </span>
                    </td>
                    <td className="py-2 pr-4">{document.owner.displayName}</td>
                    <td className="py-2 pr-4">
                      {formatDateTime(document.createdAt)}
                    </td>
                    <td className="py-2">{formatBytes(document.sizeBytes)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <nav aria-label="Pages" className="flex items-center gap-3 text-sm">
            <Button
              variant="secondary"
              isDisabled={page === 0}
              onPress={() => setPage((p) => p - 1)}
            >
              Previous
            </Button>
            <span>
              Page {documents.data.page + 1} of{' '}
              {Math.max(documents.data.totalPages, 1)},{' '}
              {documents.data.totalItems} documents
            </span>
            <Button
              variant="secondary"
              isDisabled={page + 1 >= documents.data.totalPages}
              onPress={() => setPage((p) => p + 1)}
            >
              Next
            </Button>
          </nav>
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
      <Label className="text-sm font-medium">{label}</Label>
      <AriaButton className="flex min-w-44 items-center justify-between gap-2 rounded-md border border-slate-400 bg-white px-3 py-2 text-sm outline-none data-[focus-visible]:outline-2 data-[focus-visible]:outline-indigo-600 dark:border-slate-500 dark:bg-slate-900">
        <SelectValue />
        <span aria-hidden="true">▾</span>
      </AriaButton>
      <Popover className="min-w-(--trigger-width) rounded-md border border-slate-300 bg-white shadow-lg dark:border-slate-600 dark:bg-slate-900">
        <ListBox className="p-1 outline-none">
          {options.map((option) => (
            <ListBoxItem
              key={option.id}
              id={option.id}
              className="cursor-default rounded px-3 py-1.5 text-sm outline-none data-[focused]:bg-indigo-100 data-[selected]:font-semibold dark:data-[focused]:bg-indigo-950"
            >
              {option.label}
            </ListBoxItem>
          ))}
        </ListBox>
      </Popover>
    </Select>
  )
}
