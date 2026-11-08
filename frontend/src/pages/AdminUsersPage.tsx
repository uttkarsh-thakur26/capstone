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
import { EnabledSwitch } from '../components/EnabledSwitch'
import { Pager } from '../components/Pager'
import { EmptyState, ErrorState, LoadingState } from '../components/states'
import { PageHeader } from '../components/PageHeader'
import { messageOf } from '../components/formErrors'
import { RoleSelect } from '../components/RoleSelect'
import { CARD, FIELD, LABEL, TABLE_HEAD, TABLE_ROW } from '../components/ui'
import { formatDateTime } from '../documents/format'

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
  return value ? formatDateTime(value) : 'Never'
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
      <PageHeader
        title="Users"
        description="Everyone who has an account. Registration always creates a student; change the role here."
      />

      <SearchField
        value={search}
        onChange={(value) => {
          setSearch(value)
          setPage(0)
        }}
        className="flex max-w-sm flex-col gap-1"
      >
        <Label className={LABEL}>Search by name or email</Label>
        <Input className={FIELD} />
      </SearchField>

      {notice && <Alert tone={notice.tone}>{notice.text}</Alert>}

      {users.isPending && <LoadingState>Loading users…</LoadingState>}
      {users.isError && (
        <ErrorState onRetry={() => void users.refetch()}>
          {messageOf(users.error, 'The users could not be loaded.')}
        </ErrorState>
      )}
      {users.data && users.data.items.length === 0 && (
        <EmptyState>No users match this search.</EmptyState>
      )}
      {users.data && users.data.items.length > 0 && (
        <>
          <div className={`${CARD} overflow-x-auto`}>
            <table className="w-full text-left text-sm">
              <caption className="sr-only">
                Users, page {users.data.page + 1} of {users.data.totalPages}
              </caption>
              <thead className={TABLE_HEAD}>
                <tr>
                  <th scope="col" className="px-4 py-3">
                    Name
                  </th>
                  <th scope="col" className="px-4 py-3">
                    Role
                  </th>
                  <th scope="col" className="px-4 py-3">
                    Account
                  </th>
                  <th scope="col" className="hidden px-4 py-3 md:table-cell">
                    Last sign-in
                  </th>
                </tr>
              </thead>
              <tbody>
                {users.data.items.map((user) => (
                  <tr key={user.id} className={TABLE_ROW}>
                    <td className="px-4 py-3">
                      <span className="block font-medium">
                        {user.displayName}
                      </span>
                      <span className="block text-xs text-muted wrap-anywhere">
                        {user.email}
                      </span>
                    </td>
                    <td className="px-4 py-3">
                      <RoleSelect
                        label={`Role of ${user.displayName}`}
                        value={user.role}
                        onChange={(role) => change(user, { role })}
                        isDisabled={update.isPending}
                      />
                    </td>
                    <td className="px-4 py-3">
                      <EnabledSwitch
                        label={`Account of ${user.displayName} enabled`}
                        isEnabled={user.enabled}
                        onChange={(enabled) => change(user, { enabled })}
                        isDisabled={update.isPending}
                      />
                    </td>
                    <td className="hidden px-4 py-3 whitespace-nowrap text-muted md:table-cell">
                      {formatTime(user.lastLoginAt)}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <Pager
            page={page}
            totalPages={users.data.totalPages}
            onPage={setPage}
          >
            Page {users.data.page + 1} of {Math.max(users.data.totalPages, 1)},{' '}
            {users.data.totalItems} users
          </Pager>
        </>
      )}
    </section>
  )
}
