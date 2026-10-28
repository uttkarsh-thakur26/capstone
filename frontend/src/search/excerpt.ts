import type { SearchHighlight } from '../api/types'

/** A run of passage text, marked when it is a word that matched the query. */
export interface Segment {
  text: string
  marked: boolean
}

/** About four lines of a result card. */
export const EXCERPT_CHARS = 320

/** Text kept before the first matched word, so it is seen in its sentence. */
const LEAD_CHARS = 80

/**
 * The part of a passage shown before "Show whole passage": from its start, or from shortly before
 * the first matched word when that word would otherwise be cut off. Cuts fall on spaces.
 */
export function excerptRange(
  text: string,
  highlights: readonly SearchHighlight[],
): { start: number; end: number } {
  if (text.length <= EXCERPT_CHARS) return { start: 0, end: text.length }
  let start = 0
  const first = highlights[0]
  if (first && first.end > EXCERPT_CHARS - LEAD_CHARS) {
    start = Math.max(0, first.start - LEAD_CHARS)
    const space = text.indexOf(' ', start)
    if (start > 0 && space !== -1 && space < first.start) start = space + 1
  }
  let end = Math.min(text.length, start + EXCERPT_CHARS)
  if (end < text.length) {
    const space = text.lastIndexOf(' ', end)
    if (space > start) end = space
  }
  return { start, end }
}

/**
 * Splits `text[start, end)` into plain and matched runs. Highlights are positions in UTF-16 code
 * units, as the server sends them, which is how JavaScript indexes strings too.
 */
export function segments(
  text: string,
  highlights: readonly SearchHighlight[],
  start = 0,
  end = text.length,
): Segment[] {
  const result: Segment[] = []
  let at = start
  for (const h of highlights) {
    const from = Math.max(h.start, at)
    const to = Math.min(h.end, end)
    if (to <= from) continue
    if (from > at) result.push({ text: text.slice(at, from), marked: false })
    result.push({ text: text.slice(from, to), marked: true })
    at = to
  }
  if (at < end) result.push({ text: text.slice(at, end), marked: false })
  return result
}
