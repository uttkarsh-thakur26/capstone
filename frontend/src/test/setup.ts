import '@testing-library/jest-dom/vitest'
import { cleanup } from '@testing-library/react'
import { afterEach, vi } from 'vitest'
import { setSession } from '../api/client'

afterEach(() => {
  cleanup()
  vi.unstubAllGlobals()
  // The session lives in module variables; no test may see another test's session.
  setSession(null)
})
