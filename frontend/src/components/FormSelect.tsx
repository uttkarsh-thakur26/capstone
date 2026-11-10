import {
  useController,
  type Control,
  type FieldPath,
  type FieldValues,
} from 'react-hook-form'
import {
  Button,
  FieldError,
  Label,
  ListBox,
  ListBoxItem,
  Popover,
  Select,
  SelectValue,
  Text,
} from 'react-aria-components'
import {
  FIELD_ERROR,
  HINT,
  LABEL,
  LIST_ITEM,
  POPOVER,
  SELECT_TRIGGER,
  SELECT_VALUE,
} from './ui'

interface FormSelectProps<T extends FieldValues> {
  control: Control<T>
  name: FieldPath<T>
  label: string
  /** The choices, as value and the text shown. */
  options: readonly { value: string; label: string }[]
  /** Shown, and stored as the empty string, when nothing is picked. */
  emptyLabel: string
  description?: string
  isDisabled?: boolean
}

/**
 * A labelled picker bound to React Hook Form, for a value from a fixed list. React Aria wires the
 * label, the description and the error to it and gives it keyboard and screen-reader support
 * (UI-8). Picking the empty choice clears the value.
 */
export function FormSelect<T extends FieldValues>({
  control,
  name,
  label,
  options,
  emptyLabel,
  description,
  isDisabled,
}: FormSelectProps<T>) {
  const {
    field: { ref, value, onChange, onBlur },
    fieldState,
  } = useController({ control, name })
  return (
    <Select
      selectedKey={value === '' || value == null ? EMPTY : value}
      onSelectionChange={(key) => onChange(key === EMPTY ? '' : String(key))}
      onBlur={onBlur}
      isInvalid={fieldState.invalid}
      isDisabled={isDisabled}
      validationBehavior="aria"
      className="flex flex-col gap-1"
    >
      <Label className={LABEL}>{label}</Label>
      <Button ref={ref} className={SELECT_TRIGGER}>
        <SelectValue className={SELECT_VALUE} />
        <span aria-hidden="true">▾</span>
      </Button>
      {description && (
        <Text slot="description" className={HINT}>
          {description}
        </Text>
      )}
      <FieldError className={FIELD_ERROR}>
        {fieldState.error?.message}
      </FieldError>
      <Popover className={POPOVER}>
        <ListBox className="max-h-72 overflow-auto p-1 outline-none">
          <ListBoxItem id={EMPTY} className={LIST_ITEM}>
            {emptyLabel}
          </ListBoxItem>
          {options.map((option) => (
            <ListBoxItem
              key={option.value}
              id={option.value}
              className={LIST_ITEM}
            >
              {option.label}
            </ListBoxItem>
          ))}
        </ListBox>
      </Popover>
    </Select>
  )
}

/** The key of the empty choice; never a category's name. */
const EMPTY = '__none__'
