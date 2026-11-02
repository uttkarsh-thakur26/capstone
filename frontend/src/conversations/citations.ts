import type { CitationResponse } from '../api/types'

/** How a citation is named in the sources list and to screen readers. */
export function describe(citation: CitationResponse): string {
  if (!citation.available) return 'Source no longer available'
  return `${citation.documentTitle}, ${pages(citation)}`
}

/** The cited page or pages, in words. */
export function pages(citation: CitationResponse): string {
  return citation.pageStart === citation.pageEnd
    ? `page ${citation.pageStart}`
    : `pages ${citation.pageStart}–${citation.pageEnd}`
}
