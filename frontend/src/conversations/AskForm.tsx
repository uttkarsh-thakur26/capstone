import { useState, type FormEvent, type KeyboardEvent } from 'react'
import { Label, Text, TextArea, TextField } from 'react-aria-components'
import { Button } from '../components/Button'

/** The longest question the server accepts (11.1 step 2). */
export const MAX_QUESTION_LENGTH = 1000

/**
 * The question box. Enter sends, Shift+Enter starts a new line. The text stays while the answer is
 * on its way, so a failed question can be sent again.
 */
export function AskForm({
  onAsk,
  isPending,
}: {
  onAsk: (question: string) => Promise<unknown>
  isPending: boolean
}) {
  const [question, setQuestion] = useState('')
  const trimmed = question.trim()

  const send = async () => {
    if (trimmed === '' || isPending) return
    try {
      await onAsk(trimmed)
      // Only the question that was sent goes; a new draft typed meanwhile stays.
      setQuestion((current) => (current.trim() === trimmed ? '' : current))
    } catch {
      // The page shows the error; the question stays for another try.
    }
  }

  const submit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    void send()
  }

  const keyDown = (event: KeyboardEvent<HTMLTextAreaElement>) => {
    if (
      event.key === 'Enter' &&
      !event.shiftKey &&
      !event.nativeEvent.isComposing
    ) {
      event.preventDefault()
      void send()
    }
  }

  return (
    <form onSubmit={submit} className="space-y-2">
      <TextField
        value={question}
        onChange={setQuestion}
        maxLength={MAX_QUESTION_LENGTH}
        className="flex flex-col gap-1"
      >
        <Label className="text-sm font-medium">Your question</Label>
        <TextArea
          rows={3}
          onKeyDown={keyDown}
          className="rounded-md border border-slate-400 bg-white px-3 py-2 text-slate-900 outline-none data-[focused]:outline-2 data-[focused]:outline-indigo-600 dark:border-slate-500 dark:bg-slate-900 dark:text-slate-100"
        />
        <Text
          slot="description"
          className="text-xs text-slate-600 dark:text-slate-300"
        >
          Answers come only from the documents you can read. Enter sends,
          Shift+Enter adds a line.
        </Text>
      </TextField>
      <Button type="submit" isDisabled={trimmed === '' || isPending}>
        {isPending ? 'Asking…' : 'Ask'}
      </Button>
    </form>
  )
}
