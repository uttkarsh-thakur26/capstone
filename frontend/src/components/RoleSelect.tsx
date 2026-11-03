import {
  Button,
  ListBox,
  ListBoxItem,
  Popover,
  Select,
  SelectValue,
} from 'react-aria-components'
import { ROLES, type Role } from '../api/types'
import { LIST_ITEM, POPOVER, SELECT_TRIGGER } from './ui'

/** A role picker with keyboard and screen-reader support from React Aria (UI-8). */
export function RoleSelect({
  label,
  value,
  onChange,
  isDisabled,
}: {
  label: string
  value: Role
  onChange: (role: Role) => void
  isDisabled?: boolean
}) {
  return (
    <Select
      aria-label={label}
      value={value}
      onChange={(key) => {
        if (key !== null && key !== value) onChange(key as Role)
      }}
      isDisabled={isDisabled}
    >
      <Button className={`${SELECT_TRIGGER} min-w-32 py-1.5`}>
        <SelectValue />
        <span aria-hidden="true">▾</span>
      </Button>
      <Popover className={POPOVER}>
        <ListBox className="p-1 outline-none">
          {ROLES.map((role) => (
            <ListBoxItem key={role} id={role} className={LIST_ITEM}>
              {role}
            </ListBoxItem>
          ))}
        </ListBox>
      </Popover>
    </Select>
  )
}
