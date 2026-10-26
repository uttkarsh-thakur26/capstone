import { CheckboxGroup, Label } from 'react-aria-components'
import { GRANTABLE_ROLES, type GrantableRole } from '../api/types'
import { Button } from '../components/Button'
import { Checkbox } from '../components/Checkbox'
import type { Sharing } from './sharing'
import { UserPicker } from './UserPicker'

const ROLE_LABELS: Record<GrantableRole, string> = {
  STUDENT: 'All students',
  FACULTY: 'All faculty',
}

/**
 * Chooses roles and people to share a document with. Nothing chosen means only the owner and
 * admins can read it (ACL-5).
 */
export function GrantsEditor({
  value,
  onChange,
  isDisabled,
}: {
  value: Sharing
  onChange: (sharing: Sharing) => void
  isDisabled?: boolean
}) {
  return (
    <div className="space-y-4">
      <CheckboxGroup
        value={value.roles}
        onChange={(roles) =>
          onChange({
            ...value,
            roles: GRANTABLE_ROLES.filter((role) => roles.includes(role)),
          })
        }
        isDisabled={isDisabled}
        className="flex flex-col gap-2"
      >
        <Label className="text-sm font-medium">Everyone with a role</Label>
        {GRANTABLE_ROLES.map((role) => (
          <Checkbox key={role} value={role}>
            {ROLE_LABELS[role]}
          </Checkbox>
        ))}
      </CheckboxGroup>

      <div className="space-y-2">
        <p className="text-sm font-medium" id="shared-people">
          People
        </p>
        {value.people.length === 0 ? (
          <p className="text-sm text-slate-600 dark:text-slate-300">
            No one is added by name.
          </p>
        ) : (
          <ul aria-labelledby="shared-people" className="space-y-1">
            {value.people.map((person) => (
              <li
                key={person.id}
                className="flex flex-wrap items-center gap-x-3 gap-y-1 text-sm"
              >
                <span>
                  {person.displayName}{' '}
                  <span className="text-slate-600 dark:text-slate-300">
                    {person.email}
                  </span>
                </span>
                <Button
                  variant="secondary"
                  className="px-2 py-1"
                  aria-label={`Remove ${person.displayName}`}
                  isDisabled={isDisabled}
                  onPress={() =>
                    onChange({
                      ...value,
                      people: value.people.filter((p) => p.id !== person.id),
                    })
                  }
                >
                  Remove
                </Button>
              </li>
            ))}
          </ul>
        )}
        <UserPicker
          excludeIds={value.people.map((person) => person.id)}
          onPick={(person) =>
            onChange({ ...value, people: [...value.people, person] })
          }
          isDisabled={isDisabled}
        />
      </div>
    </div>
  )
}
