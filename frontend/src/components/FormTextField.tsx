import {
  useController,
  type Control,
  type FieldPath,
  type FieldValues,
} from 'react-hook-form'
import {
  FieldError,
  Input,
  Label,
  Text,
  TextArea,
  TextField,
} from 'react-aria-components'
import { FIELD, FIELD_ERROR, HINT, LABEL } from './ui'

interface FormTextFieldProps<T extends FieldValues> {
  control: Control<T>
  name: FieldPath<T>
  label: string
  type?: 'text' | 'email' | 'password' | 'date'
  autoComplete?: string
  description?: string
  /** A text area for longer text, such as a description. */
  multiline?: boolean
  isDisabled?: boolean
  /** For a date: the earliest and latest the browser's picker offers, as YYYY-MM-DD. */
  min?: string
  max?: string
}

/**
 * A labelled text field bound to React Hook Form. The label, the description and the error are
 * wired to the input by React Aria, so screen readers announce them (UI-8).
 */
export function FormTextField<T extends FieldValues>({
  control,
  name,
  label,
  type = 'text',
  autoComplete,
  description,
  multiline,
  isDisabled,
  min,
  max,
}: FormTextFieldProps<T>) {
  // Destructured, so only `ref` is treated as a ref by React's rules of refs.
  const {
    field: { ref, name: fieldName, value, onChange, onBlur },
    fieldState,
  } = useController({ control, name })
  return (
    <TextField
      name={fieldName}
      value={value ?? ''}
      onChange={onChange}
      onBlur={onBlur}
      type={type}
      autoComplete={autoComplete}
      isInvalid={fieldState.invalid}
      isDisabled={isDisabled}
      validationBehavior="aria"
      className="flex flex-col gap-1"
    >
      <Label className={LABEL}>{label}</Label>
      {multiline ? (
        <TextArea ref={ref} rows={4} className={FIELD} />
      ) : (
        <Input ref={ref} min={min} max={max} className={FIELD} />
      )}
      {description && (
        <Text slot="description" className={HINT}>
          {description}
        </Text>
      )}
      <FieldError className={FIELD_ERROR}>
        {fieldState.error?.message}
      </FieldError>
    </TextField>
  )
}
