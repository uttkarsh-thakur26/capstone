import { Link } from 'react-router'

export function NotFoundPage() {
  return (
    <section className="mx-auto max-w-xl space-y-3 py-10">
      <h1 className="text-2xl font-semibold">Page not found</h1>
      <p>There is no page at this address.</p>
      <p>
        <Link className="underline underline-offset-2" to="/">
          Go to the home page
        </Link>
      </p>
    </section>
  )
}
