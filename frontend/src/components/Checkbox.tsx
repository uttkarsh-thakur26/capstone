import type { ReactNode } from 'react'
import {
  Checkbox as AriaCheckbox,
  type CheckboxProps,
} from 'react-aria-components'

/** React Aria's checkbox with a visible box; keyboard and screen-reader behavior included (UI-8). */
export function Checkbox({
  children,
  ...props
}: Omit<CheckboxProps, 'children' | 'className'> & { children: ReactNode }) {
  return (
    <AriaCheckbox
      {...props}
      className="group flex items-center gap-2 text-sm outline-none data-[disabled]:opacity-50"
    >
      {({ isSelected }) => (
        <>
          <span
            aria-hidden="true"
            className="flex h-4 w-4 items-center justify-center rounded border border-line-strong bg-surface text-white group-data-[focus-visible]:outline-2 group-data-[focus-visible]:outline-offset-2 group-data-[focus-visible]:outline-focus group-data-[selected]:border-accent group-data-[selected]:bg-accent"
          >
            {isSelected && (
              <svg viewBox="0 0 12 12" className="h-3 w-3" fill="none">
                <path
                  d="M2.5 6.5 5 9l4.5-5.5"
                  stroke="currentColor"
                  strokeWidth="2"
                  strokeLinecap="round"
                  strokeLinejoin="round"
                />
              </svg>
            )}
          </span>
          {children}
        </>
      )}
    </AriaCheckbox>
  )
}
