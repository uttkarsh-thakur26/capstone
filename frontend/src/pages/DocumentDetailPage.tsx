import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, type ReactNode } from 'react'
import { Link, useLocation, useNavigate, useParams } from 'react-router'
import { api, ApiError } from '../api/client'
import type {
  DocumentResponse,
  GrantsResponse,
  UpdateGrantsRequest,
} from '../api/types'
import { Alert } from '../components/Alert'
import { Button } from '../components/Button'
import { ConfirmDialog } from '../components/ConfirmDialog'
import { messageOf } from '../components/formErrors'
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

type Notice = { tone: 'success' | 'error'; text: string }

/**
 * One document (DOC-5): its description, status and file, with the actions the user may take. A
 * document the user cannot read answers 404 like one that does not exist, so both look the same
 * here (ACL-8). Editing, sharing, archiving and deleting appear only for the owner and admins; the
 * server enforces the same rule (ACL-2, UI-2).
 */
export function DocumentDetailPage() {
  const { id = '' } = useParams()
  const location = useLocation()
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
    return <p role="status">Loading the document…</p>
  }
  if (document.isError) {
    if (document.error instanceof ApiError && document.error.status === 404) {
      return <DocumentNotFound />
    }
    return (
      <div className="space-y-2">
        <Alert>
          {messageOf(document.error, 'The document could not be loaded.')}
        </Alert>
        <Button variant="secondary" onPress={() => void document.refetch()}>
          Try again
        </Button>
      </div>
    )
  }

  const doc = document.data
  const replace = (updated: DocumentResponse) => {
    queryClient.setQueryData(['document', id], updated)
    void queryClient.invalidateQueries({ queryKey: ['documents'] })
  }

  return (
    <article className="space-y-8">
      <div className="space-y-2">
        <p className="text-sm">
          <Link to="/documents" className="underline-offset-2 hover:underline">
            Documents
          </Link>
        </p>
        <h1 className="text-2xl font-semibold">{doc.title}</h1>
        <div className="flex flex-wrap gap-2">
          <StatusBadge status={doc.status} />
          {doc.archived && <ArchivedBadge />}
        </div>
      </div>

      {notice && <Alert tone={notice.tone}>{notice.text}</Alert>}

      <StatusExplanation document={doc} />

      <dl className="grid gap-x-6 gap-y-3 text-sm sm:grid-cols-[max-content_1fr]">
        <Field label="Description">{doc.description ?? '—'}</Field>
        <Field label="Category">{doc.category ?? '—'}</Field>
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

      {doc.canManage && (
        <SharingSection documentId={doc.id} onNotice={setNotice} />
      )}
    </article>
  )
}

function Field({ label, children }: { label: string; children: ReactNode }) {
  return (
    <>
      <dt className="font-medium text-slate-700 dark:text-slate-300">
        {label}
      </dt>
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
    <p
      className="text-sm text-slate-700 dark:text-slate-300"
      role="status"
      aria-live="polite"
    >
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
    <section aria-labelledby="sharing" className="space-y-4">
      <h2 id="sharing" className="text-lg font-semibold">
        Sharing
      </h2>
      <p className="text-sm text-slate-700 dark:text-slate-300">
        You and the admins can always read this document. Changes apply as soon
        as they are saved.
      </p>
      {grants.isPending && <p role="status">Loading sharing…</p>}
      {grants.isError && (
        <div className="space-y-2">
          <Alert>
            {messageOf(grants.error, 'Sharing could not be loaded.')}
          </Alert>
          <Button variant="secondary" onPress={() => void grants.refetch()}>
            Try again
          </Button>
        </div>
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
      <h1 className="text-2xl font-semibold">Document not found</h1>
      <p>There is no document at this address.</p>
      <p>
        <Link className="underline underline-offset-2" to="/documents">
          Go to the documents
        </Link>
      </p>
    </section>
  )
}
