import { QueryClient } from '@tanstack/react-query'
import { ApiError } from '../api/client'

/** A failed request is retried once, unless the server answered that the request itself is wrong. */
export function createQueryClient(): QueryClient {
  return new QueryClient({
    defaultOptions: {
      queries: {
        retry: (failures, error) =>
          failures < 1 && !(error instanceof ApiError && error.status < 500),
      },
    },
  })
}
