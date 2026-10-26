import type { components } from './schema'

/** Shorter names for the generated API types (ADR-017). Never written by hand. */
type Schemas = components['schemas']

export type UserResponse = Schemas['UserResponse']
export type Role = UserResponse['role']
export type SessionResponse = Schemas['SessionResponse']
export type ProblemResponse = Schemas['ProblemResponse']
export type LoginRequest = Schemas['LoginRequest']
export type RegistrationRequest = Schemas['RegistrationRequest']
export type AdminUserResponse = Schemas['AdminUserResponse']
export type AdminUserPage = Schemas['PageResponseAdminUserResponse']
export type UpdateUserRequest = Schemas['UpdateUserRequest']

export type DocumentResponse = Schemas['DocumentResponse']
export type DocumentStatus = DocumentResponse['status']
export type DocumentSummary = Schemas['DocumentSummaryResponse']
export type DocumentPage = Schemas['PageResponseDocumentSummaryResponse']
export type UploadDocumentRequest = Schemas['UploadDocumentRequest']
export type UpdateDocumentRequest = Schemas['UpdateDocumentRequest']
export type UploadLimits = Schemas['UploadLimitsResponse']
export type GrantRequest = Schemas['GrantRequest']
export type GrantResponse = Schemas['GrantResponse']
export type GrantsResponse = Schemas['GrantsResponse']
export type UpdateGrantsRequest = Schemas['UpdateGrantsRequest']
export type UserSummary = Schemas['UserSummary']
export type UserSummaryPage = Schemas['PageResponseUserSummary']

export const ROLES: readonly Role[] = ['ADMIN', 'FACULTY', 'STUDENT']

/** The roles a document can be shared with; admins read every document anyway (D15). */
export const GRANTABLE_ROLES = [
  'STUDENT',
  'FACULTY',
] as const satisfies readonly Role[]
export type GrantableRole = (typeof GRANTABLE_ROLES)[number]

export const DOCUMENT_STATUSES: readonly DocumentStatus[] = [
  'UPLOADED',
  'PROCESSING',
  'INDEXED',
  'FAILED',
]
