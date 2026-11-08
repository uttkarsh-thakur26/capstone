import { useMutation, useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import {
  ComboBox,
  Input,
  Label,
  ListBox,
  ListBoxItem,
  Popover,
  Text,
  useFilter,
} from 'react-aria-components'
import { Link, useSearchParams } from 'react-router'
import { api, ApiError } from '../api/client'
import type {
  CitationResponse,
  ComparisonCategory,
  ComparisonDifference,
  ComparisonResponse,
  ComparisonSide,
  DocumentPage,
  DocumentSummary,
} from '../api/types'
import { Button } from '../components/Button'
import { Card } from '../components/Card'
import { messageOf } from '../components/formErrors'
import { PageHeader } from '../components/PageHeader'
import { EmptyState, ErrorState, LoadingState } from '../components/states'
import { FIELD, HINT, LINK, LIST_ITEM, POPOVER } from '../components/ui'
import { CitationPanel } from '../conversations/CitationPanel'
import { pages } from '../conversations/citations'
import { GroundedText, Thinking } from '../conversations/Messages'

/** The headings of the groups, in the order they are shown (CMP-3). */
const CATEGORIES: { category: ComparisonCategory; title: string }[] = [
  { category: 'CHANGED_REQUIREMENT', title: 'Changed requirements' },
  { category: 'CHANGED_DATE', title: 'Changed dates' },
  { category: 'CHANGED_THRESHOLD', title: 'Changed numbers and limits' },
  { category: 'ADDED', title: 'Added' },
  { category: 'REMOVED', title: 'Removed' },
  { category: 'OTHER', title: 'Other differences' },
]

/**
 * Compare two documents (Section 13.2): pick two processed documents the reader can read, then ask.
 * Each difference quotes both documents, with its page on each side, or says it is in one only;
 * the server keeps only differences whose quotes are in the documents (CMP-4). The picks live in
 * the address, so a comparison can be shared; it runs only when asked, because it calls the model.
 */
export function ComparePage() {
  const [params, setParams] = useSearchParams()
  const leftId = params.get('left')
  const rightId = params.get('right')
  const [selected, setSelected] = useState<CitationResponse | null>(null)

  const documents = useQuery({
    queryKey: ['documents', 'comparable'],
    queryFn: ({ signal }) =>
      api<DocumentPage>(
        `/api/v1/documents?${new URLSearchParams({ status: 'INDEXED', size: '100', sort: 'title,asc' })}`,
        { signal },
      ),
  })
  const comparison = useMutation({
    mutationFn: (pair: { leftId: string; rightId: string }) =>
      api<ComparisonResponse>('/api/v1/documents/compare', {
        method: 'POST',
        body: pair,
      }),
  })

  const pick = (side: 'left' | 'right', id: string | null) => {
    const next = new URLSearchParams(params)
    if (id) next.set(side, id)
    else next.delete(side)
    setParams(next, { replace: true })
    comparison.reset()
  }
  const swap = () => {
    const next = new URLSearchParams(params)
    if (rightId) next.set('left', rightId)
    else next.delete('left')
    if (leftId) next.set('right', leftId)
    else next.delete('right')
    setParams(next, { replace: true })
    comparison.reset()
  }

  const options = documents.data?.items ?? []
  const ready = leftId !== null && rightId !== null && leftId !== rightId
  return (
    <section className="space-y-6">
      <PageHeader
        title="Compare documents"
        description="Pick two documents, such as two versions of a policy, to see what changed, with the page on each side."
      />

      {documents.isPending && (
        <LoadingState>Loading your documents…</LoadingState>
      )}
      {documents.isError && (
        <ErrorState onRetry={() => void documents.refetch()}>
          {messageOf(documents.error, 'Your documents could not be loaded.')}
        </ErrorState>
      )}
      {documents.data && options.length < 2 && (
        <EmptyState>
          Comparing needs two processed documents you can read.{' '}
          <Link to="/documents" className={LINK}>
            See your documents
          </Link>
        </EmptyState>
      )}
      {documents.data && options.length >= 2 && (
        <Card className="space-y-4">
          <div className="grid gap-4 md:grid-cols-2">
            <DocumentPicker
              label="Earlier or reference document"
              options={options}
              selectedId={leftId}
              otherId={rightId}
              onPick={(id) => pick('left', id)}
            />
            <DocumentPicker
              label="Document to compare with it"
              options={options}
              selectedId={rightId}
              otherId={leftId}
              onPick={(id) => pick('right', id)}
            />
          </div>
          {documents.data.totalItems > options.length && (
            <p className={HINT}>
              The first {options.length} documents by title are listed. Type to
              filter them.
            </p>
          )}
          <div className="flex flex-wrap gap-3">
            <Button
              onPress={() =>
                ready && comparison.mutate({ leftId: leftId, rightId: rightId })
              }
              isDisabled={!ready || comparison.isPending}
            >
              Compare
            </Button>
            <Button
              variant="secondary"
              onPress={swap}
              isDisabled={(!leftId && !rightId) || comparison.isPending}
            >
              Swap sides
            </Button>
          </div>
        </Card>
      )}

      {comparison.isPending && (
        <div
          role="status"
          className="flex items-center gap-3 text-sm text-muted"
        >
          <Thinking />
          <span>Comparing… This takes a few seconds.</span>
        </div>
      )}
      {comparison.isError && (
        <ErrorState
          onRetry={
            retryable(comparison.error) && ready
              ? () => comparison.mutate({ leftId, rightId })
              : undefined
          }
        >
          {comparisonError(comparison.error)}
        </ErrorState>
      )}
      {comparison.data && (
        <Differences response={comparison.data} onSelect={setSelected} />
      )}

      <CitationPanel
        citations={selected ? [selected] : []}
        onClose={() => setSelected(null)}
      />
    </section>
  )
}

/** A searchable picker over the processed documents the reader can read. */
function DocumentPicker({
  label,
  options,
  selectedId,
  otherId,
  onPick,
}: {
  label: string
  options: DocumentSummary[]
  selectedId: string | null
  otherId: string | null
  onPick: (id: string | null) => void
}) {
  const { contains } = useFilter({ sensitivity: 'base' })
  const choices = options.filter((option) => option.id !== otherId)
  return (
    <ComboBox
      defaultItems={choices}
      selectedKey={selectedId}
      onSelectionChange={(key) => onPick(key === null ? null : String(key))}
      defaultFilter={contains}
      menuTrigger="focus"
      className="flex flex-col gap-1"
    >
      <Label className="text-sm font-medium">{label}</Label>
      <Input className={FIELD} placeholder="Type to find a document" />
      <Text slot="description" className={HINT}>
        Processed documents you can read.
      </Text>
      <Popover className={`${POPOVER} w-(--trigger-width)`}>
        <ListBox className="max-h-72 overflow-auto p-1 outline-none">
          {(option: DocumentSummary) => (
            <ListBoxItem
              id={option.id}
              textValue={option.title}
              className={LIST_ITEM}
            >
              {option.title}
              {option.archived && (
                <span className="text-muted"> (archived)</span>
              )}
            </ListBoxItem>
          )}
        </ListBox>
      </Popover>
    </ComboBox>
  )
}

function Differences({
  response,
  onSelect,
}: {
  response: ComparisonResponse
  onSelect: (citation: CitationResponse) => void
}) {
  const count = response.differences.length
  return (
    <div className="space-y-6">
      <p role="status" className="text-sm text-muted">
        {count === 0
          ? 'No differences were found.'
          : `${count} ${count === 1 ? 'difference' : 'differences'} found.`}{' '}
        {response.identicalPassages > 0 &&
          `${response.identicalPassages} ${response.identicalPassages === 1 ? 'passage reads' : 'passages read'} the same in both.`}
      </p>
      {count === 0 && (
        <EmptyState>
          {response.modelCalls === 0
            ? 'The two documents read the same.'
            : 'The passages that differ hold no difference the documents themselves show.'}
        </EmptyState>
      )}
      {CATEGORIES.map(({ category, title }) => {
        const group = response.differences.filter(
          (d) => d.category === category,
        )
        if (group.length === 0) return null
        return (
          <section key={category} className="space-y-3">
            <h2 className="text-base font-semibold">
              {title}{' '}
              <span className="font-normal text-muted">({group.length})</span>
            </h2>
            <ol className="space-y-3">
              {group.map((difference, i) => (
                <DifferenceCard
                  key={i}
                  difference={difference}
                  leftTitle={response.left.title}
                  rightTitle={response.right.title}
                  onSelect={onSelect}
                />
              ))}
            </ol>
          </section>
        )
      })}
      {count > 0 && (
        <p className="text-xs text-muted">
          Found by {response.model}. Every quote is checked against the
          documents; check the pages before relying on a difference.
        </p>
      )}
    </div>
  )
}

function DifferenceCard({
  difference,
  leftTitle,
  rightTitle,
  onSelect,
}: {
  difference: ComparisonDifference
  leftTitle: string
  rightTitle: string
  onSelect: (citation: CitationResponse) => void
}) {
  return (
    <li className="space-y-3 rounded-lg border border-line bg-surface p-4">
      <GroundedText
        text={difference.explanation}
        citations={[]}
        selectedOrdinal={null}
        onSelect={() => {}}
        className="text-base leading-7 wrap-anywhere"
      />
      <div className="grid gap-3 md:grid-cols-2">
        <SideQuote
          title={leftTitle}
          side={difference.left}
          onSelect={onSelect}
        />
        <SideQuote
          title={rightTitle}
          side={difference.right}
          onSelect={onSelect}
        />
      </div>
    </li>
  )
}

function SideQuote({
  title,
  side,
  onSelect,
}: {
  title: string
  side?: ComparisonSide | null
  onSelect: (citation: CitationResponse) => void
}) {
  if (!side) {
    return (
      <div className="rounded-md border border-dashed border-line-strong p-3 text-sm text-muted">
        <p className="font-medium wrap-anywhere">{title}</p>
        <p>Not in this document.</p>
      </div>
    )
  }
  return (
    <div className="space-y-2 rounded-md bg-subtle p-3">
      <p className="text-sm font-medium wrap-anywhere">{title}</p>
      <blockquote className="border-l-4 border-accent pl-3 text-sm leading-relaxed whitespace-pre-line wrap-anywhere">
        {side.quote}
      </blockquote>
      <Button
        variant="secondary"
        size="sm"
        onPress={() => onSelect(side.citation)}
        aria-label={`Open ${title}, ${pages(side.citation)}`}
      >
        {pages(side.citation)[0].toUpperCase() + pages(side.citation).slice(1)}
      </Button>
    </div>
  )
}

function retryable(error: Error): boolean {
  return !(
    error instanceof ApiError &&
    (error.status === 404 ||
      error.status === 409 ||
      error.status === 422 ||
      error.status === 400)
  )
}

/** Why a comparison could not be made, in words a reader can act on. */
function comparisonError(error: Error): string {
  if (error instanceof ApiError) {
    if (error.status === 404) {
      return 'One of these documents is not available. It may have been deleted, or it is no longer shared with you.'
    }
    if (
      error.code === 'DOCUMENT_TOO_LARGE' ||
      error.code === 'DOCUMENT_NOT_INDEXED'
    ) {
      return messageOf(error, 'These documents cannot be compared.')
    }
    if (error.code === 'MODEL_UNAVAILABLE') {
      return 'Comparisons are not available right now. Try again later.'
    }
  }
  return messageOf(error, 'The comparison failed. Try again.')
}
