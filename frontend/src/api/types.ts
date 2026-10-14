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

export const ROLES: readonly Role[] = ['ADMIN', 'FACULTY', 'STUDENT']
