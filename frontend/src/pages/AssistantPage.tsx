import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useNavigate } from 'react-router'
import { Alert } from '../components/Alert'
import { messageOf } from '../components/formErrors'
import {
  askIn,
  conversationKeys,
  createConversation,
} from '../conversations/api'
import { AskForm } from '../conversations/AskForm'
import { MessageList } from '../conversations/Messages'

/**
 * The assistant (UI-5): the first question starts a conversation, which then opens on its own
 * page. A question that failed is stored in the conversation too, so the page opens either way and
 * says what went wrong.
 */
export function AssistantPage() {
  const navigate = useNavigate()
  const queryClient = useQueryClient()

  const start = useMutation({
    mutationFn: async (question: string) => {
      const conversation = await createConversation()
      let askError: string | undefined
      try {
        await askIn(conversation.id, question)
      } catch (error) {
        askError = messageOf(error, 'The question could not be answered.')
      }
      await queryClient.invalidateQueries({ queryKey: conversationKeys.all })
      return { id: conversation.id, askError }
    },
    onSuccess: ({ id, askError }) =>
      navigate(`/conversations/${id}`, {
        state: askError ? { askError } : null,
      }),
  })

  return (
    <section className="space-y-6">
      <div className="space-y-2">
        <h1 className="text-2xl font-semibold">Assistant</h1>
        <p className="text-slate-700 dark:text-slate-300">
          Ask a question about the documents you can read. Every answer cites
          the passages it comes from, and says so when they do not answer it.
        </p>
      </div>
      {start.isPending && (
        <MessageList
          messages={[]}
          selected={null}
          onSelect={() => {}}
          pendingQuestion={start.variables}
        />
      )}
      {start.isError && (
        <Alert>
          {messageOf(start.error, 'The conversation could not be started.')}
        </Alert>
      )}
      <AskForm onAsk={start.mutateAsync} isPending={start.isPending} />
    </section>
  )
}
