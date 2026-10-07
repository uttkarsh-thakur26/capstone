import '@testing-library/jest-dom/vitest'
import { cleanup } from '@testing-library/react'
import { afterEach, vi } from 'vitest'
import { setAccessToken } from '../api/client'

afterEach(() => {
  cleanup()
  vi.unstubAllGlobals()
  // The access token lives in a module variable; no test may see another test's token.
  setAccessToken(null)
})
