import type { ReactNode } from 'react'
import { CARD } from '../components/ui'
import { ProductName, ProductVersion } from './ProductName'

/** The frame of the sign-in and registration pages: the product, then the form on a card. */
export function AuthLayout({
  title,
  children,
}: {
  title: string
  children: ReactNode
}) {
  return (
    <div className="flex min-h-screen flex-col items-center justify-center px-4 py-10">
      <main className="w-full max-w-sm space-y-8">
        <div className="space-y-2 text-center">
          <p className="text-3xl font-semibold tracking-tight">
            <ProductName />
          </p>
          <p className="text-sm text-muted">
            Answers with proof from your documents
          </p>
        </div>
        <div className={`${CARD} space-y-6 p-6 shadow-sm sm:p-8`}>
          <h1 className="text-xl font-semibold">{title}</h1>
          {children}
        </div>
      </main>
      <footer className="mt-8 text-xs text-muted">
        <ProductVersion />
      </footer>
    </div>
  )
}
