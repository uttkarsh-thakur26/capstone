import { useSyncExternalStore } from 'react'

export type ThemeChoice = 'system' | 'light' | 'dark'

const STORAGE_KEY = 'theme'
const DARK_QUERY = '(prefers-color-scheme: dark)'

/** The saved choice; storage can be missing or blocked, and then the system decides. */
function readChoice(): ThemeChoice {
  try {
    const saved = window.localStorage.getItem(STORAGE_KEY)
    if (saved === 'light' || saved === 'dark') return saved
  } catch {
    // Storage blocked: follow the system.
  }
  return 'system'
}

let choice: ThemeChoice = 'system'
const listeners = new Set<() => void>()

/** Sets data-theme on <html>, which the dark: variant and the color tokens follow (index.css). */
function apply() {
  const dark =
    choice === 'dark' ||
    (choice === 'system' && (window.matchMedia?.(DARK_QUERY).matches ?? false))
  document.documentElement.dataset.theme = dark ? 'dark' : 'light'
}

/**
 * Applies the saved theme before the first render and follows the system setting from then on,
 * on every page, signed in or not. Called once from main.tsx.
 */
export function startTheme() {
  choice = readChoice()
  apply()
  window.matchMedia?.(DARK_QUERY).addEventListener('change', apply)
}

export function setThemeChoice(next: ThemeChoice) {
  try {
    if (next === 'system') window.localStorage.removeItem(STORAGE_KEY)
    else window.localStorage.setItem(STORAGE_KEY, next)
  } catch {
    // Not saved; it still applies to this visit.
  }
  choice = next
  apply()
  listeners.forEach((listener) => listener())
}

function subscribe(listener: () => void) {
  listeners.add(listener)
  return () => listeners.delete(listener)
}

/** The theme choice of the sidebar: saved in this browser only, applied at once. */
export function useTheme(): [ThemeChoice, (choice: ThemeChoice) => void] {
  const current = useSyncExternalStore(subscribe, () => choice)
  return [current, setThemeChoice]
}
