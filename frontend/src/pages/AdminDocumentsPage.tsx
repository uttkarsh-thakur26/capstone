import {
  keepPreviousData,
  useMutation,
  useQuery,
  useQueryClient,
} from '@tanstack/react-query'
import { useDeferredValue, useState } from 'react'
import { Input, Label, SearchField } from 'react-aria-components'
import { Link } from 'react-router'
import { api } from '../api/client'
import type { components } from '../api/schema'
import type { DocumentStatus } from '../api/types'
import { DOCUMENT_STATUSES } from '../api/types'
import { Alert } from '../components/Alert'
import { Button } from '../components/Button'
import { ConfirmDialog } from '../components/ConfirmDialog'
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
import {
  formatDateTime,
  isInProgress,
  STATUS_LABELS,
  STATUS_POLL_MS,
} from '../documents/format'
import { ArchivedBadge, StatusBadge } from '../documents/StatusBadge'

type AdminDocument = components['schemas']['AdminDocumentResponse']
type AdminDocumentPage =
  components['schemas']['PageResponseAdminDocumentResponse']
type ReindexResponse = components['schemas']['ReindexResponse']

const PAGE_SIZE = 20

function listPath(query: string, status: DocumentStatus | 'ALL', page: number) {
  const params = new URLSearchParams({
    page: String(page),
    size: String(PAGE_SIZE),
  })
  if (query) params.set('query', query)
  if (status !== 'ALL') params.set('status', status)
  return `/api/v1/admin/documents?${params}`
}

function canReprocess(document: AdminDocument): boolean {
  return document.status === 'INDEXED' || document.status === 'FAILED'
}

/**
 * Every document for admins (14.1): status and why one failed, process one again, delete one,
 * open its sharing, and re-index all of them after a change of model or chunking. Single-document
 * actions use the document endpoints, which allow admins (ACL-2).
 */
