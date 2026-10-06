import { useEffect, useState } from 'react'
import { fetchMeta, type Meta } from './api/meta'

type MetaState =
  { status: 'loading' } | { status: 'ready'; meta: Meta } | { status: 'error' }

function App() {
  const [state, setState] = useState<MetaState>({ status: 'loading' })

  useEffect(() => {
    const controller = new AbortController()
    fetchMeta(controller.signal)
      .then((meta) => {
        document.title = meta.name
        setState({ status: 'ready', meta })
      })
      .catch(() => {
        if (!controller.signal.aborted) setState({ status: 'error' })
      })
    return () => controller.abort()
  }, [])

  return (
    <main className="shell">
      {state.status === 'loading' && <p aria-busy="true">Loading…</p>}
      {state.status === 'error' && (
        <p role="alert">The server could not be reached. Try again later.</p>
      )}
      {state.status === 'ready' && (
        <>
          <h1>{state.meta.name}</h1>
          <p className="version">Version {state.meta.version}</p>
        </>
      )}
    </main>
  )
}

export default App
