import { Link } from 'react-router'

/** Shown when a signed-in user opens a page their role does not allow (16.2). */
export function NotAllowedPage() {
  return (
    <section className="mx-auto max-w-xl space-y-3 py-10">
      <h1 className="text-2xl font-semibold">Not allowed</h1>
      <p>Your role does not give you access to this page.</p>
      <p>
        <Link className="underline underline-offset-2" to="/">
          Go to the home page
        </Link>
      </p>
    </section>
  )
}
