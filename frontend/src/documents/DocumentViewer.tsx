import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect } from 'react'
import { apiBlob } from '../api/client'
import { Alert } from '../components/Alert'
import { Button } from '../components/Button'
import { messageOf } from '../components/formErrors'

/**
 * The address of the file now on screen. Only one viewer is shown at a time, so the previous
 * file's memory is released as soon as the next one is opened, or when no viewer is left.
 */
let shownUrl: string | null = null
let mountedViewers = 0

function showFile(blob: Blob): string {
  if (shownUrl) URL.revokeObjectURL(shownUrl)
  shownUrl = URL.createObjectURL(blob)
  return shownUrl
}

/**
 * The document's PDF in the browser's own viewer, opened at a page (16.2, ADR-035). The file is
 * fetched with the access token, which a plain link cannot carry (ADR-031), and the server checks
 * access on this request too (ACL-7). `#page=` is understood by the PDF viewers of Chrome, Edge
 * and Firefox; where a browser shows no PDF inside a page, the download still works.
 */
export function DocumentViewer({
  documentId,
  title,
  page,
}: {
  documentId: string
  title: string
  page: number
}) {
  const queryClient = useQueryClient()
  useEffect(() => {
    mountedViewers += 1
    return () => {
      mountedViewers -= 1
      // Deferred, so the double mount of React's development mode does not release a file that
      // is still on screen. The cached address goes with the file, so a later visit fetches again.
      setTimeout(() => {
        if (mountedViewers > 0) return
        queryClient.removeQueries({ queryKey: ['document-file'] })
        if (shownUrl) URL.revokeObjectURL(shownUrl)
        shownUrl = null
      })
    }
  }, [queryClient])

  const file = useQuery({
    queryKey: ['document-file', documentId],
    queryFn: async ({ signal }) =>
      showFile(
        await apiBlob(`/api/v1/documents/${documentId}/content`, signal),
      ),
    // The file can be large: it is not kept for later visits.
    gcTime: 0,
    staleTime: Infinity,
    retry: false,
  })

  if (file.isPending) {
    return <p role="status">Opening the document…</p>
  }
  if (file.isError) {
    return (
      <div className="space-y-2">
        <Alert>
          {messageOf(file.error, 'The document could not be opened.')}
        </Alert>
        <Button variant="secondary" onPress={() => void file.refetch()}>
          Try again
        </Button>
      </div>
    )
  }
  const src = `${file.data}#page=${page}`
  return (
    <iframe
      key={src}
      src={src}
      title={`${title}, page ${page}`}
      className="h-[80vh] w-full rounded-md border border-slate-300 bg-white dark:border-slate-700"
    />
  )
}
