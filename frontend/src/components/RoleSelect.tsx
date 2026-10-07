import {
  Button,
  ListBox,
  ListBoxItem,
  Popover,
  Select,
  SelectValue,
} from 'react-aria-components'
import { ROLES, type Role } from '../api/types'

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
      <Button className="flex min-w-32 items-center justify-between gap-2 rounded-md border border-slate-400 bg-white px-3 py-1.5 text-sm outline-none data-[disabled]:opacity-50 data-[focus-visible]:outline-2 data-[focus-visible]:outline-indigo-600 dark:border-slate-500 dark:bg-slate-900">
        <SelectValue />
        <span aria-hidden="true">▾</span>
      </Button>
      <Popover className="min-w-(--trigger-width) rounded-md border border-slate-300 bg-white shadow-lg dark:border-slate-600 dark:bg-slate-900">
        <ListBox className="p-1 outline-none">
          {ROLES.map((role) => (
            <ListBoxItem
              key={role}
              id={role}
              className="cursor-default rounded px-3 py-1.5 text-sm outline-none data-[focused]:bg-indigo-100 data-[selected]:font-semibold dark:data-[focused]:bg-indigo-950"
            >
              {role}
            </ListBoxItem>
          ))}
        </ListBox>
      </Popover>
    </Select>
  )
}
