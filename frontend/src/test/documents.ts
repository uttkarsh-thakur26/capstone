import type {
  DocumentResponse,
  DocumentSummary,
  GrantResponse,
} from '../api/types'

/** Test data in the shape of the generated API types. */

export const DOC_ID = '0199a1b2-0000-7000-8000-00000000d0c1'

export function documentDetail(
  overrides: Partial<DocumentResponse> = {},
): DocumentResponse {
  return {
    id: DOC_ID,
    title: 'Examination Policy 2026',
    description: 'Rules for all examinations.',
    category: 'Policy',
    documentDate: '2026-07-01',
    originalFilename: 'exam-policy.pdf',
    contentType: 'application/pdf',
    sizeBytes: 245_760,
    pageCount: 12,
    status: 'UPLOADED',
    archived: false,
    owner: { id: 'owner-id', displayName: 'Fatima Faculty' },
    createdAt: '2026-10-20T09:30:00Z',
    updatedAt: '2026-10-20T09:30:00Z',
    canManage: true,
    ...overrides,
  }
}

export function documentSummary(
  title: string,
  overrides: Partial<DocumentSummary> = {},
): DocumentSummary {
  return {
    id: `id-${title.toLowerCase().replace(/\s+/g, '-')}`,
    title,
    category: 'Policy',
    status: 'UPLOADED',
    archived: false,
    owner: { id: 'owner-id', displayName: 'Fatima Faculty' },
    sizeBytes: 2048,
    pageCount: 1,
    createdAt: '2026-10-20T09:30:00Z',
    ...overrides,
  }
}

export function documentPage(items: DocumentSummary[]) {
  return {
    items,
    page: 0,
    size: 20,
    totalItems: items.length,
    totalPages: items.length ? 1 : 0,
  }
}

export const STUDENT_GRANT: GrantResponse = { type: 'ROLE', role: 'STUDENT' }

export function personGrant(name: string): GrantResponse {
  const slug = name.toLowerCase().replace(/\s+/g, '.')
  return {
    type: 'USER',
    user: { id: `id-${slug}`, displayName: name, email: `${slug}@example.org` },
  }
}
