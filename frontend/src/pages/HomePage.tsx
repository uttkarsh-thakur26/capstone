import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router'
import { useAuth } from '../auth/useAuth'
import { Alert } from '../components/Alert'
import { messageOf } from '../components/formErrors'
import {
  conversationKeys,
  fetchConversations,
  UNTITLED,
} from '../conversations/api'

/** The start page after sign-in (16.2): quick ask, recent conversations, and where to go next. */
export function HomePage() {
  const { state } = useAuth()
  if (state.status !== 'authenticated') return null
  const { user } = state

  return (
    <section className="space-y-6">
      <div className="space-y-2">
        <h1 className="text-2xl font-semibold">Welcome, {user.displayName}</h1>
        <p>
          You are signed in as <strong>{user.email}</strong> with the role{' '}
          <strong>{user.role}</strong>.
        </p>
      </div>
      <p>
        <Link
          to="/assistant"
          className="inline-block rounded-md bg-indigo-700 px-4 py-2 text-sm font-medium text-white hover:bg-indigo-800"
        >
          Ask the assistant a question
        </Link>
      </p>
      <RecentConversations />
      <ul className="space-y-2">
        <li>
          <Link className="underline underline-offset-2" to="/documents">
            {user.role === 'STUDENT'
              ? 'See the documents shared with you'
              : 'See, upload and share documents'}
          </Link>
        </li>
        <li>
          <Link className="underline underline-offset-2" to="/search">
            Search the documents by meaning or by keywords
          </Link>
        </li>
        {user.role === 'ADMIN' && (
          <li>
            <Link className="underline underline-offset-2" to="/admin/users">
              Manage users and roles
            </Link>
          </li>
        )}
      </ul>
    </section>
  )
}

function RecentConversations() {
  const recent = useQuery({
    queryKey: conversationKeys.list(0, 3),
    queryFn: ({ signal }) => fetchConversations(0, 3, signal),
  })
  return (
    <div className="space-y-2">
      <h2 className="text-lg font-semibold">Recent conversations</h2>
      {recent.isPending && <p role="status">Loading…</p>}
      {recent.isError && (
        <Alert>
          {messageOf(recent.error, 'Your conversations could not be loaded.')}
        </Alert>
      )}
      {recent.data && recent.data.items.length === 0 && (
        <p className="text-slate-700 dark:text-slate-300">
          None yet. Your questions and their answers are kept here.
        </p>
      )}
      {recent.data && recent.data.items.length > 0 && (
        <ul className="space-y-1">
          {recent.data.items.map((conversation) => (
            <li key={conversation.id}>
              <Link
                to={`/conversations/${conversation.id}`}
                className="text-indigo-800 underline-offset-2 wrap-anywhere hover:underline dark:text-indigo-300"
              >
                {conversation.title ?? UNTITLED}
              </Link>
            </li>
          ))}
          <li>
            <Link
              className="text-sm underline underline-offset-2"
              to="/conversations"
            >
              All conversations
            </Link>
          </li>
        </ul>
      )}
    </div>
  )
}
