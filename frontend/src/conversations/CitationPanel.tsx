import { useEffect, useRef } from 'react'
import { Dialog, Modal, ModalOverlay } from 'react-aria-components'
import { Link } from 'react-router'
import type { CitationResponse } from '../api/types'
import { Button } from '../components/Button'
import { LINK } from '../components/ui'
import { DocumentViewer } from '../documents/DocumentViewer'
import { numbers, pages } from './citations'

/**
 * The cited passage in a panel that slides over the conversation from the right, full width on
 * phones (UI-5): its document and pages, every passage of that page the answer cites, and the PDF
 * opened at the cited page in the browser's own viewer (ADR-035, ADR-038). It is a modal dialog:
 * focus moves to its heading when it opens, stays inside while it is open, and returns to the
 * citation chip that opened it when it closes, with Close, Escape or a click outside (UI-8).
 */
export function CitationPanel({
  citations,
  onClose,
}: {
  /** The passages of one document and pages; empty when the panel is closed. */
  citations: CitationResponse[]
  onClose: () => void
}) {
  const isOpen = citations.length > 0
  // React Aria's modal returns focus to the element that had it before opening: the chip.
  return (
    <ModalOverlay
      isOpen={isOpen}
      onOpenChange={(open) => !open && onClose()}
      isDismissable
      className="fixed inset-0 z-40 bg-slate-950/40 data-[entering]:animate-fade-in motion-reduce:animate-none"
    >
      <Modal className="fixed inset-y-0 right-0 flex w-full border-l border-line bg-surface text-fg shadow-2xl data-[entering]:animate-slide-in motion-reduce:animate-none sm:w-[min(42rem,92vw)]">
        <Dialog
          aria-labelledby="citation-heading"
          className="flex w-full flex-col outline-none"
        >
          {isOpen && <PanelContent citations={citations} onClose={onClose} />}
        </Dialog>
      </Modal>
    </ModalOverlay>
  )
}

function PanelContent({
  citations,
  onClose,
}: {
  citations: CitationResponse[]
  onClose: () => void
}) {
  const heading = useRef<HTMLHeadingElement>(null)
  const [first] = citations
  useEffect(() => {
    heading.current?.focus()
  }, [first.documentId, first.ordinal])

  const { documentId, documentTitle } = first
  if (!first.available || !documentId || !documentTitle) return null
  const page = first.pageStart ?? 1
  const where = pages(first)
  return (
    <>
      <div className="flex items-start justify-between gap-3 border-b border-line px-4 py-4 sm:px-6">
        <div className="min-w-0">
          <h2
            id="citation-heading"
            ref={heading}
            tabIndex={-1}
            className="font-semibold wrap-anywhere outline-none"
          >
            {numbers(citations)}: {documentTitle}
          </h2>
          <p className="text-sm text-muted">
            {where[0].toUpperCase() + where.slice(1)}
          </p>
        </div>
        <Button variant="secondary" size="sm" onPress={onClose}>
          Close
        </Button>
      </div>
      <div className="flex-1 space-y-5 overflow-y-auto px-4 py-5 sm:px-6">
        <figure className="space-y-2">
          <figcaption className="text-xs font-semibold tracking-wide text-muted uppercase">
            {citations.length === 1 ? 'Cited passage' : 'Cited passages'}
          </figcaption>
          {citations.map((citation) => (
            <blockquote
              key={citation.ordinal}
              className="border-l-4 border-accent pl-3 text-sm leading-relaxed whitespace-pre-line wrap-anywhere"
            >
              {citations.length > 1 && (
                <span className="mb-1 block text-xs font-semibold text-muted">
                  Source {citation.ordinal}
                </span>
              )}
              {citation.snippet}
            </blockquote>
          ))}
        </figure>
        <DocumentViewer
          documentId={documentId}
          title={documentTitle}
          page={page}
          className="h-[70vh]"
        />
        <Link to={`/documents/${documentId}?page=${page}`} className={LINK}>
          Open the document page
        </Link>
      </div>
    </>
  )
}
