import {
  keepPreviousData,
  useMutation,
  useQuery,
  useQueryClient,
} from '@tanstack/react-query'
import { useDeferredValue, useState } from 'react'
import { Input, Label, SearchField } from 'react-aria-components'
import { api } from '../api/client'
import type {
  AdminUserPage,
  AdminUserResponse,
  UpdateUserRequest,
} from '../api/types'
import { Alert } from '../components/Alert'
import { Button } from '../components/Button'
import { EnabledSwitch } from '../components/EnabledSwitch'
import { messageOf } from '../components/formErrors'
import { RoleSelect } from '../components/RoleSelect'

const PAGE_SIZE = 20

function listPath(search: string, page: number): string {
  const params = new URLSearchParams({
    page: String(page),
    size: String(PAGE_SIZE),
    sort: 'email',
  })
  if (search) params.set('query', search)
  return `/api/v1/admin/users?${params}`
}

function formatTime(value?: string): string {
  return value
    ? new Date(value).toLocaleString(undefined, {
        dateStyle: 'medium',
        timeStyle: 'short',
      })
    : 'Never'
}

/**
 * User administration (14.1): search, see roles and change a role or the enabled flag. The server
 * refuses changes that would leave no enabled admin (AUTH-2); its message is shown as it is.
 */
export function AdminUsersPage() {
  const queryClient = useQueryClient()
  const [search, setSearch] = useState('')
  const [page, setPage] = useState(0)
  const [notice, setNotice] = useState<{
    tone: 'success' | 'error'
    text: string
  } | null>(null)
  const query = useDeferredValue(search.trim())

  const users = useQuery({
    queryKey: ['admin-users', query, page],
    queryFn: ({ signal }) =>
      api<AdminUserPage>(listPath(query, page), { signal }),
    placeholderData: keepPreviousData,
  })

  const update = useMutation({
    mutationFn: ({ id, change }: { id: string; change: UpdateUserRequest }) =>
      api<AdminUserResponse>(`/api/v1/admin/users/${id}`, {
        method: 'PATCH',
        body: change,
      }),
    onSuccess: (user) => {
      setNotice({
        tone: 'success',
        text: `${user.displayName} is now ${user.role} and ${user.enabled ? 'enabled' : 'disabled'}.`,
      })
      return queryClient.invalidateQueries({ queryKey: ['admin-users'] })
    },
    onError: (error) =>
      setNotice({
        tone: 'error',
        text: messageOf(error, 'The change was not saved. Try again.'),
      }),
  })

  const change = (user: AdminUserResponse, patch: UpdateUserRequest) => {
    setNotice(null)
    update.mutate({ id: user.id, change: patch })
  }

  return (
    <section className="space-y-6">
      <h1 className="text-2xl font-semibold">Users</h1>

      <SearchField
        value={search}
        onChange={(value) => {
          setSearch(value)
          setPage(0)
        }}
        className="flex max-w-sm flex-col gap-1"
      >
        <Label className="text-sm font-medium">Search by name or email</Label>
        <Input className="rounded-md border border-slate-400 bg-white px-3 py-2 outline-none data-[focused]:outline-2 data-[focused]:outline-indigo-600 dark:border-slate-500 dark:bg-slate-900" />
      </SearchField>

      {notice && <Alert tone={notice.tone}>{notice.text}</Alert>}

      {users.isPending && <p role="status">Loading users…</p>}
      {users.isError && (
        <div className="space-y-2">
          <Alert>
            {messageOf(users.error, 'The users could not be loaded.')}
          </Alert>
          <Button variant="secondary" onPress={() => void users.refetch()}>
            Try again
          </Button>
        </div>
      )}
      {users.data && users.data.items.length === 0 && (
        <p>No users match this search.</p>
      )}
      {users.data && users.data.items.length > 0 && (
        <>
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm">
              <caption className="sr-only">
                Users, page {users.data.page + 1} of {users.data.totalPages}
              </caption>
              <thead className="border-b border-slate-300 dark:border-slate-700">
                <tr>
                  <th scope="col" className="py-2 pr-4">
                    Name
                  </th>
                  <th scope="col" className="py-2 pr-4">
                    Email
                  </th>
                  <th scope="col" className="py-2 pr-4">
                    Role
                  </th>
                  <th scope="col" className="py-2 pr-4">
                    Account
                  </th>
                  <th scope="col" className="py-2">
                    Last sign-in
                  </th>
                </tr>
              </thead>
              <tbody>
                {users.data.items.map((user) => (
                  <tr
                    key={user.id}
                    className="border-b border-slate-200 dark:border-slate-800"
                  >
                    <td className="py-2 pr-4">{user.displayName}</td>
                    <td className="py-2 pr-4">{user.email}</td>
                    <td className="py-2 pr-4">
                      <RoleSelect
                        label={`Role of ${user.displayName}`}
                        value={user.role}
                        onChange={(role) => change(user, { role })}
                        isDisabled={update.isPending}
                      />
                    </td>
                    <td className="py-2 pr-4">
                      <EnabledSwitch
                        label={`Account of ${user.displayName} enabled`}
                        isEnabled={user.enabled}
                        onChange={(enabled) => change(user, { enabled })}
                        isDisabled={update.isPending}
                      />
                    </td>
                    <td className="py-2">{formatTime(user.lastLoginAt)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <nav aria-label="Pages" className="flex items-center gap-3 text-sm">
            <Button
              variant="secondary"
              isDisabled={page === 0}
              onPress={() => setPage((p) => p - 1)}
            >
              Previous
            </Button>
            <span>
              Page {users.data.page + 1} of {Math.max(users.data.totalPages, 1)}
              , {users.data.totalItems} users
            </span>
            <Button
              variant="secondary"
              isDisabled={page + 1 >= users.data.totalPages}
              onPress={() => setPage((p) => p + 1)}
            >
              Next
            </Button>
          </nav>
        </>
      )}
    </section>
  )
}
