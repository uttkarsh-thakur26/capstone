import { z } from 'zod'
import type { DocumentCategory } from '../api/types'

/**
 * The kinds of document, in the order the picker shows them, with the names readers see. The
 * server keeps the same fixed list and refuses anything else (ADR-040).
 */
export const CATEGORY_LABELS: Record<DocumentCategory, string> = {
  POLICY: 'Policy',
  SYLLABUS: 'Syllabus',
  COURSE_PLAN: 'Course plan',
  EXAMINATION: 'Examination',
  NOTICE: 'Notice',
  SCHEDULE: 'Schedule',
  HANDBOOK: 'Handbook',
  OTHER: 'Other',
}

export const CATEGORIES = Object.keys(CATEGORY_LABELS) as DocumentCategory[]

/** The name a reader sees for a category, or a dash when there is none. */
export function categoryLabel(category: DocumentCategory | null | undefined) {
  return category ? CATEGORY_LABELS[category] : '—'
}

export const TITLE_MIN = 3
export const TITLE_MAX = 200
export const TITLE_MIN_LETTERS = 3
export const DESCRIPTION_MAX = 2000
export const EARLIEST_DATE = '2000-01-01'

/** The hint under a title field. */
export const TITLE_HINT = `${TITLE_MIN} to ${TITLE_MAX} characters, with at least ${TITLE_MIN_LETTERS} letters.`

/** The category picker's choices, in the list's order. */
export const CATEGORY_OPTIONS = CATEGORIES.map((value) => ({
  value,
  label: CATEGORY_LABELS[value],
}))

/** The title as the server stores it: runs of white space made one space, then trimmed. */
export function normalizeTitle(title: string): string {
  return title.replace(/[\s\p{Z}]+/gu, ' ').trim()
}

/** A date as YYYY-MM-DD in the reader's own calendar. */
function isoDate(date: Date): string {
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${date.getFullYear()}-${month}-${day}`
}

/** The latest effective date allowed: one year from the reader's today. */
export function latestDate(now = new Date()): string {
  const year = now.getFullYear() + 1
  // Day 0 of the next month is the last day of this one: 29 February becomes 28 February in a
  // year without it, as the server's plusYears(1) does, instead of rolling over to 1 March.
  const lastDay = new Date(year, now.getMonth() + 1, 0).getDate()
  return isoDate(
    new Date(year, now.getMonth(), Math.min(now.getDate(), lastDay)),
  )
}

/** Whether YYYY-MM-DD names a day that exists, so 2026-02-30 does not. */
function isRealDate(value: string): boolean {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value)
  if (!match) return false
  const [year, month, day] = match.slice(1).map(Number)
  const date = new Date(year, month - 1, day)
  return (
    date.getFullYear() === year &&
    date.getMonth() === month - 1 &&
    date.getDate() === day
  )
}

function readable(value: string): string {
  const [year, month, day] = value.split('-').map(Number)
  return new Date(year, month - 1, day).toLocaleDateString(undefined, {
    dateStyle: 'medium',
  })
}

/**
 * The editable description of a document, as the upload and edit forms hold it (DOC-1, DOC-7).
 * The same rules as the server's, so mistakes show at once next to their field; the server checks
 * again and is the real check (ADR-040).
 */
export const metadataSchema = z.object({
  title: z.string().superRefine((value, context) => {
    const title = normalizeTitle(value)
    const length = [...title].length
    let message: string | null = null
    if (title === '') message = 'Enter a title.'
    else if (length < TITLE_MIN || length > TITLE_MAX)
      message = `Use ${TITLE_MIN} to ${TITLE_MAX} characters.`
    else if ((title.match(/\p{L}/gu) ?? []).length < TITLE_MIN_LETTERS)
      message = `Use at least ${TITLE_MIN_LETTERS} letters, not only digits or symbols.`
    if (message) context.addIssue({ code: 'custom', message })
  }),
  description: z
    .string()
    .refine(
      (value) => value.trim().length <= DESCRIPTION_MAX,
      `Use at most ${DESCRIPTION_MAX.toLocaleString('en')} characters.`,
    ),
  category: z.union([
    z.enum(CATEGORIES as [DocumentCategory, ...DocumentCategory[]]),
    z.literal(''),
  ]),
  documentDate: z.string().superRefine((value, context) => {
    if (value === '') return
    if (!isRealDate(value)) {
      context.addIssue({ code: 'custom', message: 'Enter a real date.' })
      return
    }
    const latest = latestDate()
    if (value < EARLIEST_DATE || value > latest) {
      context.addIssue({
        code: 'custom',
        message: `Pick a date from ${readable(EARLIEST_DATE)} to ${readable(latest)}.`,
      })
    }
  }),
})
export type MetadataForm = z.infer<typeof metadataSchema>

export const METADATA_FIELDS = [
  'title',
  'description',
  'category',
  'documentDate',
] as const

export const EMPTY_METADATA: MetadataForm = {
  title: '',
  description: '',
  category: '',
  documentDate: '',
}

/**
 * The request fields, written the way the server stores them: an empty optional field is sent as
 * absent, which the server stores as none.
 */
export function toMetadata(form: MetadataForm) {
  const description = form.description.trim()
  return {
    title: normalizeTitle(form.title),
    description: description || undefined,
    category: form.category || undefined,
    documentDate: form.documentDate || undefined,
  }
}
