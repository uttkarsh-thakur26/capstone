import type { RouteObject } from 'react-router'
import { RequireAuth } from './auth/RequireAuth'
import { AppShell } from './layout/AppShell'
import { AdminUsersPage } from './pages/AdminUsersPage'
import { DocumentDetailPage } from './pages/DocumentDetailPage'
import { DocumentsPage } from './pages/DocumentsPage'
import { HomePage } from './pages/HomePage'
import { LoginPage } from './pages/LoginPage'
import { NotFoundPage } from './pages/NotFoundPage'
import { RegisterPage } from './pages/RegisterPage'
import { SearchPage } from './pages/SearchPage'
import { UploadDocumentPage } from './pages/UploadDocumentPage'

/** The routes of 16.2 that exist so far. Everything except sign-in and registration needs a session. */
export const routes: RouteObject[] = [
  { path: '/login', element: <LoginPage /> },
  { path: '/register', element: <RegisterPage /> },
  {
    element: (
      <RequireAuth>
        <AppShell />
      </RequireAuth>
    ),
    children: [
      { index: true, element: <HomePage /> },
      { path: 'documents', element: <DocumentsPage /> },
      {
        path: 'documents/upload',
        element: (
          <RequireAuth roles={['ADMIN', 'FACULTY']}>
            <UploadDocumentPage />
          </RequireAuth>
        ),
      },
      { path: 'documents/:id', element: <DocumentDetailPage /> },
      { path: 'search', element: <SearchPage /> },
      {
        path: 'admin/users',
        element: (
          <RequireAuth roles={['ADMIN']}>
            <AdminUsersPage />
          </RequireAuth>
        ),
      },
      { path: '*', element: <NotFoundPage /> },
    ],
  },
]
