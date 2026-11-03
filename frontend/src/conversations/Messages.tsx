import { Fragment, type ReactNode } from 'react'
import { Button as AriaButton } from 'react-aria-components'
import type {
  AnswerOutcome,
  CitationResponse,
  MessageResponse,
} from '../api/types'
import { useMeta } from '../app/useMeta'
import { NoAnswerIcon, WarningIcon } from '../components/icons'
import { ProductName } from '../layout/ProductName'
import { parseAnswer, type Block, type Inline } from './answerText'
import { describe, groupSources, numbers, pages } from './citations'

/** The citation open in the panel: which answer, which source number. */
export interface SelectedCitation {
  messageId: string
  ordinal: number
}

/**
 * A conversation's messages as a chat log (UI-5): questions as bubbles on the right, answers as
 * plain text in the reading column. New messages are announced politely: a `log` region is a live
 * region (UI-8).
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
    <ol role="log" aria-label="Conversation" className="space-y-8">
      {messages.map((message) =>
        message.role === 'USER' ? (
          <Question key={message.id} text={message.content} />
        ) : (
          <li key={message.id}>
            <Answer
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
          <li className="space-y-2">
            <AssistantMark />
            <Thinking />
          </li>
        </>
      )}
    </ol>
  )
}

function Question({ text }: { text: string }) {
  return (
    <li className="flex justify-end">
      <p className="max-w-[85%] rounded-2xl rounded-br-md bg-subtle px-4 py-2.5 text-base leading-relaxed whitespace-pre-line wrap-anywhere text-fg">
        <span className="sr-only">You asked: </span>
        {text}
      </p>
    </li>
  )
}

/** The product's mark at the start of each answer. */
function AssistantMark() {
  const meta = useMeta()
  return (
    <p className="flex items-center gap-2 text-sm font-semibold">
      <span
        aria-hidden="true"
        className="grid size-6 place-items-center rounded-full bg-accent text-xs font-bold text-white"
      >
        {meta.data?.name.charAt(0) ?? ''}
      </span>
      <ProductName />
    </p>
  )
}

/** Three dots while the answer is on its way; screen readers hear one sentence instead. */
export function Thinking() {
  return (
    <div role="status" className="flex h-7 items-center gap-1.5 pl-8">
      <span className="sr-only">
        Searching your documents and writing an answer…
      </span>
      {[0, 150, 300].map((delay) => (
        <span
          key={delay}
          aria-hidden="true"
          style={{ animationDelay: `${delay}ms` }}
          className="size-2 animate-thinking rounded-full bg-muted motion-reduce:animate-none"
        />
      ))}
    </div>
  )
}

/**
 * One answer. An answer, insufficient evidence and an error look clearly different (UI-5). The
 * text goes through a small Markdown subset built into React elements, with its markers turned into
 * chips: model output never becomes HTML (UI-6).
 */
export function Answer({
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
      <div className="space-y-2">
        <AssistantMark />
        <p className="flex items-start gap-2 pl-8 text-sm text-muted">
          <span className="mt-0.5 text-amber-600 dark:text-amber-400">
            <NoAnswerIcon />
          </span>
          <span>
            <strong className="font-medium text-fg">No answer found</strong>.{' '}
            {text}
          </span>
        </p>
      </div>
    )
  }
  if (outcome === 'ERROR') {
    return (
      <div className="space-y-2">
        <AssistantMark />
        <p className="flex items-start gap-2 pl-8 text-sm text-muted">
          <span className="mt-0.5 text-red-600 dark:text-red-400">
            <WarningIcon />
          </span>
          <span>
            <strong className="font-medium text-fg">No answer</strong>. {text}
          </span>
        </p>
      </div>
    )
  }
  const byOrdinal = new Map(citations.map((c) => [c.ordinal, c]))
  const chips = (ordinals: number[]) =>
    ordinals.map((ordinal) => {
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
    })
  return (
    <div className="space-y-3">
      <AssistantMark />
      <div className="space-y-4 text-base leading-7 wrap-anywhere">
        {parseAnswer(text).map((block, i) => (
          <AnswerBlock key={i} block={block} chips={chips} />
        ))}
      </div>
      {citations.length > 0 && (
        <SourceList
          citations={citations}
          selectedOrdinal={selectedOrdinal}
          onSelect={onSelect}
        />
      )}
    </div>
  )
}

type Chips = (ordinals: number[]) => ReactNode

