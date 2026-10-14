import { QueryClientProvider, type QueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { createBrowserRouter } from 'react-router'
import { RouterProvider } from 'react-router/dom'
import { createQueryClient } from './app/queryClient'
import { AuthProvider } from './auth/AuthProvider'
import { routes } from './routes'

type Router = ReturnType<typeof createBrowserRouter>

/** Server state, the session and the routes. Tests pass a memory router and their own client. */
function App({
  router,
  queryClient,
}: {
  router?: Router
  queryClient?: QueryClient
}) {
  const [client] = useState(() => queryClient ?? createQueryClient())
  const [appRouter] = useState(() => router ?? createBrowserRouter(routes))
  return (
    <QueryClientProvider client={client}>
      <AuthProvider>
        <RouterProvider router={appRouter} />
      </AuthProvider>
    </QueryClientProvider>
  )
}

export default App
