import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, type ReactNode } from 'react'
import { api, ApiError } from '../api/client'
import type { CitationResponse, SummaryResponse } from '../api/types'
import { Alert } from '../components/Alert'
import { Button } from '../components/Button'
import { Card } from '../components/Card'
import { messageOf } from '../components/formErrors'
import { ErrorState, LoadingState } from '../components/states'
import { CitationPanel } from '../conversations/CitationPanel'
import { GroundedText, SourceList, Thinking } from '../conversations/Messages'
import { formatDateTime } from '../documents/format'

/** How often the card asks again while the summary is being made (SUM-5). */
export const SUMMARY_POLL_MS = 2000

const summaryKey = (documentId: string) => ['summary', documentId] as const

/**
 * A document's summary (Section 13.1): offered once the document is processed, made in the
 * background while the card asks again, and shown in the chat's formatting with the same source
 * chips and panel. The server checks access on every request (ACL-E5); a summary the reader may no
 * longer see answers 404 like the document.
 */
export function SummaryCard({ documentId }: { documentId: string }) {
  const queryClient = useQueryClient()
  const [selected, setSelected] = useState<number | null>(null)
  const summary = useQuery({
    queryKey: summaryKey(documentId),
    queryFn: ({ signal }) =>
      api<SummaryResponse>(`/api/v1/documents/${documentId}/summary`, {
        signal,
      }),
    // Ask again while the summary is made, but not after a read failed: the last good answer
    // would still say PENDING, and the card would ask forever.
    refetchInterval: (query) =>
      query.state.status !== 'error' && query.state.data?.status === 'PENDING'
        ? SUMMARY_POLL_MS
        : false,
    retry: false,
  })
  const start = useMutation({
    mutationFn: () =>
      api<SummaryResponse>(`/api/v1/documents/${documentId}/summary`, {
        method: 'POST',
      }),
    onSuccess: (response) =>
      queryClient.setQueryData(summaryKey(documentId), response),
  })

  // After a failed read only the error is shown: the server checks access on every read
  // (ACL-E5), so an older answer may no longer be the reader's to see.
  const data = summary.isError ? undefined : summary.data
  const citations = data?.summary?.citations ?? []
  return (
    <Card
      title="Summary"
      action={
        data &&
        (data.status === 'NONE' || data.status === 'FAILED') && (
          <Button
            onPress={() => start.mutate()}
            isDisabled={start.isPending}
            size="sm"
            variant={data.status === 'FAILED' ? 'secondary' : 'primary'}
          >
            {data.status === 'FAILED' ? 'Try again' : 'Summarize'}
          </Button>
        )
      }
      className="space-y-4"
    >
      {summary.isPending && <LoadingState>Loading the summary…</LoadingState>}
      {summary.isError && (
        <ErrorState onRetry={() => void summary.refetch()}>
          {messageOf(summary.error, 'The summary could not be loaded.')}
        </ErrorState>
      )}
      {start.isError && <Alert>{startError(start.error)}</Alert>}

      {data?.status === 'NONE' && !start.isPending && (
        <p className="text-sm text-muted">
          Get an overview, the key points, the rules and the important dates of
          this document, each with the page it comes from.
        </p>
      )}
      {(data?.status === 'PENDING' || start.isPending) && (
        <div
          role="status"
          className="flex items-center gap-3 text-sm text-muted"
        >
          <Thinking />
          <span>
            Summarizing… A short document takes a few seconds; a long one is
            read part by part and takes longer.
          </span>
        </div>
      )}
      {data?.status === 'FAILED' && !start.isPending && (
        <Alert>
          The summary could not be made. {data.failureMessage ?? ''}
        </Alert>
      )}
      {data?.status === 'READY' && data.summary && (
        <div className="space-y-5">
          {data.summary.overview && (
            <Section title="Overview">
              <GroundedText
                text={data.summary.overview}
                citations={citations}
                selectedOrdinal={selected}
                onSelect={setSelected}
              />
            </Section>
          )}
          <ListSection
            title="Key points"
            items={data.summary.keyPoints}
            citations={citations}
            selected={selected}
            onSelect={setSelected}
          />
          <ListSection
            title="Rules and requirements"
            items={data.summary.rules}
            citations={citations}
            selected={selected}
            onSelect={setSelected}
          />
          <ListSection
            title="Important dates"
            items={data.summary.dates}
            citations={citations}
            selected={selected}
            onSelect={setSelected}
          />
          {citations.length > 0 && (
            <SourceList
              citations={citations}
              selectedOrdinal={selected}
              onSelect={setSelected}
            />
          )}
          <p className="text-xs text-muted">
            Generated from this document
            {data.model ? ` by ${data.model}` : ''}
            {data.path === 'MAP_REDUCE'
              ? `, part by part in ${data.modelCalls} steps`
              : ''}
            {data.finishedAt ? ` on ${formatDateTime(data.finishedAt)}` : ''}.
            Check the cited page before relying on it.
          </p>
        </div>
      )}

      <CitationPanel
        citations={sourcesOf(citations, selected)}
        onClose={() => setSelected(null)}
      />
    </Card>
  )
}

function Section({ title, children }: { title: string; children: ReactNode }) {
  return (
    <section className="space-y-2">
      <h3 className="text-sm font-semibold tracking-wide text-muted uppercase">
        {title}
      </h3>
      {children}
    </section>
  )
}

function ListSection({
  title,
  items,
  citations,
  selected,
  onSelect,
}: {
  title: string
  items: string[]
  citations: CitationResponse[]
  selected: number | null
  onSelect: (ordinal: number) => void
}) {
  if (items.length === 0) return null
  return (
    <Section title={title}>
      {/*
        Each item is parsed on its own, so each keeps its own chips. Parsed as one list, a marker
        repeated on consecutive items would show once, at the last, as in an answer, and the items
        before it would look uncited.
      */}
      <ul className="list-disc space-y-1.5 pl-6 text-base leading-7 marker:text-muted">
        {items.map((item, i) => (
          <li key={i} className="pl-1">
            <GroundedText
              text={item.replace(/\n+/g, ' ')}
              citations={citations}
              selectedOrdinal={selected}
              onSelect={onSelect}
              className="wrap-anywhere"
            />
          </li>
        ))}
      </ul>
    </Section>
  )
}

/** The passages of the chip's document and pages, for the panel; empty when it is closed. */
function sourcesOf(
  citations: CitationResponse[],
  selected: number | null,
): CitationResponse[] {
  const chosen = citations.find((c) => c.ordinal === selected)
  if (!chosen) return []
  return citations.filter(
    (c) => c.pageStart === chosen.pageStart && c.pageEnd === chosen.pageEnd,
  )
}

/** Why a summary could not be started, in words a reader can act on. */
function startError(error: Error): string {
  if (error instanceof ApiError) {
    if (error.code === 'DOCUMENT_TOO_LARGE' || error.code === 'BUSY') {
      return messageOf(error, 'This document cannot be summarized.')
    }
    if (error.code === 'DOCUMENT_NOT_INDEXED') {
      return 'This document is not ready yet. It can be summarized once it is processed.'
    }
    if (error.code === 'MODEL_UNAVAILABLE') {
      return 'Summaries are not available right now. Try again later.'
    }
  }
  return messageOf(error, 'The summary could not be started. Try again.')
}
