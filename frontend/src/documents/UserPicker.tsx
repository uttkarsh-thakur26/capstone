import { useQuery } from '@tanstack/react-query'
import { useDeferredValue, useState } from 'react'
import {
  ComboBox,
  Input,
  Label,
  ListBox,
  ListBoxItem,
  Popover,
  Text,
} from 'react-aria-components'
import { api } from '../api/client'
import type { UserSummary, UserSummaryPage } from '../api/types'
import { FIELD } from '../components/ui'

/** The server needs at least this many characters (DOC-8). */
const MIN_QUERY = 3

/**
 * Finds a person to share a document with, by part of their name or email (DOC-8). The server
 * returns enabled users only, and only their id, name and email.
 */
export function UserPicker({
  excludeIds,
  onPick,
  isDisabled,
}: {
  excludeIds: readonly string[]
  onPick: (user: UserSummary) => void
  isDisabled?: boolean
}) {
  const [text, setText] = useState('')
  const typed = [...text.trim()].length >= MIN_QUERY
  const query = useDeferredValue(text.trim())
  const longEnough = [...query].length >= MIN_QUERY

  const results = useQuery({
    queryKey: ['user-search', query],
    queryFn: ({ signal }) =>
      api<UserSummaryPage>(
        `/api/v1/users/search?${new URLSearchParams({ query, size: '10' })}`,
        { signal },
      ),
    enabled: longEnough,
    staleTime: 30_000,
  })
  const people = longEnough
    ? (results.data?.items ?? []).filter(
        (person) => !excludeIds.includes(person.id),
      )
    : []

  const emptyText = results.isError
    ? 'The search failed. Try again.'
    : results.isFetching || !longEnough
      ? 'Searching…'
      : 'No one matches.'

  return (
    <ComboBox
      items={people}
      inputValue={text}
      onInputChange={setText}
      selectedKey={null}
      onSelectionChange={(key) => {
        const person = people.find((p) => p.id === key)
        if (person) {
          onPick(person)
          setText('')
        }
      }}
      menuTrigger="input"
      // The list opens once a search can run, and closes when a person is picked and the field is
      // cleared.
      allowsEmptyCollection={typed}
      isDisabled={isDisabled}
      className="flex max-w-md flex-col gap-1"
    >
      <Label className="text-sm font-medium">Add a person</Label>
      <Input className={FIELD} />
      <Text slot="description" className="text-sm text-muted">
        Type at least {MIN_QUERY} characters of a name or email.
      </Text>
      <Popover className="w-(--trigger-width) rounded-md border border-line bg-surface shadow-lg">
        <ListBox
          className="max-h-64 overflow-auto p-1 outline-none"
          renderEmptyState={() => (
            <p className="px-3 py-2 text-sm text-muted">{emptyText}</p>
          )}
        >
          {(person: UserSummary) => (
            <ListBoxItem
              id={person.id}
              textValue={`${person.displayName} ${person.email}`}
              className="flex cursor-default flex-col rounded px-3 py-1.5 text-sm outline-none data-[focused]:bg-accent-soft"
            >
              <span>{person.displayName}</span>
              <span className="text-xs text-muted">{person.email}</span>
            </ListBoxItem>
          )}
        </ListBox>
      </Popover>
    </ComboBox>
  )
}
