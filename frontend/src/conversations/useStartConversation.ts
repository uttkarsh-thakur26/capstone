import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useNavigate } from 'react-router'
import { messageOf } from '../components/formErrors'
import { retryAfterOf } from '../components/rateLimit'
import {
  askIn,
  conversationKeys,
  createConversation,
  deleteConversation,
} from './api'

/**
 * A first question: starts a conversation, asks in it, then opens it. A question that failed is
 * stored in the conversation too, so the page opens either way and says what went wrong. Used by
 * the assistant page and the dashboard's quick ask. A question refused by the rate limit (SEC-8)
 * is different: nothing was asked, so the empty conversation is removed again and the error stays
 * on this page, where the wait is counted down.
 */
export function useStartConversation() {
  const navigate = useNavigate()
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: async (question: string) => {
      const conversation = await createConversation()
      let askError: string | undefined
      try {
        await askIn(conversation.id, question)
      } catch (error) {
        if (retryAfterOf(error) !== null) {
          await deleteConversation(conversation.id).catch(() => undefined)
          throw error
        }
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
}
