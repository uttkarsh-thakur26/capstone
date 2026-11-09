import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, type ReactNode } from 'react'
import {
  Link,
  useLocation,
  useNavigate,
  useParams,
  useSearchParams,
} from 'react-router'
import { api, ApiError } from '../api/client'
import type {
  DocumentResponse,
  GrantsResponse,
  UpdateGrantsRequest,
} from '../api/types'
import { Alert } from '../components/Alert'
import { Button, ButtonLink } from '../components/Button'
import { ConfirmDialog } from '../components/ConfirmDialog'
import { ErrorState, LoadingState } from '../components/states'
import { PageHeader } from '../components/PageHeader'
import { messageOf } from '../components/formErrors'
import { DocumentViewer } from '../documents/DocumentViewer'
import { SummaryCard } from '../summaries/SummaryCard'
import { downloadDocument } from '../documents/download'
import { EditDocumentDialog } from '../documents/EditDocumentDialog'
import {
  formatBytes,
  formatDate,
  formatDateTime,
  isInProgress,
  STATUS_POLL_MS,
} from '../documents/format'
import { GrantsEditor } from '../documents/GrantsEditor'
import { fromGrants, toGrantRequests, type Sharing } from '../documents/sharing'
import { ArchivedBadge, StatusBadge } from '../documents/StatusBadge'
import { CARD, LINK } from '../components/ui'
import { categoryLabel } from '../documents/metadata'

type Notice = { tone: 'success' | 'error'; text: string }

/**
 * One document (DOC-5): its description, status and file, with the actions the user may take. A
 * document the user cannot read answers 404 like one that does not exist, so both look the same
 * here (ACL-8). Editing, sharing, archiving and deleting appear only for the owner and admins; the
 * server enforces the same rule (ACL-2, UI-2). `?page=` opens the viewer at that page, which is
 * where a search result links to (UI-7).
 */
export function DocumentDetailPage() {
  const { id = '' } = useParams()
  const location = useLocation()
  const [params] = useSearchParams()
  const queryClient = useQueryClient()
  const [notice, setNotice] = useState<Notice | null>(() => {
    const text = (location.state as { notice?: string } | null)?.notice
    return text ? { tone: 'success', text } : null
  })

  const document = useQuery({
    queryKey: ['document', id],
    queryFn: ({ signal }) =>
      api<DocumentResponse>(`/api/v1/documents/${id}`, { signal }),
    // DOC-12, UI-4: ask again while processing is still to come, then stop.
    refetchInterval: (query) =>
      isInProgress(query.state.data?.status) ? STATUS_POLL_MS : false,
  })

  if (document.isPending) {
    return <LoadingState>Loading the document…</LoadingState>
  }
  if (document.isError) {
    if (document.error instanceof ApiError && document.error.status === 404) {
      return <DocumentNotFound />
    }
    return (
      <ErrorState onRetry={() => void document.refetch()}>
        {messageOf(document.error, 'The document could not be loaded.')}
      </ErrorState>
    )
  }

  const doc = document.data
  const replace = (updated: DocumentResponse) => {
    queryClient.setQueryData(['document', id], updated)
    void queryClient.invalidateQueries({ queryKey: ['documents'] })
  }

  return (
    <article className="space-y-8">
      <div className="space-y-3">
        <PageHeader
          title={doc.title}
          above={
            <Link to="/documents" className={LINK}>
              Documents
            </Link>
          }
        />
        <div className="flex flex-wrap gap-2">
          <StatusBadge status={doc.status} />
          {doc.archived && <ArchivedBadge />}
        </div>
      </div>

      {notice && <Alert tone={notice.tone}>{notice.text}</Alert>}

      <StatusExplanation document={doc} />

      <dl
        className={`${CARD} grid gap-x-6 gap-y-3 p-4 text-sm sm:grid-cols-[max-content_1fr] sm:p-5`}
      >
        <Field label="Description">{doc.description ?? '—'}</Field>
        <Field label="Category">{categoryLabel(doc.category)}</Field>
        <Field label="Effective date">
          {doc.documentDate ? formatDate(doc.documentDate) : '—'}
        </Field>
        <Field label="File">
          {doc.originalFilename}, {formatBytes(doc.sizeBytes)}
          {doc.pageCount != null &&
            `, ${doc.pageCount} ${doc.pageCount === 1 ? 'page' : 'pages'}`}
        </Field>
        <Field label="Search index">
          {doc.status === 'INDEXED' && doc.chunkCount != null
            ? `${doc.chunkCount} ${doc.chunkCount === 1 ? 'passage' : 'passages'}${
                doc.indexedAt
                  ? `, indexed ${formatDateTime(doc.indexedAt)}`
                  : ''
              }`
            : '—'}
        </Field>
        <Field label="Uploaded by">{doc.owner.displayName}</Field>
        <Field label="Uploaded">{formatDateTime(doc.createdAt)}</Field>
        <Field label="Last changed">{formatDateTime(doc.updatedAt)}</Field>
      </dl>

      <Actions document={doc} onChanged={replace} onNotice={setNotice} />

      {doc.status === 'INDEXED' && <SummaryCard documentId={doc.id} />}

      <ViewerSection
        document={doc}
        requestedPage={pageOf(params.get('page'), doc.pageCount)}
      />

      {doc.canManage && (
        <SharingSection documentId={doc.id} onNotice={setNotice} />
      )}
    </article>
  )
}

