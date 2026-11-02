import {
  keepPreviousData,
  useMutation,
  useQuery,
  useQueryClient,
} from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import {
  Dialog,
  FieldError,
  Heading,
  Input,
  Label,
  Modal,
  ModalOverlay,
  TextField,
} from 'react-aria-components'
import { Link } from 'react-router'
import type { ConversationResponse } from '../api/types'
import { Alert } from '../components/Alert'
import { Button } from '../components/Button'
import { ConfirmDialog } from '../components/ConfirmDialog'
import { messageOf } from '../components/formErrors'
import {
  conversationKeys,
  deleteConversation,
  fetchConversations,
  renameConversation,
  UNTITLED,
} from '../conversations/api'
import { formatDateTime } from '../documents/format'

const PAGE_SIZE = 20

/** The longest title the server accepts. */
const MAX_TITLE_LENGTH = 200

/**
 * The user's own conversations, the most recently used first, with rename and delete (CONV-2).
 * Nobody else's appear, admins' included (CONV-1, D15).
 */
export function ConversationsPage() {
  const [page, setPage] = useState(0)
  const [renaming, setRenaming] = useState<ConversationResponse | null>(null)
  const [deleting, setDeleting] = useState<ConversationResponse | null>(null)
  const queryClient = useQueryClient()

  const list = useQuery({
    queryKey: conversationKeys.list(page, PAGE_SIZE),
    queryFn: ({ signal }) => fetchConversations(page, PAGE_SIZE, signal),
    placeholderData: keepPreviousData,
  })

  const remove = useMutation({
    mutationFn: (id: string) => deleteConversation(id),
    onSuccess: async () => {
      setDeleting(null)
      await queryClient.invalidateQueries({ queryKey: conversationKeys.all })
    },
  })

  return (
    <section className="space-y-6">
      <div className="flex flex-wrap items-baseline justify-between gap-2">
        <h1 className="text-2xl font-semibold">Conversations</h1>
        <Link
          to="/assistant"
          className="rounded-md bg-indigo-700 px-4 py-2 text-sm font-medium text-white hover:bg-indigo-800"
        >
          New question
        </Link>
      </div>

      {list.isPending && <p role="status">Loading your conversations…</p>}
      {list.isError && (
        <div className="space-y-2">
          <Alert>
            {messageOf(list.error, 'Your conversations could not be loaded.')}
          </Alert>
          <Button variant="secondary" onPress={() => void list.refetch()}>
            Try again
          </Button>
        </div>
      )}
      {list.data && list.data.items.length === 0 && (
        <p className="text-slate-700 dark:text-slate-300">
          No conversations yet.{' '}
          <Link className="underline underline-offset-2" to="/assistant">
            Ask the assistant a question
          </Link>
          .
        </p>
      )}
      {list.data && list.data.items.length > 0 && (
        <>
          <ul className="divide-y divide-slate-200 rounded-lg border border-slate-200 dark:divide-slate-800 dark:border-slate-800">
            {list.data.items.map((conversation) => (
              <li
                key={conversation.id}
                className="flex flex-wrap items-center gap-x-4 gap-y-2 px-4 py-3"
              >
                <div className="min-w-0 flex-1">
                  <Link
                    to={`/conversations/${conversation.id}`}
                    className="font-medium text-indigo-800 underline-offset-2 wrap-anywhere hover:underline dark:text-indigo-300"
                  >
                    {conversation.title ?? UNTITLED}
                  </Link>
                  <p className="text-xs text-slate-600 dark:text-slate-300">
                    Last used {formatDateTime(conversation.updatedAt)}
                  </p>
                </div>
                <div className="flex gap-2">
                  <Button
                    variant="secondary"
                    onPress={() => setRenaming(conversation)}
                    aria-label={`Rename ${conversation.title ?? UNTITLED}`}
                  >
                    Rename
                  </Button>
                  <Button
                    variant="secondary"
                    onPress={() => setDeleting(conversation)}
                    aria-label={`Delete ${conversation.title ?? UNTITLED}`}
                  >
                    Delete
                  </Button>
                </div>
              </li>
            ))}
          </ul>
          {list.data.totalPages > 1 && (
            <nav aria-label="Pages" className="flex items-center gap-3">
              <Button
                variant="secondary"
                isDisabled={page === 0}
                onPress={() => setPage(page - 1)}
              >
                Previous
              </Button>
              <span className="text-sm">
                Page {list.data.page + 1} of {list.data.totalPages}
              </span>
              <Button
                variant="secondary"
                isDisabled={page + 1 >= list.data.totalPages}
                onPress={() => setPage(page + 1)}
              >
                Next
              </Button>
            </nav>
          )}
        </>
      )}

      {renaming && (
        <RenameDialog
          conversation={renaming}
          onClose={() => setRenaming(null)}
        />
      )}
      <ConfirmDialog
        isOpen={deleting !== null}
        onOpenChange={(open) => {
          if (!open) {
            setDeleting(null)
            remove.reset()
          }
        }}
        title="Delete this conversation?"
        confirmLabel="Delete"
        onConfirm={() => deleting && remove.mutate(deleting.id)}
        isPending={remove.isPending}
      >
        <p>
          “{deleting?.title ?? UNTITLED}” and all its questions and answers are
          deleted for good.
        </p>
        {remove.isError && (
          <div className="mt-3">
            <Alert>
              {messageOf(
                remove.error,
                'The conversation could not be deleted.',
              )}
            </Alert>
          </div>
        )}
      </ConfirmDialog>
    </section>
  )
}

