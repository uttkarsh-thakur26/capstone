import { useQuery } from '@tanstack/react-query'
import { useEffect } from 'react'
import { fetchMeta } from '../api/meta'

/** The product name and version from `GET /api/v1/meta` (ADR-007), also used as the page title. */
export function useMeta() {
  const meta = useQuery({
    queryKey: ['meta'],
    queryFn: ({ signal }) => fetchMeta(signal),
    staleTime: Infinity,
  })
  const name = meta.data?.name
  useEffect(() => {
    if (name) document.title = name
  }, [name])
  return meta
}
