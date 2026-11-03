import { segments } from './markers'

/**
 * A small, safe subset of Markdown for answers (UI-6): paragraphs, line breaks, headings, bulleted
 * and numbered lists, bold and italics, with the `[n]` markers kept as their own nodes. The result
 * is data that the answer component turns into React elements; nothing is ever parsed or rendered
 * as HTML, so a tag in an answer stays visible text. Anything else (tables, links, code) stays as
 * the characters the model wrote.
 */
export type Inline =
  | { kind: 'text'; text: string }
  | { kind: 'strong'; children: Inline[] }
  | { kind: 'em'; children: Inline[] }
  | { kind: 'marker'; ordinals: number[] }
  | { kind: 'break' }

export type Block =
  | { kind: 'paragraph'; content: Inline[] }
  | { kind: 'heading'; content: Inline[] }
  | { kind: 'list'; ordered: boolean; start: number; items: Inline[][] }

const HEADING = /^\s{0,3}#{1,6}\s+(.*?)(?:\s+#+)?\s*$/
const BULLET = /^\s*[-*•]\s+(.*)$/
const NUMBERED = /^\s*(\d{1,3})[.)]\s+(.*)$/
const WORD = /[\p{L}\p{N}]/u

export function parseAnswer(text: string): Block[] {
  const blocks: Block[] = []
  let paragraph: string[] = []
  let list: { ordered: boolean; start: number; items: string[] } | null = null

  const endParagraph = () => {
    if (paragraph.length === 0) return
    const content: Inline[] = []
    paragraph.forEach((line, i) => {
      if (i > 0) content.push({ kind: 'break' })
      content.push(...parseInline(line))
    })
    blocks.push({ kind: 'paragraph', content })
    paragraph = []
  }
  const endList = () => {
    if (!list) return
    blocks.push({
      kind: 'list',
      ordered: list.ordered,
      start: list.start,
      items: list.items.map(parseInline),
    })
    list = null
  }

  for (const raw of text.replace(/\r\n?/g, '\n').split('\n')) {
    const line = raw.trimEnd()
    if (line.trim() === '') {
      endParagraph()
      endList()
      continue
    }
    const heading = HEADING.exec(line)
    if (heading) {
      endParagraph()
      endList()
      blocks.push({ kind: 'heading', content: parseInline(heading[1]) })
      continue
    }
    const bullet = BULLET.exec(line)
    const numbered = bullet ? null : NUMBERED.exec(line)
    if (bullet || numbered) {
      endParagraph()
      const ordered = numbered !== null
      if (!list || list.ordered !== ordered) {
        endList()
        list = { ordered, start: numbered ? Number(numbered[1]) : 1, items: [] }
      }
      list.items.push(bullet ? bullet[1] : numbered![2])
      continue
    }
    if (list) {
      // An indented line continues the last item; anything else ends the list.
      if (/^\s/.test(raw)) {
        list.items[list.items.length - 1] += ` ${line.trim()}`
        continue
      }
      endList()
    }
    paragraph.push(line)
  }
  endParagraph()
  endList()
  return blocks.map(dropRepeatedMarkers)
}

/** Bold (`**` or `__`) and italics (`*` or `_`), nested, with markers in any of them. */
export function parseInline(text: string): Inline[] {
  const out: Inline[] = []
  let plain = ''
  const flush = () => {
    if (plain) out.push(...withMarkers(plain))
    plain = ''
  }
  let i = 0
  while (i < text.length) {
    const pair = text.slice(i, i + 2)
    if (pair === '**' || pair === '__') {
      const end = text.indexOf(pair, i + 2)
      if (end > i + 2 && opens(text, i, 2) && closes(text, end, 2)) {
        flush()
        out.push({
          kind: 'strong',
          children: parseInline(text.slice(i + 2, end)),
        })
        i = end + 2
        continue
      }
    } else if (text[i] === '*' || text[i] === '_') {
      const end = closingSingle(text, i)
      if (end > i + 1) {
        flush()
        out.push({ kind: 'em', children: parseInline(text.slice(i + 1, end)) })
        i = end + 1
        continue
      }
    }
    plain += text[i]
    i += 1
  }
  flush()
  return out
}

