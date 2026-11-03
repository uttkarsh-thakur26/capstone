import '@testing-library/jest-dom/vitest'
import { cleanup, configure } from '@testing-library/react'
import { afterEach, vi } from 'vitest'
import { setSession } from '../api/client'

// Pages are lazy routes (routes.tsx). Loading them once here, before any test, keeps a cold module
// load out of each test's waiting time; the routes still go through React Router's lazy loading.
import.meta.glob('../pages/*Page.tsx', { eager: true })

// findBy and waitFor give up after 1 s by default. The first render of a test file, with every
// worker busy at once, was measured at 1.1 to 1.2 s on the development machine, which failed one
// file in a few runs (16 of 17). 3 s leaves room; a test that waits for something that never
// comes still fails.
configure({ asyncUtilTimeout: 3000 })

afterEach(() => {
  cleanup()
  vi.unstubAllGlobals()
  // The session lives in module variables; no test may see another test's session.
  setSession(null)
})
