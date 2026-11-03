import { render, screen } from '@testing-library/react'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { describe, expect, it, vi } from 'vitest'
import { RouteError } from './RouteError'

describe('RouteError', () => {
  it('offers a reload when a page chunk fails to load', async () => {
    vi.spyOn(console, 'error').mockImplementation(() => {})
    const router = createMemoryRouter([
      {
        ErrorBoundary: RouteError,
        children: [
          {
            path: '/',
            lazy: () => Promise.reject(new Error('chunk failed')),
          },
        ],
      },
    ])
    render(<RouterProvider router={router} />)

    expect(
      await screen.findByText(/This page could not be opened/),
    ).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Try again' })).toBeEnabled()
  })
})
