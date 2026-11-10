import {
  Button as AriaButton,
  Label,
  ListBox,
  ListBoxItem,
  Popover,
  Select,
  SelectValue,
} from 'react-aria-components'
import { LABEL, LIST_ITEM, POPOVER, SELECT_TRIGGER, SELECT_VALUE } from './ui'

/** A labelled select for a list filter: one value, chosen from a short fixed list. */
export function FilterSelect<T extends string>({
  label,
  value,
  onChange,
  options,
}: {
  label: string
  value: T
  onChange: (value: T) => void
  options: { id: T; label: string }[]
}) {
  return (
    <Select
      value={value}
      onChange={(key) => {
        if (key !== null) onChange(key as T)
      }}
      className="flex flex-col gap-1"
    >
      <Label className={LABEL}>{label}</Label>
      <AriaButton className={SELECT_TRIGGER}>
        <SelectValue className={SELECT_VALUE} />
        <span aria-hidden="true">▾</span>
      </AriaButton>
      <Popover className={POPOVER}>
        <ListBox className="p-1 outline-none">
          {options.map((option) => (
            <ListBoxItem key={option.id} id={option.id} className={LIST_ITEM}>
              {option.label}
            </ListBoxItem>
          ))}
        </ListBox>
      </Popover>
    </Select>
  )
}
