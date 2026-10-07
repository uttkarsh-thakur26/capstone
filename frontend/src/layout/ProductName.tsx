import { useMeta } from '../app/useMeta'

/** The name the backend serves; there is no second copy in the bundle (ADR-007). */
export function ProductName() {
  const meta = useMeta()
  if (meta.isPending) return <span aria-busy="true">Loading…</span>
  if (meta.isError) return <span>Knowledge platform</span>
  return <>{meta.data.name}</>
}

export function ProductVersion() {
  const meta = useMeta()
  if (meta.isError) {
    return <p role="alert">The server could not be reached. Try again later.</p>
  }
  return meta.data ? <p>Version {meta.data.version}</p> : null
}
