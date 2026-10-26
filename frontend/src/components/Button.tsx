import {
  Button as AriaButton,
  type ButtonProps as AriaButtonProps,
} from 'react-aria-components'

const VARIANTS = {
  primary:
    'bg-indigo-700 text-white data-[hovered]:bg-indigo-800 data-[pressed]:bg-indigo-900',
  secondary:
    'border border-slate-300 bg-white text-slate-900 data-[hovered]:bg-slate-100 dark:border-slate-600 dark:bg-slate-900 dark:text-slate-100 dark:data-[hovered]:bg-slate-800',
  danger:
    'bg-red-700 text-white data-[hovered]:bg-red-800 data-[pressed]:bg-red-900',
} as const

export interface ButtonProps extends AriaButtonProps {
  variant?: keyof typeof VARIANTS
  className?: string
}

/** React Aria's button: keyboard, touch and screen-reader behavior included (ADR-021, UI-8). */
export function Button({
  variant = 'primary',
  className = '',
  ...props
}: ButtonProps) {
  return (
    <AriaButton
      {...props}
      className={`inline-flex items-center justify-center rounded-md px-4 py-2 text-sm font-medium outline-none data-[disabled]:opacity-50 data-[focus-visible]:outline-2 data-[focus-visible]:outline-offset-2 data-[focus-visible]:outline-indigo-600 ${VARIANTS[variant]} ${className}`}
    />
  )
}
