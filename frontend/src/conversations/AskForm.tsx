import {
  useLayoutEffect,
  useRef,
  useState,
  type FormEvent,
  type KeyboardEvent,
} from 'react'
import {
  Button as AriaButton,
  Label,
  Text,
  TextArea,
  TextField,
} from 'react-aria-components'
import { ArrowUpIcon } from '../components/icons'

/** The longest question the server accepts (11.1 step 2). */
export const MAX_QUESTION_LENGTH = 1000

/** The box grows with the question up to about six lines, then scrolls. */
const MAX_HEIGHT_PX = 168

/**
 * The question box: grows with the text, with the send button inside it. Enter sends, Shift+Enter
 * starts a new line. A sent question leaves the box at once; it comes back only if sending failed
 * and nothing new was typed meanwhile, so it can be sent again.
 */
export function AskForm({
  onAsk,
  isPending,
}: {
  onAsk: (question: string) => Promise<unknown>
  isPending: boolean
}) {
  const [question, setQuestion] = useState('')
  const box = useRef<HTMLTextAreaElement>(null)
  const trimmed = question.trim()

  useLayoutEffect(() => {
    const element = box.current
    if (!element) return
    element.style.height = 'auto'
    if (element.scrollHeight > 0) {
      element.style.height = `${Math.min(element.scrollHeight, MAX_HEIGHT_PX)}px`
      element.style.overflowY =
        element.scrollHeight > MAX_HEIGHT_PX ? 'auto' : 'hidden'
    }
  }, [question])

  const send = async () => {
    if (trimmed === '' || isPending) return
    const sent = trimmed
    setQuestion('')
    try {
      await onAsk(sent)
    } catch {
      // The page shows the error; the question returns for another try.
      setQuestion((current) => (current.trim() === '' ? sent : current))
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
      <div className="flex items-end gap-2 rounded-3xl border border-line-strong bg-surface py-2 pr-2 pl-4 shadow-sm focus-within:outline-2 focus-within:-outline-offset-1 focus-within:outline-focus">
        <TextField
          value={question}
          onChange={setQuestion}
          maxLength={MAX_QUESTION_LENGTH}
          className="flex min-w-0 flex-1 flex-col"
        >
          <Label className="sr-only">Your question</Label>
          <TextArea
            ref={box}
            rows={1}
            onKeyDown={keyDown}
            placeholder="Ask about your documents"
            className="block w-full resize-none bg-transparent py-1.5 text-base leading-6 text-fg outline-none placeholder:text-muted"
          />
          <Text slot="description" className="sr-only">
            Enter sends, Shift+Enter adds a line.
          </Text>
        </TextField>
        <AriaButton
          type="submit"
          aria-label="Ask"
          isDisabled={trimmed === '' || isPending}
          className="grid size-9 shrink-0 place-items-center rounded-full bg-accent text-white outline-none data-[disabled]:opacity-40 data-[focus-visible]:outline-2 data-[focus-visible]:outline-offset-2 data-[focus-visible]:outline-focus data-[hovered]:bg-accent-hover"
        >
          <ArrowUpIcon />
        </AriaButton>
      </div>
      <p className="text-center text-xs text-muted">
        Generated from your documents. Check the cited page.
      </p>
    </form>
  )
}
