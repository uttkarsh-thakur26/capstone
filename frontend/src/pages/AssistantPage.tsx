import { Alert } from '../components/Alert'
import { messageOf } from '../components/formErrors'
import { rateLimitText, useRetryCountdown } from '../components/rateLimit'
import { PageHeader } from '../components/PageHeader'
import { AskForm } from '../conversations/AskForm'
import { ChatLayout } from '../conversations/ChatLayout'
import { MessageList } from '../conversations/Messages'
import { useStartConversation } from '../conversations/useStartConversation'

/**
 * The assistant (UI-5): the first question starts a conversation, which then opens on its own
 * page.
 */
export function AssistantPage() {
  const start = useStartConversation()
  const wait = useRetryCountdown(start.error)
  const pending = start.isPending ? start.variables : undefined

  return (
    <ChatLayout
      messageCount={pending !== undefined ? 0.5 : 0}
      header={
        <PageHeader
          title="Assistant"
          description="Ask a question about the documents you can read. Every answer cites the passages it comes from, and says so when they do not answer it."
        />
      }
      composer={
        <AskForm
          onAsk={start.mutateAsync}
          isPending={start.isPending || (wait ?? 0) > 0}
        />
      }
    >
      {pending !== undefined && (
        <MessageList
          messages={[]}
          selected={null}
          onSelect={() => {}}
          pendingQuestion={pending}
        />
      )}
      {start.isError && (
        <Alert>
          {wait !== null
            ? rateLimitText(start.error, wait)
            : messageOf(start.error, 'The conversation could not be started.')}
        </Alert>
      )}
    </ChatLayout>
  )
}