/** A page number from the address, within the document when its length is known. */
function pageOf(
  value: string | null,
  pageCount: number | undefined,
): number | null {
  const page = Number(value)
  if (!value || !Number.isInteger(page) || page < 1) return null
  return pageCount ? Math.min(page, pageCount) : page
}

function ViewerSection({
  document,
  requestedPage,
}: {
  document: DocumentResponse
  requestedPage: number | null
}) {
  const [opened, setOpened] = useState(false)
  const open = opened || requestedPage !== null
  return (
    <section
      aria-labelledby="viewer-heading"
      className={`${CARD} space-y-3 p-4 sm:p-5`}
    >
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h2 id="viewer-heading" className="text-base font-semibold">
          {requestedPage === null ? 'View' : `Page ${requestedPage}`}
        </h2>
        {!open && (
          <Button variant="secondary" onPress={() => setOpened(true)}>
            View the document
          </Button>
        )}
      </div>
      {open && (
        <>
          <DocumentViewer
            documentId={document.id}
            title={document.title}
            page={requestedPage ?? 1}
          />
          <p className="text-sm text-muted">
            If the document does not appear above, your browser cannot show it
            inside the page: use Download instead.
          </p>
        </>
      )}
    </section>
  )
}

function Field({ label, children }: { label: string; children: ReactNode }) {
  return (
    <>
      <dt className="font-medium text-muted">{label}</dt>
      <dd className="whitespace-pre-line">{children}</dd>
    </>
  )
}

/** What the status means for the reader (DOC-12). */
function StatusExplanation({ document }: { document: DocumentResponse }) {
  if (document.status === 'FAILED') {
    return (
      <Alert>
        Processing failed:{' '}
        {document.failureMessage ?? 'no reason was recorded.'}
      </Alert>
    )
  }
  return (
    <p className="text-sm text-muted" role="status" aria-live="polite">
      {document.status === 'UPLOADED' &&
        'Waiting in the processing queue. This page updates by itself.'}
      {document.status === 'PROCESSING' &&
        'Being processed: its text is read, split into passages and indexed. This page updates by itself.'}
      {document.status === 'INDEXED' &&
        !document.archived &&
        'The document is processed and used for search and answers.'}
      {document.archived &&
        ' Archived: it stays readable and downloadable, but search and answers leave it out.'}
    </p>
  )
}

function Actions({
  document,
  onChanged,
  onNotice,
}: {
  document: DocumentResponse
  onChanged: (document: DocumentResponse) => void
  onNotice: (notice: Notice | null) => void
}) {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [editing, setEditing] = useState(false)
  const [confirmingDelete, setConfirmingDelete] = useState(false)

  const download = useMutation({
    mutationFn: () => downloadDocument(document.id, document.originalFilename),
    onError: (error) =>
      onNotice({
        tone: 'error',
        text: messageOf(error, 'The download failed. Try again.'),
      }),
  })

  const archive = useMutation({
    mutationFn: (archived: boolean) =>
      api<DocumentResponse>(`/api/v1/documents/${document.id}`, {
        method: 'PATCH',
        body: { archived },
      }),
    onSuccess: (updated) => {
      onChanged(updated)
      onNotice({
        tone: 'success',
        text: updated.archived
          ? 'The document is archived.'
          : 'The document is no longer archived.',
      })
    },
    onError: (error) =>
      onNotice({
        tone: 'error',
        text: messageOf(error, 'The change was not saved. Try again.'),
      }),
  })

  // DOC-10: back into the queue; it leaves search until it is indexed again.
  const reprocess = useMutation({
    mutationFn: () =>
      api<DocumentResponse>(`/api/v1/documents/${document.id}/reprocess`, {
        method: 'POST',
      }),
    onSuccess: (updated) => {
      onChanged(updated)
      onNotice({
        tone: 'success',
        text: 'The document is queued for processing again.',
      })
    },
    onError: (error) =>
      onNotice({
        tone: 'error',
        text: messageOf(error, 'The document could not be queued. Try again.'),
      }),
  })

  const remove = useMutation({
    mutationFn: () =>
      api<void>(`/api/v1/documents/${document.id}`, { method: 'DELETE' }),
    onSuccess: async () => {
      queryClient.removeQueries({ queryKey: ['document', document.id] })
      await queryClient.invalidateQueries({ queryKey: ['documents'] })
      navigate('/documents', {
        replace: true,
        state: { notice: `“${document.title}” was deleted.` },
      })
    },
    onError: (error) => {
      setConfirmingDelete(false)
      onNotice({
        tone: 'error',
        text: messageOf(error, 'The document was not deleted. Try again.'),
      })
    },
  })

  return (
    <div className="flex flex-wrap gap-3">
      <Button
        onPress={() => {
          onNotice(null)
          download.mutate()
        }}
        isDisabled={download.isPending}
      >
        {download.isPending ? 'Downloading…' : 'Download'}
      </Button>
      {document.status === 'INDEXED' && !document.archived && (
        <ButtonLink
          variant="secondary"
          to={`/search?documentId=${document.id}`}
        >
          Search in this document
        </ButtonLink>
      )}
      {document.status === 'INDEXED' && (
        <ButtonLink variant="secondary" to={`/compare?left=${document.id}`}>
          Compare with…
        </ButtonLink>
      )}
      {document.canManage && (
        <>
          <Button variant="secondary" onPress={() => setEditing(true)}>
            Edit details
          </Button>
          {(document.status === 'INDEXED' || document.status === 'FAILED') && (
            <Button
              variant="secondary"
              isDisabled={reprocess.isPending}
              onPress={() => {
                onNotice(null)
                reprocess.mutate()
              }}
            >
              {reprocess.isPending ? 'Queuing…' : 'Reprocess'}
            </Button>
          )}
          <Button
            variant="secondary"
            isDisabled={archive.isPending}
            onPress={() => {
              onNotice(null)
              archive.mutate(!document.archived)
            }}
          >
            {document.archived ? 'Bring back from archive' : 'Archive'}
          </Button>
          <Button variant="danger" onPress={() => setConfirmingDelete(true)}>
            Delete
          </Button>
          <EditDocumentDialog
            document={document}
            isOpen={editing}
            onOpenChange={setEditing}
            onSaved={(saved) => {
              onChanged(saved)
              onNotice({ tone: 'success', text: 'The details were saved.' })
            }}
          />
          <ConfirmDialog
            isOpen={confirmingDelete}
            onOpenChange={setConfirmingDelete}
            title="Delete this document?"
            confirmLabel="Delete"
            onConfirm={() => remove.mutate()}
            isPending={remove.isPending}
          >
            “{document.title}” and its file are removed for everyone. This
            cannot be undone.
          </ConfirmDialog>
        </>
      )}
    </div>
  )
}

