import {
  GRANTABLE_ROLES,
  type GrantableRole,
  type GrantRequest,
  type GrantResponse,
  type UserSummary,
} from '../api/types'

/** Who may read a document besides its owner and admins (ACL-1). */
export interface Sharing {
  roles: GrantableRole[]
  people: UserSummary[]
}

export const NOT_SHARED: Sharing = { roles: [], people: [] }

export function toGrantRequests(sharing: Sharing): GrantRequest[] {
  return [
    ...sharing.roles.map((role): GrantRequest => ({ type: 'ROLE', role })),
    ...sharing.people.map((person): GrantRequest => ({
      type: 'USER',
      userId: person.id,
    })),
  ]
}

export function fromGrants(grants: readonly GrantResponse[]): Sharing {
  return {
    roles: GRANTABLE_ROLES.filter((role) =>
      grants.some((grant) => grant.type === 'ROLE' && grant.role === role),
    ),
    people: grants.flatMap((grant) =>
      grant.type === 'USER' && grant.user ? [grant.user] : [],
    ),
  }
}
