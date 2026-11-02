import { screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import type { ConversationResponse } from '../api/types'
import {
  fakeApi,
  json,
  META,
  noContent,
  session,
  type RecordedRequest,
} from '../test/fakeApi'
import { renderApp } from '../test/renderApp'

const ID = '0199a1b2-0000-7000-8000-0000000c0001'

function conversation(
  overrides: Partial<ConversationResponse> = {},
): ConversationResponse {
  return {
    id: ID,
    title: 'How long is the lab exam?',
    createdAt: '2026-11-02T10:00:00Z',
    updatedAt: '2026-11-02T10:05:00Z',
    ...overrides,
  }
}

function page(items: ConversationResponse[]) {
  return {
    items,
    page: 0,
    size: 20,
    totalItems: items.length,
    totalPages: items.length === 0 ? 0 : 1,
  }
}

function api(routes: Record<string, (request: RecordedRequest) => Response>) {
  return fakeApi({
    'GET /api/v1/meta': () => json(META),
    'POST /api/v1/auth/refresh': () => json(session('STUDENT')),
    ...routes,
  })
}

describe('ConversationsPage', () => {
  it('lists the conversations, untitled ones included, each linking to its page', async () => {
    api({
      'GET /api/v1/conversations': () =>
        json(
          page([conversation(), conversation({ id: 'c2', title: undefined })]),
        ),
    })
    renderApp('/conversations')

    expect(
      await screen.findByRole('link', { name: 'How long is the lab exam?' }),
    ).toHaveAttribute('href', `/conversations/${ID}`)
    expect(screen.getByRole('link', { name: 'New conversation' })).toBeVisible()
  })

  it('has an empty state that leads to the assistant', async () => {
    api({ 'GET /api/v1/conversations': () => json(page([])) })
    renderApp('/conversations')

    expect(await screen.findByText(/No conversations yet/)).toBeVisible()
    expect(
      screen.getByRole('link', { name: 'Ask the assistant a question' }),
    ).toHaveAttribute('href', '/assistant')
  })

  it('renames a conversation', async () => {
    let title = 'How long is the lab exam?'
    const server = api({
      'GET /api/v1/conversations': () => json(page([conversation({ title })])),
      [`PATCH /api/v1/conversations/${ID}`]: (request) => {
        title = (request.body as { title: string }).title
        return json(conversation({ title }))
      },
    })
    const { user } = renderApp('/conversations')

    await user.click(
      await screen.findByRole('button', {
        name: 'Rename How long is the lab exam?',
      }),
    )
    const dialog = await screen.findByRole('dialog')
    const field = within(dialog).getByRole('textbox', { name: 'Title' })
    await user.clear(field)
    await user.type(field, '  Lab exam rules  ')
    await user.click(within(dialog).getByRole('button', { name: 'Save' }))

    expect(
      await screen.findByRole('link', { name: 'Lab exam rules' }),
    ).toBeVisible()
    expect(server.to('PATCH', `/api/v1/conversations/${ID}`)[0].body).toEqual({
      title: 'Lab exam rules',
    })
  })

  it('deletes a conversation after confirmation', async () => {
    let deleted = false
    const server = api({
      'GET /api/v1/conversations': () =>
        json(page(deleted ? [] : [conversation()])),
      [`DELETE /api/v1/conversations/${ID}`]: () => {
        deleted = true
        return noContent()
      },
    })
    const { user } = renderApp('/conversations')

    await user.click(
      await screen.findByRole('button', {
        name: 'Delete How long is the lab exam?',
      }),
    )
    const dialog = await screen.findByRole('alertdialog')
    expect(within(dialog).getByText(/deleted for good/)).toBeVisible()
    await user.click(within(dialog).getByRole('button', { name: 'Delete' }))

    expect(await screen.findByText(/No conversations yet/)).toBeVisible()
    expect(server.to('DELETE', `/api/v1/conversations/${ID}`)).toHaveLength(1)
  })
})
