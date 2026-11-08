import { useQuery } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import {
  Input,
  Label,
  Radio,
  RadioGroup,
  SearchField,
} from 'react-aria-components'
import { Link, useSearchParams } from 'react-router'
import { api, ApiError } from '../api/client'
import type {
  DocumentResponse,
  SearchMode,
  SearchResponse,
  SearchResult,
} from '../api/types'
import { Alert } from '../components/Alert'
import { Button } from '../components/Button'
import { LoadingState } from '../components/states'
import { PageHeader } from '../components/PageHeader'
import { messageOf } from '../components/formErrors'
import { excerptRange, segments } from '../search/excerpt'
import { FIELD } from '../components/ui'

/** The longest query the server accepts. */
const MAX_QUERY_LENGTH = 1000

/**
 * How each mode appears in the address, so a search can be shared and Back works. Best match, the
 * default, has no parameter.
 */
const MODE_PARAMS: Record<SearchMode, string | null> = {
  HYBRID: null,
  SEMANTIC: 'meaning',
  KEYWORD: 'keywords',
}

function modeOf(param: string | null): SearchMode {
  if (param === MODE_PARAMS.SEMANTIC) return 'SEMANTIC'
  if (param === MODE_PARAMS.KEYWORD) return 'KEYWORD'
  return 'HYBRID'
}

const MODE_HINTS: Record<SearchMode, string> = {
  HYBRID:
    'Ranks passages by meaning and by your words together. A good first choice for most searches.',
  SEMANTIC:
    'Finds passages about the same idea, even when they use other words.',
  KEYWORD:
    'Finds passages that contain all of your words. Best for exact terms, codes and numbers.',
}

/**
 * Search (UI-7): a query, the mode, and the passages found, each linking to its page in the viewer.
 * The query, mode and optional document live in the address. The server returns only passages of
 * documents the user may retrieve, so nothing here filters by permission (RET-1).
 */
export function SearchPage() {
  const [params, setParams] = useSearchParams()
  const query = (params.get('q') ?? '').trim()
  const mode = modeOf(params.get('mode'))
  const documentId = params.get('documentId')

  const [draft, setDraft] = useState(query)
  const [shownQuery, setShownQuery] = useState(query)
  if (query !== shownQuery) {
    // The address changed, for example through Back: show its query in the field.
    setShownQuery(query)
    setDraft(query)
  }

  const change = (changes: Record<string, string | null>) => {
    const next = new URLSearchParams(params)
    for (const [key, value] of Object.entries(changes)) {
      if (value === null) next.delete(key)
      else next.set(key, value)
    }
    setParams(next)
  }

  const results = useQuery({
    queryKey: ['search', query, mode, documentId],
    queryFn: ({ signal }) =>
      api<SearchResponse>('/api/v1/search', {
        method: 'POST',
        body: {
          query,
          mode,
          ...(documentId ? { documentIds: [documentId] } : {}),
        },
        signal,
      }),
    enabled: query !== '',
    // The server already retries the model; asking again at once rarely helps.
    retry: false,
  })

  const scope = useQuery({
    queryKey: ['document', documentId],
    queryFn: ({ signal }) =>
      api<DocumentResponse>(`/api/v1/documents/${documentId}`, { signal }),
    enabled: documentId !== null,
    retry: false,
  })

  const submit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    const next = draft.trim()
    if (next === '') return
    if (next === query) void results.refetch()
    else change({ q: next })
  }

  return (
    <section className="space-y-6">
      <PageHeader
        title="Search"
        description="Find passages in the documents you can read, by meaning, by exact words, or both."
      />

      <form role="search" onSubmit={submit} className="space-y-4">
        <div className="flex flex-wrap items-end gap-3">
          <SearchField
            value={draft}
            onChange={setDraft}
            className="flex w-full max-w-xl flex-col gap-1"
          >
            <Label className="text-sm font-medium">
              Search the documents you can read
            </Label>
            <Input
              maxLength={MAX_QUERY_LENGTH}
              placeholder="A question, or some words"
              className={FIELD}
            />
          </SearchField>
          <Button type="submit">Search</Button>
        </div>
        <ModeSelector
          mode={mode}
          onChange={(next) => change({ mode: MODE_PARAMS[next] })}
        />
      </form>

      {documentId !== null && (
        <div className="flex flex-wrap items-center gap-3 text-sm">
          <span>
            Only in:{' '}
            <strong>
              {scope.data?.title ??
                (scope.isError ? 'a document that is not available' : '…')}
            </strong>
          </span>
          <Button
            variant="secondary"
            onPress={() => change({ documentId: null })}
          >
            Search all documents
          </Button>
        </div>
      )}

      {query === '' && (
        <p className="text-muted">
          Type a question or some words, then press Search.
        </p>
      )}
      {query !== '' && results.isPending && (
        <LoadingState>Searching…</LoadingState>
      )}
      {results.isError && (
        <SearchFailed
          error={results.error}
          scoped={documentId !== null}
          onRetry={() => void results.refetch()}
          onSearchAll={() => change({ documentId: null })}
        />
      )}
      {results.data && (
        <Results
          response={results.data}
          scoped={documentId !== null}
          refreshing={results.isFetching}
        />
      )}
    </section>
  )
}

