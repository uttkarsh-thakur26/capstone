import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useState } from 'react'
import { Link, useLocation, useNavigate, useParams } from 'react-router'
import { ApiError } from '../api/client'
import type { CitationResponse, ConversationDetail } from '../api/types'
import { Alert } from '../components/Alert'
import { ErrorState, LoadingState } from '../components/states'
import { PageHeader } from '../components/PageHeader'
import { messageOf } from '../components/formErrors'
import { rateLimitText, useRetryCountdown } from '../components/rateLimit'
import {
  askIn,
  conversationKeys,
  fetchConversation,
  UNTITLED,
} from '../conversations/api'
import { AskForm } from '../conversations/AskForm'
import { ChatLayout } from '../conversations/ChatLayout'
import { CitationPanel } from '../conversations/CitationPanel'
import { groupSources } from '../conversations/citations'
import { MessageList, type SelectedCitation } from '../conversations/Messages'
import { LINK } from '../components/ui'

/**
 * One conversation (UI-5): its messages, the question box, and the cited passage in a panel over them. A
 * conversation of another user answers 404 like a missing one (CONV-1).
 *
 * Keyed by the conversation, so moving to another one starts afresh: no pending question, error,
 * draft or open citation of the previous conversation is carried over.
 */
export function ConversationPage() {
  const { id = '' } = useParams()
  return <Conversation key={id} id={id} />
}

function Conversation({ id }: { id: string }) {
  const location = useLocation()
  const navigate = useNavigate()
  // Why the first answer failed, when the assistant page started this conversation. Kept here and
  // removed from the history entry, so it shows until the next question and not after a reload.
  const [startError] = useState(
    () => (location.state as { askError?: string } | null)?.askError,
  )
  useEffect(() => {
    if (location.state) {
      void navigate(location.pathname, { replace: true, state: null })
    }
  }, [location.state, location.pathname, navigate])
  const queryClient = useQueryClient()
  const [selected, setSelected] = useState<SelectedCitation | null>(null)

  const conversation = useQuery({
    queryKey: conversationKeys.detail(id),
    queryFn: ({ signal }) => fetchConversation(id, signal),
    retry: false,
  })

  const ask = useMutation({
    mutationFn: (question: string) => askIn(id, question),
    // A failed answer is stored too, so the conversation is read again either way, and the
    // question shows as pending until its answer is on screen.
    onSettled: () =>
      queryClient.invalidateQueries({ queryKey: conversationKeys.all }),
  })
  const wait = useRetryCountdown(ask.error)

  if (conversation.isPending) {
    return <LoadingState>Opening the conversation…</LoadingState>
  }
  if (conversation.isError) {
    if (
      conversation.error instanceof ApiError &&
      conversation.error.status === 404
    ) {
      return (
        <section className="space-y-3">
          <h1 className="text-2xl font-semibold tracking-tight">
            Conversation not found
          </h1>
          <p>It may have been deleted.</p>
          <Link className={LINK} to="/conversations">
            See your conversations
          </Link>
        </section>
      )
    }
    return (
      <ErrorState onRetry={() => void conversation.refetch()}>
        {messageOf(conversation.error, 'The conversation could not be opened.')}
      </ErrorState>
    )
  }

  const { data } = conversation
  const pending = ask.isPending ? ask.variables : undefined
  return (
    <>
      <ChatLayout
        messageCount={data.messages.length + (pending !== undefined ? 0.5 : 0)}
        header={
          <PageHeader
            title={data.title ?? UNTITLED}
            above={
              <Link className={LINK} to="/conversations">
                All conversations
              </Link>
            }
          />
        }
        composer={
          <AskForm
            onAsk={ask.mutateAsync}
            isPending={ask.isPending || (wait ?? 0) > 0}
          />
        }
      >
        {data.messages.length === 0 && pending === undefined && (
          <p className="text-sm text-muted">No questions yet. Ask one below.</p>
        )}
        <MessageList
          messages={data.messages}
          selected={selected}
          onSelect={setSelected}
          pendingQuestion={pending}
        />
        {ask.isError && (
          <div className="mt-6">
            <Alert>
              {wait !== null
                ? rateLimitText(ask.error, wait)
                : messageOf(ask.error, 'The question could not be sent.')}
            </Alert>
          </div>
        )}
        {startError && ask.isIdle && (
          <div className="mt-6">
            <Alert>{startError}</Alert>
          </div>
        )}
      </ChatLayout>
      <CitationPanel
        citations={selectedSources(data, selected)}
        onClose={() => setSelected(null)}
      />
    </>
  )
}

/**
 * The passages of the chosen citation's document and pages in the same answer: the chip opens
 * them together. A citation that is no longer available opens nothing (CONV-6).
 */
function selectedSources(
  data: ConversationDetail,
  selected: SelectedCitation | null,
): CitationResponse[] {
  if (!selected) return []
  const citations =
    data.messages.find((message) => message.id === selected.messageId)
      ?.citations ?? []
  const chosen = citations.find((c) => c.ordinal === selected.ordinal)
  if (!chosen?.available) return []
  return (
    groupSources(citations).find((group) => group.citations.includes(chosen))
      ?.citations ?? []
  )
}
