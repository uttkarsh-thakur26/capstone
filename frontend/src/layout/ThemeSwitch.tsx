import { Label, Radio, RadioGroup } from 'react-aria-components'
import { useTheme, type ThemeChoice } from '../app/theme'
import { FOCUS_RING } from '../components/ui'

const CHOICES: { id: ThemeChoice; label: string }[] = [
  { id: 'system', label: 'System' },
  { id: 'light', label: 'Light' },
  { id: 'dark', label: 'Dark' },
]

/** System, light or dark: a radio group, so arrow keys move between the three (UI-8). */
export function ThemeSwitch() {
  const [choice, setChoice] = useTheme()
  return (
    <RadioGroup
      value={choice}
      onChange={(value) => setChoice(value as ThemeChoice)}
      orientation="horizontal"
      className="space-y-1.5"
    >
      <Label className="text-xs font-medium text-muted">Theme</Label>
      <div className="grid grid-cols-3 rounded-md border border-line p-0.5">
        {CHOICES.map(({ id, label }) => (
          <Radio
            key={id}
            value={id}
            className={`cursor-default rounded px-2 py-1 text-center text-xs font-medium text-muted data-[hovered]:text-fg data-[selected]:bg-subtle data-[selected]:text-fg ${FOCUS_RING}`}
          >
            {label}
          </Radio>
        ))}
      </div>
    </RadioGroup>
  )
}
