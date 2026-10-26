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
}

const FIELD =
  'rounded-md border border-slate-400 bg-white px-3 py-2 text-slate-900 outline-none data-[focused]:outline-2 data-[focused]:outline-indigo-600 data-[invalid]:border-red-700 data-[disabled]:opacity-50 dark:border-slate-500 dark:bg-slate-900 dark:text-slate-100'

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
      <Label className="text-sm font-medium">{label}</Label>
      {multiline ? (
        <TextArea ref={ref} rows={4} className={FIELD} />
      ) : (
        <Input ref={ref} className={FIELD} />
      )}
      {description && (
        <Text
          slot="description"
          className="text-sm text-slate-600 dark:text-slate-300"
        >
          {description}
        </Text>
      )}
      <FieldError className="text-sm text-red-700 dark:text-red-400">
        {fieldState.error?.message}
      </FieldError>
    </TextField>
  )
}
