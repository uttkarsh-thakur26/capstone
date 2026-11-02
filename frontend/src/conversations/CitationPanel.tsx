import { useEffect, useRef } from 'react'
import { Link } from 'react-router'
import type { CitationResponse } from '../api/types'
import { Button } from '../components/Button'
import { DocumentViewer } from '../documents/DocumentViewer'
import { pages } from './citations'

/**
 * The cited passage beside the conversation (UI-5): its document and pages, the snippet the answer
 * was built from, and the PDF opened at the cited page in the browser's own viewer (ADR-035,
 * ADR-038). Focus moves to the panel's heading when it opens, so keyboard and screen-reader users
 * land on it.
 */
export function CitationPanel({
  citation,
  onClose,
}: {
  citation: CitationResponse
  onClose: () => void
}) {
  const heading = useRef<HTMLHeadingElement>(null)
  useEffect(() => {
    heading.current?.focus()
  }, [citation.documentId, citation.ordinal])

  const { documentId, documentTitle } = citation
  if (!citation.available || !documentId || !documentTitle) return null
  const page = citation.pageStart ?? 1
  return (
    <section
      aria-labelledby="citation-heading"
      className="space-y-3 rounded-lg border border-slate-200 p-4 dark:border-slate-800"
    >
      <div className="flex items-start justify-between gap-3">
        <div>
          <h2
            id="citation-heading"
            ref={heading}
            tabIndex={-1}
            className="font-semibold outline-none"
          >
            Source {citation.ordinal}: {documentTitle}
          </h2>
          <p className="text-sm text-slate-700 dark:text-slate-300">
            {pages(citation)[0].toUpperCase() + pages(citation).slice(1)}
          </p>
        </div>
        <Button variant="secondary" onPress={onClose}>
          Close
        </Button>
      </div>
      <figure className="space-y-1">
        <figcaption className="text-xs font-semibold tracking-wide text-slate-600 uppercase dark:text-slate-300">
          Cited passage
        </figcaption>
        <blockquote className="border-l-4 border-indigo-300 pl-3 text-sm whitespace-pre-line wrap-anywhere dark:border-indigo-700">
          {citation.snippet}
        </blockquote>
      </figure>
      <DocumentViewer
        documentId={documentId}
        title={documentTitle}
        page={page}
      />
      <Link
        to={`/documents/${documentId}?page=${page}`}
        className="inline-block text-sm text-indigo-800 underline underline-offset-2 dark:text-indigo-300"
      >
        Open the document page
      </Link>
    </section>
  )
}
