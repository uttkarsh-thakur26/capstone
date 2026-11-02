import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, useLocation, useParams } from 'react-router'
import { ApiError } from '../api/client'
import type { ConversationDetail } from '../api/types'
import { Alert } from '../components/Alert'
import { Button } from '../components/Button'
import { messageOf } from '../components/formErrors'
import {
  askIn,
  conversationKeys,
  fetchConversation,
  UNTITLED,
} from '../conversations/api'
import { AskForm } from '../conversations/AskForm'
import { CitationPanel } from '../conversations/CitationPanel'
import { MessageList, type SelectedCitation } from '../conversations/Messages'

/**
 * One conversation (UI-5): its messages, the question box, and the cited passage beside them. A
 * conversation of another user answers 404 like a missing one (CONV-1).
 */
export function ConversationPage() {
  const { id = '' } = useParams()
  const location = useLocation()
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

  if (conversation.isPending) {
    return <p role="status">Opening the conversation…</p>
  }
  if (conversation.isError) {
    if (
      conversation.error instanceof ApiError &&
      conversation.error.status === 404
    ) {
      return (
        <section className="space-y-3">
          <h1 className="text-2xl font-semibold">Conversation not found</h1>
          <p>It may have been deleted.</p>
          <Link className="underline underline-offset-2" to="/conversations">
            See your conversations
          </Link>
        </section>
      )
    }
    return (
      <div className="space-y-2">
        <Alert>
          {messageOf(
            conversation.error,
            'The conversation could not be opened.',
          )}
        </Alert>
        <Button variant="secondary" onPress={() => void conversation.refetch()}>
          Try again
        </Button>
      </div>
    )
  }

  const { data } = conversation
  const citation = selectedCitation(data, selected)
  const startError = (location.state as { askError?: string } | null)?.askError
  return (
    <section className="space-y-6">
      <div className="flex flex-wrap items-baseline justify-between gap-2">
        <h1 className="text-2xl font-semibold wrap-anywhere">
          {data.title ?? UNTITLED}
        </h1>
        <Link
          className="text-sm underline underline-offset-2"
          to="/conversations"
        >
          All conversations
        </Link>
      </div>
      <div
        className={
          citation ? 'grid gap-6 lg:grid-cols-2 lg:items-start' : 'space-y-6'
        }
      >
        <div className="space-y-6">
          {data.messages.length === 0 && !ask.isPending && (
            <p className="text-slate-700 dark:text-slate-300">
              No questions yet. Ask one below.
            </p>
          )}
          <MessageList
            messages={data.messages}
            selected={selected}
            onSelect={setSelected}
            pendingQuestion={ask.isPending ? ask.variables : undefined}
          />
          {ask.isError && (
            <Alert>
              {messageOf(ask.error, 'The question could not be sent.')}
            </Alert>
          )}
          {startError && !ask.isError && !ask.isPending && (
            <Alert>{startError}</Alert>
          )}
          <AskForm onAsk={ask.mutateAsync} isPending={ask.isPending} />
        </div>
        {citation && (
          <div className="lg:sticky lg:top-4">
            <CitationPanel
              citation={citation}
              onClose={() => setSelected(null)}
            />
          </div>
        )}
      </div>
    </section>
  )
}

function selectedCitation(
  data: ConversationDetail,
  selected: SelectedCitation | null,
) {
  if (!selected) return null
  const citation = data.messages
    .find((message) => message.id === selected.messageId)
    ?.citations.find((c) => c.ordinal === selected.ordinal)
  return citation?.available ? citation : null
}
