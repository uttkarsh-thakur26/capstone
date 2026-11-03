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

/** Citations of one answer that point to the same document and pages: one source chip. */
export interface SourceGroup {
  key: string
  citations: CitationResponse[]
}

/**
 * The answer's sources, one per document and pages, in the order they are first cited. Each
 * passage stays in the group, so the panel can show all of them. A source that is no longer
 * available names nothing, so it is never merged with another (CONV-6).
 */
export function groupSources(citations: CitationResponse[]): SourceGroup[] {
  const groups = new Map<string, SourceGroup>()
  for (const citation of [...citations].sort((a, b) => a.ordinal - b.ordinal)) {
    const key = citation.available
      ? `${citation.documentId}:${citation.pageStart}-${citation.pageEnd}`
      : `gone:${citation.ordinal}`
    const group = groups.get(key)
    if (group) group.citations.push(citation)
    else groups.set(key, { key, citations: [citation] })
  }
  return [...groups.values()]
}

/** "Source 1" or "Sources 1 and 2", for labels and headings. */
export function numbers(citations: CitationResponse[]): string {
  const ordinals = citations.map((c) => c.ordinal)
  if (ordinals.length === 1) return `Source ${ordinals[0]}`
  return `Sources ${ordinals.slice(0, -1).join(', ')} and ${ordinals[ordinals.length - 1]}`
}
