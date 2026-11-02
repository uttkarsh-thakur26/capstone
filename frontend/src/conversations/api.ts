import { api } from '../api/client'
import type {
  AnswerResponse,
  ConversationDetail,
  ConversationPage,
  ConversationResponse,
} from '../api/types'

/** Query keys: the list and each conversation, so asking refreshes both. */
export const conversationKeys = {
  all: ['conversations'] as const,
  list: (page: number, size: number) =>
    ['conversations', 'list', page, size] as const,
  detail: (id: string) => ['conversations', 'detail', id] as const,
}

export function fetchConversations(
  page: number,
  size: number,
  signal?: AbortSignal,
): Promise<ConversationPage> {
  return api<ConversationPage>(
    `/api/v1/conversations?page=${page}&size=${size}`,
    { signal },
  )
}

export function fetchConversation(
  id: string,
  signal?: AbortSignal,
): Promise<ConversationDetail> {
  return api<ConversationDetail>(`/api/v1/conversations/${id}`, { signal })
}

export function createConversation(): Promise<ConversationResponse> {
  return api<ConversationResponse>('/api/v1/conversations', { method: 'POST' })
}

export function askIn(id: string, question: string): Promise<AnswerResponse> {
  return api<AnswerResponse>(`/api/v1/conversations/${id}/messages`, {
    method: 'POST',
    body: { question },
  })
}

export function renameConversation(
  id: string,
  title: string,
): Promise<ConversationResponse> {
  return api<ConversationResponse>(`/api/v1/conversations/${id}`, {
    method: 'PATCH',
    body: { title },
  })
}

export function deleteConversation(id: string): Promise<void> {
  return api<void>(`/api/v1/conversations/${id}`, { method: 'DELETE' })
}

/** What an untitled conversation is called until its first question names it (CONV-3). */
export const UNTITLED = 'New conversation'
