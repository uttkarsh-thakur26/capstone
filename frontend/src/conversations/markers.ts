/** A run of answer text, or a citation marker with the source numbers it names. */
export type Segment =
  | { kind: 'text'; text: string }
  | { kind: 'marker'; text: string; ordinals: number[] }

/**
 * The `[n]` and `[1, 3]` markers the server keeps in an answer (11.1 step 8). Numbers have at
 * most three digits, as on the server, so a year in brackets stays text.
 */
const MARKER = /\[(\d{1,3}(?:\s*,\s*\d{1,3})*)\]/g

/**
 * Splits an answer into text and markers, so markers can become citation chips while everything
 * else stays plain text (UI-6). Nothing here is ever treated as HTML.
 */
export function segments(text: string): Segment[] {
  const parts: Segment[] = []
  let last = 0
  for (const match of text.matchAll(MARKER)) {
    if (match.index > last) {
      parts.push({ kind: 'text', text: text.slice(last, match.index) })
    }
    parts.push({
      kind: 'marker',
      text: match[0],
      ordinals: match[1].split(',').map((n) => Number(n.trim())),
    })
    last = match.index + match[0].length
  }
  if (last < text.length) parts.push({ kind: 'text', text: text.slice(last) })
  return parts
}
