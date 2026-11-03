import { useRouteError } from 'react-router'
import { ErrorState } from '../components/states'

/**
 * Shown when a page cannot be opened, most often because its code chunk failed to load (the app
 * was updated or the network dropped). Reloading fetches the current chunks.
 */
export function RouteError() {
  const error = useRouteError()
  console.error(error)
  return (
    <main className="mx-auto max-w-xl p-6">
      <ErrorState onRetry={() => window.location.reload()}>
        This page could not be opened. Reload to try again.
      </ErrorState>
    </main>
  )
}
