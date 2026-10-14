import { QueryClient } from '@tanstack/react-query'
import { render } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { createMemoryRouter } from 'react-router'
import App from '../App'
import { routes } from '../routes'

/** The whole app at a path, with a memory router and no retries, against the stubbed fetch. */
export function renderApp(path = '/') {
  const router = createMemoryRouter(routes, { initialEntries: [path] })
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  })
  const user = userEvent.setup()
  render(<App router={router} queryClient={queryClient} />)
  return { router, queryClient, user }
}