function AnswerBlock({ block, chips }: { block: Block; chips: Chips }) {
  switch (block.kind) {
    case 'heading':
      return (
        <h3 className="pt-2 text-base font-semibold">
          <InlineText nodes={block.content} chips={chips} />
        </h3>
      )
    case 'list': {
      const items = block.items.map((item, i) => (
        <li key={i} className="pl-1">
          <InlineText nodes={item} chips={chips} />
        </li>
      ))
      return block.ordered ? (
        <ol
          start={block.start}
          className="list-decimal space-y-1.5 pl-6 marker:text-muted"
        >
          {items}
        </ol>
      ) : (
        <ul className="list-disc space-y-1.5 pl-6 marker:text-muted">
          {items}
        </ul>
      )
    }
    case 'paragraph':
      return (
        <p>
          <InlineText nodes={block.content} chips={chips} />
        </p>
      )
  }
}

function InlineText({ nodes, chips }: { nodes: Inline[]; chips: Chips }) {
  return nodes.map((node, i) => {
    switch (node.kind) {
      case 'text':
        return <Fragment key={i}>{node.text}</Fragment>
      case 'break':
        return <br key={i} />
      case 'strong':
        return (
          <strong key={i} className="font-semibold text-fg">
            <InlineText nodes={node.children} chips={chips} />
          </strong>
        )
      case 'em':
        return (
          <em key={i}>
            <InlineText nodes={node.children} chips={chips} />
          </em>
        )
      case 'marker':
        return (
          <span key={i} className="whitespace-nowrap">
            {chips(node.ordinals)}
          </span>
        )
    }
  })
}

/**
 * The sources as one row of chips: one per document and pages, however many passages of it the
 * answer cites. Each opens the panel with all of them.
 */
function SourceList({
  citations,
  selectedOrdinal,
  onSelect,
}: {
  citations: CitationResponse[]
  selectedOrdinal: number | null
  onSelect: (ordinal: number) => void
}) {
  return (
    <div className="space-y-1.5">
      <h3 className="sr-only">Sources</h3>
      <ul className="flex flex-wrap gap-2">
        {groupSources(citations).map(({ key, citations: group }) => {
          const [first] = group
          if (!first.available) {
            return (
              <li
                key={key}
                aria-label={`Source ${first.ordinal}, no longer available`}
                className="inline-flex items-center gap-1.5 rounded-full border border-dashed border-line-strong px-2.5 py-1 text-xs text-muted"
              >
                <span className="line-through">{first.ordinal}</span>
                {describe(first)}
              </li>
            )
          }
          const selected = group.some((c) => c.ordinal === selectedOrdinal)
          return (
            <li key={key}>
              <AriaButton
                onPress={() => onSelect(first.ordinal)}
                aria-label={`${numbers(group)}: ${describe(first)}`}
                aria-pressed={selected}
                className={`inline-flex max-w-full items-center gap-1.5 rounded-full border px-2.5 py-1 text-xs outline-none data-[focus-visible]:outline-2 data-[focus-visible]:outline-offset-1 data-[focus-visible]:outline-focus ${
                  selected
                    ? 'border-accent bg-accent-soft text-fg'
                    : 'border-line bg-surface text-muted data-[hovered]:border-line-strong data-[hovered]:text-fg'
                }`}
              >
                <span className="font-semibold whitespace-nowrap text-link">
                  {group.map((c) => c.ordinal).join(', ')}
                </span>
                <span className="truncate">{first.documentTitle}</span>
                <span className="shrink-0">· {pages(first)}</span>
              </AriaButton>
            </li>
          )
        })}
      </ul>
    </div>
  )
}

/**
 * A numbered source in the text. An available one opens the panel at its page; one whose document
 * was deleted or is no longer shared names nothing and opens nothing (CONV-6).
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
        className="mx-0.5 inline-flex min-w-5 justify-center rounded-full border border-line-strong px-1.5 align-text-top text-[11px] leading-4 text-muted line-through"
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
      className={`mx-0.5 inline-flex min-w-5 justify-center rounded-full px-1.5 align-text-top text-[11px] leading-4 font-semibold outline-none data-[focus-visible]:outline-2 data-[focus-visible]:outline-offset-1 data-[focus-visible]:outline-focus ${
        selected
          ? 'bg-accent text-white'
          : 'bg-accent-soft text-link data-[hovered]:bg-accent data-[hovered]:text-white'
      }`}
    >
      {citation.ordinal}
    </AriaButton>
  )
}
