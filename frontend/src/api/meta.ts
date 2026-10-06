import type { components } from './schema'

/** Name and version of the running backend. Generated from its OpenAPI document. */
export type Meta = components['schemas']['MetaResponse']

export async function fetchMeta(signal?: AbortSignal): Promise<Meta> {
  const response = await fetch('/api/v1/meta', {
    headers: { Accept: 'application/json' },
    signal,
  })
  if (!response.ok) {
    throw new Error(`GET /api/v1/meta answered ${response.status}`)
  }
  return (await response.json()) as Meta
}
