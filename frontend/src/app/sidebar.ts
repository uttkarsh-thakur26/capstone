import { useCallback, useState } from 'react'

const STORAGE_KEY = 'sidebar'

/**
 * Whether the sidebar stays open on large screens. Kept in this browser, like the theme: a reading
 * preference, not something the server needs to know. Storage can be missing or blocked, and then
 * the sidebar starts open.
 */
function readPinned(): boolean {
  try {
    return window.localStorage.getItem(STORAGE_KEY) !== 'hidden'
  } catch {
    return true
  }
}

export function useSidebarPinned(): [boolean, (pinned: boolean) => void] {
  const [pinned, setPinned] = useState(readPinned)
  const set = useCallback((next: boolean) => {
    setPinned(next)
    try {
      window.localStorage.setItem(STORAGE_KEY, next ? 'shown' : 'hidden')
    } catch {
      // Storage blocked: the choice lasts until the page is reloaded.
    }
  }, [])
  return [pinned, set]
}
