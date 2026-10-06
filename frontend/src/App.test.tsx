import { render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import App from './App'

function stubFetch(response: Response | Promise<Response>) {
  const fetchMock = vi.fn().mockReturnValue(Promise.resolve(response))
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

describe('App', () => {
  it('shows the name and version that /api/v1/meta reports', async () => {
    const fetchMock = stubFetch(
      Response.json({ name: 'Test platform', version: '1.2.3' }),
    )

    render(<App />)

    expect(
      await screen.findByRole('heading', { name: 'Test platform' }),
    ).toBeInTheDocument()
    expect(screen.getByText('Version 1.2.3')).toBeInTheDocument()
    expect(document.title).toBe('Test platform')
    expect(fetchMock).toHaveBeenCalledWith('/api/v1/meta', expect.anything())
  })

  it('shows a loading state until the answer arrives', () => {
    stubFetch(new Promise<Response>(() => {}))

    render(<App />)

    expect(screen.getByText('Loading…')).toBeInTheDocument()
  })

  it('says so when the backend cannot be reached', async () => {
    stubFetch(new Response(null, { status: 503 }))

    render(<App />)

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'The server could not be reached',
    )
  })
})
