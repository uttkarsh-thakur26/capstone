import { Link } from 'react-router'
import { LINK } from '../components/ui'

export function NotFoundPage() {
  return (
    <section className="mx-auto max-w-xl space-y-3 py-10">
      <h1 className="text-2xl font-semibold tracking-tight">Page not found</h1>
      <p>There is no page at this address.</p>
      <p>
        <Link className={LINK} to="/">
          Go to the home page
        </Link>
      </p>
    </section>
  )
}