/** A delimiter opens emphasis when text follows it, and an underscore only at a word's start. */
function opens(text: string, at: number, width: number): boolean {
  const next = text[at + width]
  if (next === undefined || /\s/.test(next)) return false
  return text[at] !== '_' || !WORD.test(text[at - 1] ?? '')
}

/** A delimiter closes emphasis after text, and an underscore only at a word's end. */
function closes(text: string, at: number, width: number): boolean {
  const before = text[at - 1]
  if (before === undefined || /\s/.test(before)) return false
  return text[at] !== '_' || !WORD.test(text[at + width] ?? '')
}

/** The single `*` or `_` that closes one opened at `at`, or -1. Never part of a `**` pair. */
function closingSingle(text: string, at: number): number {
  const mark = text[at]
  if (!opens(text, at, 1) || text[at + 1] === mark) return -1
  for (let j = at + 2; j < text.length; j++) {
    if (
      text[j] === mark &&
      text[j + 1] !== mark &&
      text[j - 1] !== mark &&
      closes(text, j, 1)
    ) {
      return j
    }
  }
  return -1
}

/** Text with its `[n]` markers; markers next to each other, like `[1][3]`, become one group. */
function withMarkers(text: string): Inline[] {
  const out: Inline[] = []
  for (const segment of segments(text)) {
    const last = out[out.length - 1]
    if (segment.kind === 'text') {
      out.push({ kind: 'text', text: segment.text })
      continue
    }
    const beforeLast = out[out.length - 2]
    if (last?.kind === 'marker') {
      last.ordinals = unique([...last.ordinals, ...segment.ordinals])
    } else if (
      last?.kind === 'text' &&
      last.text.trim() === '' &&
      beforeLast?.kind === 'marker'
    ) {
      out.pop()
      beforeLast.ordinals = unique([
        ...beforeLast.ordinals,
        ...segment.ordinals,
      ])
    } else {
      out.push({ kind: 'marker', ordinals: unique(segment.ordinals) })
    }
  }
  return out
}

function unique(ordinals: number[]): number[] {
  return [...new Set(ordinals)]
}

const key = (ordinals: number[]) =>
  [...ordinals].sort((a, b) => a - b).join(',')

/**
 * When the same sources are cited again and again in one paragraph or list, for example after
 * every item, only the last marker of each such run stays. A marker naming other sources is never
 * dropped, so every source the answer cites keeps its chip.
 */
function dropRepeatedMarkers(block: Block): Block {
  const found: { parent: Inline[]; node: Inline & { kind: 'marker' } }[] = []
  const walk = (nodes: Inline[]) => {
    for (const node of nodes) {
      if (node.kind === 'marker') found.push({ parent: nodes, node })
      else if (node.kind === 'strong' || node.kind === 'em') walk(node.children)
    }
  }
  const containers = block.kind === 'list' ? block.items : [block.content]
  containers.forEach(walk)

  const drop = new Set<Inline>()
  for (let i = 0; i < found.length - 1; i++) {
    if (key(found[i].node.ordinals) === key(found[i + 1].node.ordinals)) {
      drop.add(found[i].node)
    }
  }
  if (drop.size === 0) return block

  const prune = (nodes: Inline[]): Inline[] => {
    const out: Inline[] = []
    nodes.forEach((node, i) => {
      if (drop.has(node)) {
        // "text [1]." loses the space before the marker as well.
        const previous = out[out.length - 1]
        const next = nodes[i + 1]
        if (
          previous?.kind === 'text' &&
          (next === undefined ||
            (next.kind === 'text' && /^[\s.,;:!?)]/.test(next.text)) ||
            next.kind === 'break')
        ) {
          previous.text = previous.text.trimEnd()
        }
        return
      }
      if (node.kind === 'strong' || node.kind === 'em') {
        out.push({ ...node, children: prune(node.children) })
      } else {
        out.push(node)
      }
    })
    return out
  }
  return block.kind === 'list'
    ? { ...block, items: block.items.map(prune) }
    : { ...block, content: prune(block.content) }
}
