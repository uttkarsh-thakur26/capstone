import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router'
import { api } from '../api/client'
import type { DocumentPage, UserResponse } from '../api/types'
import { useAuth } from '../auth/useAuth'
import { Alert } from '../components/Alert'
import { ButtonLink } from '../components/Button'
import { Card } from '../components/Card'
import { messageOf } from '../components/formErrors'
import { PageHeader } from '../components/PageHeader'
import { EmptyState, ErrorState, LoadingState } from '../components/states'
import { LINK } from '../components/ui'
import {
  conversationKeys,
  fetchConversations,
  UNTITLED,
} from '../conversations/api'
import { AskForm } from '../conversations/AskForm'
import { Thinking } from '../conversations/Messages'
import { useStartConversation } from '../conversations/useStartConversation'
import { formatDateTime } from '../documents/format'
import { ArchivedBadge, StatusBadge } from '../documents/StatusBadge'

/** How many recent documents and conversations the dashboard lists. */
const RECENT = 5

/**
 * The dashboard (16.2): a quick question, the newest documents the user can read and their latest
 * conversations. Both lists come from the same endpoints as their full pages, so the server's
 * permission filter applies here too (ACL-E2).
 */
export function HomePage() {
  const { state } = useAuth()
  if (state.status !== 'authenticated') return null
  const { user } = state

  return (
    <section className="space-y-8">
      <PageHeader
        title={`Welcome, ${user.displayName}`}
        description="Ask a question, or pick up where you left off."
      />
      <QuickAsk />
      <div className="grid gap-6 lg:grid-cols-2">
        <RecentDocuments user={user} />
        <RecentConversations />
      </div>
      {user.role === 'ADMIN' && (
        <Card title="Administration">
          <p className="text-sm text-muted">
            Change roles and turn accounts on or off.{' '}
            <Link className={LINK} to="/admin/users">
              Manage users and roles
            </Link>
          </p>
        </Card>
      )}
    </section>
  )
}

function QuickAsk() {
  const start = useStartConversation()
  return (
    <Card title="Ask the assistant">
      <div className="space-y-3">
        <AskForm onAsk={start.mutateAsync} isPending={start.isPending} />
        {start.isPending && <Thinking />}
        {start.isError && (
          <Alert>
            {messageOf(start.error, 'The conversation could not be started.')}
          </Alert>
        )}
      </div>
    </Card>
  )
}

function RecentDocuments({ user }: { user: UserResponse }) {
  const recent = useQuery({
    queryKey: ['documents', 'recent', RECENT],
    queryFn: ({ signal }) =>
      api<DocumentPage>(
        `/api/v1/documents?page=0&size=${RECENT}&archived=false`,
        { signal },
      ),
  })
  const canUpload = user.role !== 'STUDENT'
  return (
    <Card
      title="Recent documents"
      action={
        <Link className={LINK} to="/documents">
          All documents
        </Link>
      }
    >
      {recent.isPending && <LoadingState>Loading documents…</LoadingState>}
      {recent.isError && (
        <ErrorState onRetry={() => void recent.refetch()}>
          {messageOf(recent.error, 'The documents could not be loaded.')}
        </ErrorState>
      )}
      {recent.data && recent.data.items.length === 0 && (
        <EmptyState
          action={
            canUpload && (
              <ButtonLink to="/documents/upload" variant="secondary" size="sm">
                Upload a document
              </ButtonLink>
            )
          }
        >
          {canUpload
            ? 'No documents yet. Upload one and share it.'
            : 'Nothing has been shared with you yet.'}
        </EmptyState>
      )}
      {recent.data && recent.data.items.length > 0 && (
        <ul className="-my-2 divide-y divide-line">
          {recent.data.items.map((document) => (
            <li
              key={document.id}
              className="flex items-start justify-between gap-3 py-2.5"
            >
              <div className="min-w-0">
                <Link
                  to={`/documents/${document.id}`}
                  className={`${LINK} text-sm wrap-anywhere`}
                >
                  {document.title}
                </Link>
                <p className="text-xs text-muted">
                  {document.owner.displayName} ·{' '}
                  {formatDateTime(document.createdAt)}
                </p>
              </div>
              <span className="flex shrink-0 flex-wrap justify-end gap-1">
                <StatusBadge status={document.status} />
                {document.archived && <ArchivedBadge />}
              </span>
            </li>
          ))}
        </ul>
      )}
    </Card>
  )
}

function RecentConversations() {
  const recent = useQuery({
    queryKey: conversationKeys.list(0, RECENT),
    queryFn: ({ signal }) => fetchConversations(0, RECENT, signal),
  })
  return (
    <Card
      title="Recent conversations"
      action={
        <Link className={LINK} to="/conversations">
          All conversations
        </Link>
      }
    >
      {recent.isPending && <LoadingState>Loading…</LoadingState>}
      {recent.isError && (
        <ErrorState onRetry={() => void recent.refetch()}>
          {messageOf(recent.error, 'Your conversations could not be loaded.')}
        </ErrorState>
      )}
      {recent.data && recent.data.items.length === 0 && (
        <EmptyState>
          None yet. Your questions and their answers are kept here.
        </EmptyState>
      )}
      {recent.data && recent.data.items.length > 0 && (
        <ul className="-my-2 divide-y divide-line">
          {recent.data.items.map((conversation) => (
            <li key={conversation.id} className="py-2.5">
              <Link
                to={`/conversations/${conversation.id}`}
                className={`${LINK} text-sm wrap-anywhere`}
              >
                {conversation.title ?? UNTITLED}
              </Link>
              <p className="text-xs text-muted">
                {formatDateTime(conversation.updatedAt)}
              </p>
            </li>
          ))}
        </ul>
      )}
    </Card>
  )
}