export function AdminDocumentsPage() {
  const queryClient = useQueryClient()
  const [search, setSearch] = useState('')
  const [status, setStatus] = useState<DocumentStatus | 'ALL'>('ALL')
  const [page, setPage] = useState(0)
  const [notice, setNotice] = useState<{
    tone: 'success' | 'error'
    text: string
  } | null>(null)
  const [deleting, setDeleting] = useState<AdminDocument | null>(null)
  const [confirmReindex, setConfirmReindex] = useState(false)
  const query = useDeferredValue(search.trim())

  const documents = useQuery({
    queryKey: ['admin-documents', query, status, page],
    queryFn: ({ signal }) =>
      api<AdminDocumentPage>(listPath(query, status, page), { signal }),
    placeholderData: keepPreviousData,
    // While something is waiting or being processed, its status is read again.
    refetchInterval: (current) =>
      current.state.data?.items.some((d) => isInProgress(d.status))
        ? STATUS_POLL_MS
        : false,
  })

  const refresh = () =>
    queryClient.invalidateQueries({ queryKey: ['admin-documents'] })
  const failed = (fallback: string) => (error: Error) =>
    setNotice({ tone: 'error', text: messageOf(error, fallback) })

  const reprocess = useMutation({
    mutationFn: (document: AdminDocument) =>
      api(`/api/v1/documents/${document.id}/reprocess`, { method: 'POST' }),
    onSuccess: (_, document) => {
      setNotice({
        tone: 'success',
        text: `“${document.title}” is waiting to be processed again.`,
      })
      return refresh()
    },
    onError: failed('The document could not be sent for processing.'),
  })

  const remove = useMutation({
    mutationFn: (document: AdminDocument) =>
      api(`/api/v1/documents/${document.id}`, { method: 'DELETE' }),
    onSuccess: (_, document) => {
      setDeleting(null)
      setNotice({ tone: 'success', text: `“${document.title}” was deleted.` })
      return refresh()
    },
    onError: (error) => {
      setDeleting(null)
      failed('The document could not be deleted.')(error)
    },
  })

  const reindex = useMutation({
    mutationFn: () =>
      api<ReindexResponse>('/api/v1/admin/reindex', { method: 'POST' }),
    onSuccess: (result) => {
      setConfirmReindex(false)
      setNotice({
        tone: 'success',
        text:
          `${result.queued} ${result.queued === 1 ? 'document is' : 'documents are'} waiting to be processed again` +
          (result.skipped > 0
            ? `; ${result.skipped} ${result.skipped === 1 ? 'was' : 'were'} already waiting or being processed.`
            : '.'),
      })
      return refresh()
    },
    onError: (error) => {
      setConfirmReindex(false)
      failed('Re-indexing could not be started.')(error)
    },
  })

  const busy = reprocess.isPending || remove.isPending || reindex.isPending

  return (
    <section className="space-y-6">
      <PageHeader
        title="All documents"
        description="Every document, with its status and why it failed. Reprocess or delete one, or re-index them all after the search model or its settings change."
        actions={
          <Button
            variant="secondary"
            onPress={() => setConfirmReindex(true)}
            isDisabled={busy}
          >
            Re-index all
          </Button>
        }
      />

      <div className="flex flex-wrap items-end gap-4">
        <SearchField
          value={search}
          onChange={(value) => {
            setSearch(value)
            setPage(0)
          }}
          className="flex w-full max-w-sm flex-col gap-1"
        >
          <Label className={LABEL}>Search titles and descriptions</Label>
          <Input className={FIELD} />
        </SearchField>
        <FilterSelect
          label="Status"
          value={status}
          onChange={(value) => {
            setStatus(value)
            setPage(0)
          }}
          options={[
            { id: 'ALL' as const, label: 'All statuses' },
            ...DOCUMENT_STATUSES.map((id) => ({
              id,
              label: STATUS_LABELS[id],
            })),
          ]}
        />
      </div>

      {notice && <Alert tone={notice.tone}>{notice.text}</Alert>}

      {documents.isPending && <LoadingState>Loading documents…</LoadingState>}
      {documents.isError && (
        <ErrorState onRetry={() => void documents.refetch()}>
          {messageOf(documents.error, 'The documents could not be loaded.')}
        </ErrorState>
      )}
      {documents.data && documents.data.items.length === 0 && (
        <EmptyState>
          {query || status !== 'ALL'
            ? 'No documents match these filters.'
            : 'No documents have been uploaded yet.'}
        </EmptyState>
      )}
      {documents.data && documents.data.items.length > 0 && (
        <>
          <div className={`${CARD} overflow-x-auto`}>
            <table className="w-full text-left text-sm">
              <caption className="sr-only">
                All documents, page {documents.data.page + 1} of{' '}
                {documents.data.totalPages}
              </caption>
              <thead className={TABLE_HEAD}>
                <tr>
                  <th scope="col" className="px-4 py-3">
                    Document
                  </th>
                  <th scope="col" className="px-4 py-3">
                    Status
                  </th>
                  <th scope="col" className="hidden px-4 py-3 md:table-cell">
                    Pages, passages
                  </th>
                  <th scope="col" className="hidden px-4 py-3 lg:table-cell">
                    Uploaded
                  </th>
                  <th scope="col" className="px-4 py-3">
                    <span className="sr-only">Actions</span>
                  </th>
                </tr>
              </thead>
              <tbody>
                {documents.data.items.map((document) => (
                  <tr key={document.id} className={`${TABLE_ROW} align-top`}>
                    <td className="px-4 py-3">
                      <Link
                        className={`${LINK} block wrap-anywhere`}
                        to={`/documents/${document.id}`}
                      >
                        {document.title}
                      </Link>
                      <span className="block text-xs text-muted">
                        {document.owner.displayName}
                      </span>
                    </td>
                    <td className="px-4 py-3">
                      <span className="flex flex-wrap gap-1.5">
                        <StatusBadge status={document.status} />
                        {document.archived && <ArchivedBadge />}
                      </span>
                      {document.status === 'FAILED' && (
                        <span className="mt-1 block max-w-xs text-xs text-muted">
                          {document.failureMessage ?? document.failureCode}
                        </span>
                      )}
                    </td>
                    <td className="hidden px-4 py-3 whitespace-nowrap tabular-nums md:table-cell">
                      {document.pageCount ?? '—'}, {document.chunkCount ?? '—'}
                    </td>
                    <td className="hidden px-4 py-3 whitespace-nowrap text-muted lg:table-cell">
                      {formatDateTime(document.createdAt)}
                    </td>
                    <td className="px-4 py-3">
                      <div className="flex flex-wrap justify-end gap-2">
                        {canReprocess(document) && (
                          <Button
                            size="sm"
                            variant="secondary"
                            onPress={() => {
                              setNotice(null)
                              reprocess.mutate(document)
                            }}
                            isDisabled={busy}
                            aria-label={`Process “${document.title}” again`}
                          >
                            Reprocess
                          </Button>
                        )}
                        <Button
                          size="sm"
                          variant="ghost"
                          onPress={() => {
                            setNotice(null)
                            setDeleting(document)
                          }}
                          isDisabled={busy}
                          aria-label={`Delete “${document.title}”`}
                        >
                          Delete
                        </Button>
                      </div>
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

      <ConfirmDialog
        isOpen={deleting !== null}
        onOpenChange={(open) => !open && setDeleting(null)}
        title="Delete this document?"
        confirmLabel="Delete"
        onConfirm={() => deleting && remove.mutate(deleting)}
        isPending={remove.isPending}
      >
        “{deleting?.title}” and its file are removed for everyone. Answers that
        cited it keep their text and show the source as no longer available.
        This cannot be undone.
      </ConfirmDialog>
      <ConfirmDialog
        isOpen={confirmReindex}
        onOpenChange={setConfirmReindex}
        title="Re-index every document?"
        confirmLabel="Re-index all"
        onConfirm={() => reindex.mutate()}
        isPending={reindex.isPending}
      >
        Every ready and failed document is processed again with the configured
        search model. Until a document is done, search and the assistant cannot
        use it, and its summary must be made again.
      </ConfirmDialog>
    </section>
  )
}
