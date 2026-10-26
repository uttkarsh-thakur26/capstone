import { apiBlob } from '../api/client'

/**
 * Downloads a document's file (DOC-6). A plain link cannot carry the access token, which lives in
 * memory only (AUTH-6), so the file is fetched with it and then saved from the browser's memory
 * (ADR-031). The server still checks access on this request (ACL-7).
 */
export async function downloadDocument(
  id: string,
  filename: string,
): Promise<void> {
  const blob = await apiBlob(`/api/v1/documents/${id}/content`)
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  document.body.append(link)
  link.click()
  link.remove()
  // Some browsers start reading the file only after the click returns.
  setTimeout(() => URL.revokeObjectURL(url), 10_000)
}
