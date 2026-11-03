import { Switch } from 'react-aria-components'

/** An on/off switch whose visible text says the current state; the label names the account. */
export function EnabledSwitch({
  label,
  isEnabled,
  onChange,
  isDisabled,
}: {
  label: string
  isEnabled: boolean
  onChange: (enabled: boolean) => void
  isDisabled?: boolean
}) {
  return (
    <Switch
      aria-label={label}
      isSelected={isEnabled}
      onChange={onChange}
      isDisabled={isDisabled}
      className="group flex items-center gap-2 text-sm outline-none data-[disabled]:opacity-50"
    >
      <span className="flex h-5 w-9 items-center rounded-full bg-line-strong px-0.5 transition group-data-[focus-visible]:outline-2 group-data-[focus-visible]:outline-offset-2 group-data-[focus-visible]:outline-focus group-data-[selected]:bg-accent">
        <span className="h-4 w-4 rounded-full bg-white transition group-data-[selected]:translate-x-4" />
      </span>
      {isEnabled ? 'Enabled' : 'Disabled'}
    </Switch>
  )
}