function ModeSelector({
  mode,
  onChange,
}: {
  mode: SearchMode
  onChange: (mode: SearchMode) => void
}) {
  return (
    <RadioGroup
      value={mode}
      onChange={(value) => onChange(value as SearchMode)}
      orientation="horizontal"
      className="flex flex-col gap-2"
    >
      <Label className="text-sm font-medium">Search by</Label>
      <div className="inline-flex w-fit rounded-md border border-line-strong bg-surface p-0.5">
        <ModeOption value="HYBRID">Best match</ModeOption>
        <ModeOption value="SEMANTIC">Meaning</ModeOption>
        <ModeOption value="KEYWORD">Keywords</ModeOption>
      </div>
      <p className="text-sm text-muted">{MODE_HINTS[mode]}</p>
    </RadioGroup>
  )
}

function ModeOption({
  value,
  children,
}: {
  value: SearchMode
  children: string
}) {
  return (
    <Radio
      value={value}
      className="cursor-default rounded px-4 py-1.5 text-sm font-medium outline-none data-[focus-visible]:outline-2 data-[focus-visible]:outline-focus data-[hovered]:bg-subtle data-[selected]:bg-accent data-[selected]:text-white"
    >
      {children}
    </Radio>
  )
}

function SearchFailed({
  error,
  scoped,
  onRetry,
  onSearchAll,
}: {
  error: Error
  scoped: boolean
  onRetry: () => void
  onSearchAll: () => void
}) {
  // RET-8: a document the user cannot read answers 404, like one that does not exist (ACL-8).
  if (scoped && error instanceof ApiError && error.status === 404) {
    return (
      <div className="space-y-2">
        <Alert>
          The document to search in is not available. It may have been deleted,
          or it is no longer shared with you.
        </Alert>
        <Button variant="secondary" onPress={onSearchAll}>
          Search all documents
        </Button>
      </div>
    )
  }
  return (
    <div className="space-y-2">
      <Alert>{messageOf(error, 'The search failed.')}</Alert>
      <Button variant="secondary" onPress={onRetry}>
        Try again
      </Button>
    </div>
  )
}

function Results({
  response,
  scoped,
  refreshing,
}: {
  response: SearchResponse
  scoped: boolean
  refreshing: boolean
}) {
  const count = response.results.length
  return (
    <div className="space-y-4" aria-busy={refreshing}>
      <p role="status" className="text-sm text-muted">
        {count === 0
          ? emptyMessage(response.mode, scoped)
          : `${count} ${count === 1 ? 'passage' : 'passages'}, best match first.`}
      </p>
      {count > 0 && (
        <ol className="space-y-4">
          {response.results.map((result, index) => (
            <ResultCard
              key={`${result.documentId}-${result.pageStart}-${index}`}
              result={result}
              mode={response.mode}
            />
          ))}
        </ol>
      )}
    </div>
  )
}

function emptyMessage(mode: SearchMode, scoped: boolean): string {
  if (mode === 'KEYWORD') {
    return 'No passage contains all of these words. Try fewer words, or search by meaning.'
  }
  return scoped
    ? 'This document has no passages to search: it is archived or not processed yet.'
    : 'No passages found. None of the documents you can read has been processed yet.'
}

/**
 * The score shown with a result. A fused score is a rank, not a measure anyone can read, so a best
 * match result shows its similarity instead.
 */
function scoreLabel(result: SearchResult, mode: SearchMode): string {
  if (mode === 'KEYWORD') return `Keyword rank ${result.score.toFixed(2)}`
  const similarity = mode === 'SEMANTIC' ? result.score : result.similarity
  return similarity == null ? '' : `Similarity ${similarity.toFixed(2)}`
}

function ResultCard({
  result,
  mode,
}: {
  result: SearchResult
  mode: SearchMode
}) {
  const [expanded, setExpanded] = useState(false)
  const { text, highlights } = result
  const { start, end } = expanded
    ? { start: 0, end: text.length }
    : excerptRange(text, highlights)
  const shortened = start > 0 || end < text.length
  const pages =
    result.pageStart === result.pageEnd
      ? `Page ${result.pageStart}`
      : `Pages ${result.pageStart}–${result.pageEnd}`
  return (
    <li className="space-y-2 rounded-lg border border-line bg-surface p-4">
      <div className="flex flex-wrap items-baseline justify-between gap-x-4 gap-y-1">
        <Link
          to={`/documents/${result.documentId}?page=${result.pageStart}`}
          className="font-medium text-link underline-offset-2 hover:underline"
        >
          {result.title}
        </Link>
        <span className="text-sm text-muted">
          {pages} · {scoreLabel(result, mode)}
        </span>
      </div>
      <p className="text-sm leading-relaxed whitespace-pre-line wrap-anywhere">
        {start > 0 && '… '}
        {segments(text, highlights, start, end).map((segment, i) =>
          segment.marked ? (
            <mark
              key={i}
              className="rounded bg-amber-200 px-0.5 text-inherit dark:bg-amber-700/60"
            >
              {segment.text}
            </mark>
          ) : (
            <span key={i}>{segment.text}</span>
          ),
        )}
        {end < text.length && ' …'}
      </p>
      {(shortened || expanded) && (
        <Button
          variant="secondary"
          aria-expanded={expanded}
          onPress={() => setExpanded((value) => !value)}
        >
          {expanded ? 'Show less' : 'Show whole passage'}
        </Button>
      )}
    </li>
  )
}