function RenameDialog({
  conversation,
  onClose,
}: {
  conversation: ConversationResponse
  onClose: () => void
}) {
  const queryClient = useQueryClient()
  const [title, setTitle] = useState(conversation.title ?? '')
  const rename = useMutation({
    mutationFn: (next: string) => renameConversation(conversation.id, next),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: conversationKeys.all })
      onClose()
    },
  })
  const trimmed = title.trim()

  const submit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (trimmed !== '') rename.mutate(trimmed)
  }

  return (
    <ModalOverlay
      isOpen
      onOpenChange={(open) => !open && onClose()}
      isDismissable={!rename.isPending}
      className="fixed inset-0 z-10 flex items-center justify-center bg-slate-900/50 p-4"
    >
      <Modal className="w-full max-w-md rounded-lg bg-white p-6 shadow-xl dark:bg-slate-900">
        <Dialog className="outline-none">
          <form onSubmit={submit} className="space-y-4">
            <Heading slot="title" className="text-lg font-semibold">
              Rename the conversation
            </Heading>
            <TextField
              value={title}
              onChange={setTitle}
              maxLength={MAX_TITLE_LENGTH}
              isRequired
              isInvalid={trimmed === '' && title !== ''}
              autoFocus
              className="flex flex-col gap-1"
            >
              <Label className="text-sm font-medium">Title</Label>
              <Input className="rounded-md border border-slate-400 bg-white px-3 py-2 text-slate-900 outline-none data-[focused]:outline-2 data-[focused]:outline-indigo-600 dark:border-slate-500 dark:bg-slate-900 dark:text-slate-100" />
              <FieldError className="text-sm text-red-700 dark:text-red-400">
                Enter a title.
              </FieldError>
            </TextField>
            {rename.isError && (
              <Alert>
                {messageOf(
                  rename.error,
                  'The conversation could not be renamed.',
                )}
              </Alert>
            )}
            <div className="flex justify-end gap-3">
              <Button
                variant="secondary"
                onPress={onClose}
                isDisabled={rename.isPending}
              >
                Cancel
              </Button>
              <Button
                type="submit"
                isDisabled={trimmed === '' || rename.isPending}
              >
                Save
              </Button>
            </div>
          </form>
        </Dialog>
      </Modal>
    </ModalOverlay>
  )
}
