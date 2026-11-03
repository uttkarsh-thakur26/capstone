import type { RouteObject } from 'react-router'
import { RequireAuth } from './auth/RequireAuth'
import { AppShell } from './layout/AppShell'
import { RouteError } from './layout/RouteError'
import { Starting } from './layout/Starting'

/*
 * Each page is its own chunk, fetched the first time its route is opened, so the first page loads
 * only the shell, that page and the libraries they use. Sign-in is a chunk too: a returning user
 * with a session never needs its form libraries.
 */

/** The routes of 16.2 that exist so far. Everything except sign-in and registration needs a session. */
export const routes: RouteObject[] = [
  {
    HydrateFallback: Starting,
    ErrorBoundary: RouteError,
    children: [
      {
        path: '/login',
        lazy: async () => ({
          Component: (await import('./pages/LoginPage')).LoginPage,
        }),
      },
      {
        path: '/register',
        lazy: async () => ({
          Component: (await import('./pages/RegisterPage')).RegisterPage,
        }),
      },
      {
        element: (
          <RequireAuth>
            <AppShell />
          </RequireAuth>
        ),
        children: [
          {
            index: true,
            lazy: async () => ({
              Component: (await import('./pages/HomePage')).HomePage,
            }),
          },
          {
            path: 'documents',
            lazy: async () => ({
              Component: (await import('./pages/DocumentsPage')).DocumentsPage,
            }),
          },
          {
            path: 'documents/upload',
            lazy: async () => {
              const { UploadDocumentPage } =
                await import('./pages/UploadDocumentPage')
              return {
                element: (
                  <RequireAuth roles={['ADMIN', 'FACULTY']}>
                    <UploadDocumentPage />
                  </RequireAuth>
                ),
              }
            },
          },
          {
            path: 'documents/:id',
            lazy: async () => ({
              Component: (await import('./pages/DocumentDetailPage'))
                .DocumentDetailPage,
            }),
          },
          {
            path: 'search',
            lazy: async () => ({
              Component: (await import('./pages/SearchPage')).SearchPage,
            }),
          },
          {
            path: 'assistant',
            handle: { chat: true },
            lazy: async () => ({
              Component: (await import('./pages/AssistantPage')).AssistantPage,
            }),
          },
          {
            path: 'conversations',
            lazy: async () => ({
              Component: (await import('./pages/ConversationsPage'))
                .ConversationsPage,
            }),
          },
          {
            path: 'conversations/:id',
            handle: { chat: true },
            lazy: async () => ({
              Component: (await import('./pages/ConversationPage'))
                .ConversationPage,
            }),
          },
          {
            path: 'admin/users',
            lazy: async () => {
              const { AdminUsersPage } = await import('./pages/AdminUsersPage')
              return {
                element: (
                  <RequireAuth roles={['ADMIN']}>
                    <AdminUsersPage />
                  </RequireAuth>
                ),
              }
            },
          },
          {
            path: '*',
            lazy: async () => ({
              Component: (await import('./pages/NotFoundPage')).NotFoundPage,
            }),
          },
        ],
      },
    ],
  },
]
