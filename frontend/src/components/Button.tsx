import {
  Button as AriaButton,
  type ButtonProps as AriaButtonProps,
} from 'react-aria-components'
import { Link, type LinkProps } from 'react-router'
import { FOCUS_RING, FOCUS_RING_NATIVE } from './ui'

const VARIANTS = {
  primary:
    'bg-accent text-white hover:bg-accent-hover data-[hovered]:bg-accent-hover',
  secondary:
    'border border-line-strong bg-surface text-fg hover:bg-subtle data-[hovered]:bg-subtle',
  ghost: 'text-fg hover:bg-subtle data-[hovered]:bg-subtle',
  danger:
    'bg-red-700 text-white hover:bg-red-800 data-[hovered]:bg-red-800 data-[pressed]:bg-red-900',
} as const

const SIZES = {
  md: 'gap-2 px-4 py-2 text-sm',
  sm: 'gap-1.5 px-3 py-1.5 text-sm',
} as const

type Variant = keyof typeof VARIANTS
type Size = keyof typeof SIZES

const BASE =
  'inline-flex items-center justify-center rounded-md font-medium whitespace-nowrap transition-colors data-[disabled]:opacity-50'

export interface ButtonProps extends AriaButtonProps {
  variant?: Variant
  size?: Size
  className?: string
}

/** React Aria's button: keyboard, touch and screen-reader behavior included (ADR-021, UI-8). */
export function Button({
  variant = 'primary',
  size = 'md',
  className = '',
  ...props
}: ButtonProps) {
  return (
    <AriaButton
      {...props}
      className={`${BASE} ${FOCUS_RING} ${VARIANTS[variant]} ${SIZES[size]} ${className}`}
    />
  )
}

/** A link that looks like a button, for actions that open another page. */
export function ButtonLink({
  variant = 'primary',
  size = 'md',
  className = '',
  ...props
}: LinkProps & { variant?: Variant; size?: Size }) {
  return (
    <Link
      {...props}
      className={`${BASE} ${FOCUS_RING_NATIVE} ${VARIANTS[variant]} ${SIZES[size]} ${className}`}
    />
  )
}
