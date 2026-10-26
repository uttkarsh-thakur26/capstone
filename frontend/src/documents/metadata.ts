import { z } from 'zod'

/** The editable description of a document, as the upload and edit forms hold it (DOC-1, DOC-7). */
export const metadataSchema = z.object({
  title: z
    .string()
    .trim()
    .min(1, 'Enter a title.')
    .max(200, 'Use at most 200 characters.'),
  description: z.string().max(2000, 'Use at most 2000 characters.'),
  category: z.string().trim().max(100, 'Use at most 100 characters.'),
  documentDate: z.string(),
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

/** The request fields: an empty optional field is sent as absent, which the server stores as none. */
export function toMetadata(form: MetadataForm) {
  return {
    title: form.title,
    description: form.description || undefined,
    category: form.category || undefined,
    documentDate: form.documentDate || undefined,
  }
}
