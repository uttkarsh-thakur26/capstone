import { Button as AriaButton } from 'react-aria-components'
import type {
  AnswerOutcome,
  CitationResponse,
  MessageResponse,
} from '../api/types'
import { describe } from './citations'
import { segments } from './markers'

/** The citation open in the panel: which answer, which source number. */
export interface SelectedCitation {
  messageId: string
  ordinal: number
}

/**
 * A conversation's messages as a chat log (UI-5). New messages are announced politely: a `log`
 * region is a live region (UI-8).
 */
export function MessageList({
  messages,
  selected,
  onSelect,
  pendingQuestion,
}: {
  messages: MessageResponse[]
  selected: SelectedCitation | null
  onSelect: (citation: SelectedCitation) => void
  /** A question sent and not answered yet. */
  pendingQuestion?: string
}) {
  return (
    <ol role="log" aria-label="Conversation" className="space-y-4">
      {messages.map((message) =>
        message.role === 'USER' ? (
          <Question key={message.id} text={message.content} />
        ) : (
          <li key={message.id}>
            <AnswerCard
              outcome={message.outcome ?? 'ERROR'}
              text={message.content}
              citations={message.citations}
              selectedOrdinal={
                selected?.messageId === message.id ? selected.ordinal : null
              }
              onSelect={(ordinal) =>
                onSelect({ messageId: message.id, ordinal })
              }
            />
          </li>
        ),
      )}
      {pendingQuestion !== undefined && (
        <>
          <Question text={pendingQuestion} />
          <li
            role="status"
            className="flex items-center gap-2 text-sm text-slate-700 dark:text-slate-300"
          >
            <span
              aria-hidden="true"
              className="size-2 animate-pulse rounded-full bg-indigo-600"
            />
            Searching your documents and writing an answer…
          </li>
        </>
      )}
    </ol>
  )
}

function Question({ text }: { text: string }) {
  return (
    <li className="ml-auto w-fit max-w-[85%] rounded-lg bg-indigo-50 px-4 py-2 whitespace-pre-line wrap-anywhere text-slate-900 dark:bg-indigo-950 dark:text-slate-100">
      <span className="sr-only">You asked: </span>
      {text}
    </li>
  )
}

/**
 * One answer. An answer, insufficient evidence and an error look clearly different (UI-5). The
 * text is shown as text, with its markers turned into chips: model output never becomes HTML
 * (UI-6).
 */
export function AnswerCard({
  outcome,
  text,
  citations,
  selectedOrdinal,
  onSelect,
}: {
  outcome: AnswerOutcome
  text: string
  citations: CitationResponse[]
  selectedOrdinal: number | null
  onSelect: (ordinal: number) => void
}) {
  if (outcome === 'INSUFFICIENT_EVIDENCE') {
    return (
      <div className="rounded-lg border border-dashed border-amber-600 bg-amber-50 px-4 py-3 text-amber-950 dark:border-amber-400 dark:bg-amber-950 dark:text-amber-50">
        <p className="font-medium">No answer found</p>
        <p className="mt-1 text-sm">{text}</p>
        <p className="mt-2 text-sm">
          Try other words, or a more specific question.
        </p>
      </div>
    )
  }
  if (outcome === 'ERROR') {
    return (
      <div className="rounded-lg border-l-4 border-red-700 bg-red-50 px-4 py-3 text-sm text-red-900 dark:border-red-400 dark:bg-red-950 dark:text-red-100">
        <p className="font-medium">No answer</p>
        <p className="mt-1">{text}</p>
      </div>
    )
  }
  const byOrdinal = new Map(citations.map((c) => [c.ordinal, c]))
  return (
    <div className="space-y-3 rounded-lg border border-slate-200 px-4 py-3 dark:border-slate-800">
      <p className="leading-relaxed whitespace-pre-line wrap-anywhere">
        {segments(text).map((segment, index) =>
          segment.kind === 'text' ? (
            <span key={index}>{segment.text}</span>
          ) : (
            <span key={index} className="whitespace-nowrap">
              {segment.ordinals.map((ordinal) => {
                const citation = byOrdinal.get(ordinal)
                return citation ? (
                  <CitationChip
                    key={ordinal}
                    citation={citation}
                    selected={selectedOrdinal === ordinal}
                    onSelect={onSelect}
                  />
                ) : (
                  <span key={ordinal}>[{ordinal}]</span>
                )
              })}
            </span>
          ),
        )}
      </p>
      {citations.length > 0 && (
        <div>
          <h3 className="text-xs font-semibold tracking-wide text-slate-600 uppercase dark:text-slate-300">
            Sources
          </h3>
          <ul className="mt-1 space-y-1 text-sm">
            {citations.map((citation) => (
              <li key={citation.ordinal} className="flex items-baseline gap-2">
                <CitationChip
                  citation={citation}
                  selected={selectedOrdinal === citation.ordinal}
                  onSelect={onSelect}
                />
                <span>{describe(citation)}</span>
              </li>
            ))}
          </ul>
        </div>
      )}
      <p className="text-xs text-slate-600 dark:text-slate-300">
        Answers are generated from the cited documents. Check them against the
        cited page.
      </p>
    </div>
  )
}

/**
 * A numbered source. An available one opens the viewer at its page; one whose document was deleted
 * or is no longer shared names nothing and opens nothing (CONV-6).
 */
function CitationChip({
  citation,
  selected,
  onSelect,
}: {
  citation: CitationResponse
  selected: boolean
  onSelect: (ordinal: number) => void
}) {
  if (!citation.available) {
    return (
      <span
        aria-label={`Source ${citation.ordinal}, no longer available`}
        className="mx-0.5 inline-flex min-w-6 justify-center rounded-full border border-slate-300 px-1.5 text-xs text-slate-500 line-through dark:border-slate-600 dark:text-slate-400"
      >
        {citation.ordinal}
      </span>
    )
  }
  return (
    <AriaButton
      onPress={() => onSelect(citation.ordinal)}
      aria-label={`Source ${citation.ordinal}: ${describe(citation)}`}
      aria-pressed={selected}
      className={`mx-0.5 inline-flex min-w-6 justify-center rounded-full px-1.5 text-xs font-semibold outline-none data-[focus-visible]:outline-2 data-[focus-visible]:outline-offset-1 data-[focus-visible]:outline-indigo-600 ${
        selected
          ? 'bg-indigo-700 text-white'
          : 'bg-indigo-100 text-indigo-900 data-[hovered]:bg-indigo-200 dark:bg-indigo-900 dark:text-indigo-100'
      }`}
    >
      {citation.ordinal}
    </AriaButton>
  )
}