/** DOC-8: who may read the document, replaced as a whole when saved (ACL-7). */
function SharingSection({
  documentId,
  onNotice,
}: {
  documentId: string
  onNotice: (notice: Notice | null) => void
}) {
  const queryClient = useQueryClient()
  const grants = useQuery({
    queryKey: ['grants', documentId],
    queryFn: ({ signal }) =>
      api<GrantsResponse>(`/api/v1/documents/${documentId}/grants`, {
        signal,
      }),
  })
  // The user's unsaved choice; null while it equals what the server has.
  const [draft, setDraft] = useState<Sharing | null>(null)

  const save = useMutation({
    mutationFn: (sharing: Sharing) =>
      api<GrantsResponse>(`/api/v1/documents/${documentId}/grants`, {
        method: 'PUT',
        body: {
          grants: toGrantRequests(sharing),
        } satisfies UpdateGrantsRequest,
      }),
    onSuccess: (saved) => {
      queryClient.setQueryData(['grants', documentId], saved)
      setDraft(null)
      onNotice({ tone: 'success', text: 'Sharing was saved.' })
    },
    onError: (error) =>
      onNotice({
        tone: 'error',
        text: messageOf(error, 'Sharing was not saved. Try again.'),
      }),
  })

  return (
    <section
      aria-labelledby="sharing"
      className={`${CARD} space-y-4 p-4 sm:p-5`}
    >
      <h2 id="sharing" className="text-base font-semibold">
        Sharing
      </h2>
      <p className="text-sm text-muted">
        You and the admins can always read this document. Changes apply as soon
        as they are saved.
      </p>
      {grants.isPending && <LoadingState>Loading sharing…</LoadingState>}
      {grants.isError && (
        <ErrorState onRetry={() => void grants.refetch()}>
          {messageOf(grants.error, 'Sharing could not be loaded.')}
        </ErrorState>
      )}
      {grants.data && (
        <>
          <GrantsEditor
            value={draft ?? fromGrants(grants.data.grants)}
            onChange={setDraft}
            isDisabled={save.isPending}
          />
          <div className="flex gap-3">
            <Button
              isDisabled={draft === null || save.isPending}
              onPress={() => {
                onNotice(null)
                if (draft) save.mutate(draft)
              }}
            >
              Save sharing
            </Button>
            {draft !== null && (
              <Button
                variant="secondary"
                isDisabled={save.isPending}
                onPress={() => setDraft(null)}
              >
                Undo changes
              </Button>
            )}
          </div>
        </>
      )}
    </section>
  )
}

function DocumentNotFound() {
  return (
    <section className="mx-auto max-w-xl space-y-3 py-10">
      <h1 className="text-2xl font-semibold tracking-tight">
        Document not found
      </h1>
      <p>There is no document at this address.</p>
      <p>
        <Link className={LINK} to="/documents">
          Go to the documents
        </Link>
      </p>
    </section>
  )
}
