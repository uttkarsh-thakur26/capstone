import type { ReactNode } from 'react'
import { ProductName, ProductVersion } from './ProductName'

/** The frame of the sign-in and registration pages. */
export function AuthLayout({
  title,
  children,
}: {
  title: string
  children: ReactNode
}) {
  return (
    <div className="flex min-h-screen flex-col items-center justify-center px-4 py-10">
      <main className="w-full max-w-sm space-y-6">
        <header className="space-y-1 text-center">
          <p className="text-sm font-medium text-slate-600 dark:text-slate-300">
            <ProductName />
          </p>
          <h1 className="text-2xl font-semibold">{title}</h1>
        </header>
        {children}
      </main>
      <footer className="mt-8 text-xs text-slate-600 dark:text-slate-300">
        <ProductVersion />
      </footer>
    </div>
  )
}
