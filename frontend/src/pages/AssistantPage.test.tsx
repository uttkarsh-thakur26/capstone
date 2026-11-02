import { screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { fakeApi, json, META, problem, session } from '../test/fakeApi'
import { renderApp } from '../test/renderApp'

const ID = '0199a1b2-0000-7000-8000-0000000c0001'
const CONVERSATION = {
  id: ID,
  title: 'How long is the lab exam?',
  createdAt: '2026-11-02T10:00:00Z',
  updatedAt: '2026-11-02T10:00:01Z',
}

describe('AssistantPage', () => {
  it('starts a conversation with the first question and opens it', async () => {
    const server = fakeApi({
      'GET /api/v1/meta': () => json(META),
      'POST /api/v1/auth/refresh': () => json(session('STUDENT')),
      'POST /api/v1/conversations': () =>
        json({ ...CONVERSATION, title: undefined }, 201),
      [`POST /api/v1/conversations/${ID}/messages`]: () =>
        json({
          messageId: 'a1',
          outcome: 'INSUFFICIENT_EVIDENCE',
          answer:
            'Insufficient information was found in the available authorized documents to answer this question.',
          citations: [],
        }),
      [`GET /api/v1/conversations/${ID}`]: () =>
        json({
          ...CONVERSATION,
          messages: [
            {
              id: 'q1',
              role: 'USER',
              content: 'What is the re-exam fee?',
              createdAt: '2026-11-02T10:00:00Z',
              citations: [],
            },
            {
              id: 'a1',
              role: 'ASSISTANT',
              content:
                'Insufficient information was found in the available authorized documents to answer this question.',
              outcome: 'INSUFFICIENT_EVIDENCE',
              createdAt: '2026-11-02T10:00:01Z',
              citations: [],
            },
          ],
        }),
    })
    const { user, router } = renderApp('/assistant')

    await user.type(
      await screen.findByRole('textbox', { name: 'Your question' }),
      'What is the re-exam fee?{Enter}',
    )

    expect(await screen.findByText('No answer found')).toBeVisible()
    expect(router.state.location.pathname).toBe(`/conversations/${ID}`)
    expect(
      server.to('POST', `/api/v1/conversations/${ID}/messages`)[0].body,
    ).toEqual({
      question: 'What is the re-exam fee?',
    })
  })

  it('says so when no conversation can be started', async () => {
    fakeApi({
      'GET /api/v1/meta': () => json(META),
      'POST /api/v1/auth/refresh': () => json(session('STUDENT')),
      'POST /api/v1/conversations': () =>
        problem(500, 'INTERNAL', 'Something went wrong.'),
    })
    const { user } = renderApp('/assistant')

    await user.type(
      await screen.findByRole('textbox', { name: 'Your question' }),
      'What is the re-exam fee?{Enter}',
    )

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Something went wrong.',
    )
  })
})
