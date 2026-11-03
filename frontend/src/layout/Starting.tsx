/** Shown while the session is checked or the first page's code loads, before anything else. */
export function Starting() {
  return (
    <p role="status" className="p-6 text-muted">
      Loading…
    </p>
  )
}
