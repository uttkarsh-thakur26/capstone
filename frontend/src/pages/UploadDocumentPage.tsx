import { zodResolver } from '@hookform/resolvers/zod'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import {
  DropZone,
  FileTrigger,
  Label,
  ProgressBar,
  Text,
} from 'react-aria-components'
import { Link, useNavigate } from 'react-router'
import { api, ApiError, upload } from '../api/client'
import type {
  DocumentResponse,
  ProblemResponse,
  UploadDocumentRequest,
  UploadLimits,
} from '../api/types'
import { Alert } from '../components/Alert'
import { Button } from '../components/Button'
import { messageOf, showServerErrors } from '../components/formErrors'
import { FormTextField } from '../components/FormTextField'
import { PageHeader } from '../components/PageHeader'
import { LINK } from '../components/ui'
import { formatBytes } from '../documents/format'
import {
  CATEGORY_OPTIONS,
  EARLIEST_DATE,
  EMPTY_METADATA,
  latestDate,
  METADATA_FIELDS,
  metadataSchema,
  normalizeTitle,
  TITLE_HINT,
  toMetadata,
  type MetadataForm,
} from '../documents/metadata'
import { FormSelect } from '../components/FormSelect'
import { GrantsEditor } from '../documents/GrantsEditor'
import { NOT_SHARED, toGrantRequests, type Sharing } from '../documents/sharing'

/** A title from a file name: without ".pdf", with underscores read as spaces. */
function titleFrom(filename: string): string {
  return normalizeTitle(filename.replace(/\.pdf$/i, '').replace(/_+/g, ' '))
}

/** Checked again by the server from the content (DOC-1); this only saves a pointless upload. */
function problemWith(file: File, limits: UploadLimits | undefined) {
  const isPdf =
    file.type === 'application/pdf' || file.name.toLowerCase().endsWith('.pdf')
  if (!isPdf) return 'Choose a PDF file.'
  if (file.size === 0) return 'The file is empty.'
  if (limits && file.size > limits.maxFileSizeBytes) {
    return `The file is ${formatBytes(file.size)}; the limit is ${formatBytes(limits.maxFileSizeBytes)}.`
  }
  return null
}

/**
 * Upload for faculty and admins (DOC-1, ACL-3): a PDF, its description and who may read it. With
 * nothing shared, only the uploader and admins can read it (ACL-5). Shows the upload's progress
 * (UI-4). The new document is queued as UPLOADED, and its detail page follows the processing (DOC-3).
 */
export function UploadDocumentPage() {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [file, setFile] = useState<File | null>(null)
  const [fileError, setFileError] = useState<string | null>(null)
  const [sharing, setSharing] = useState<Sharing>(NOT_SHARED)
  const [progress, setProgress] = useState<number | null>(null)
  const [duplicateId, setDuplicateId] = useState<string | null>(null)
  const [abort, setAbort] = useState<AbortController | null>(null)

  const limits = useQuery({
    queryKey: ['upload-limits'],
    queryFn: ({ signal }) =>
      api<UploadLimits>('/api/v1/documents/upload-limits', { signal }),
    staleTime: Infinity,
  })

  const form = useForm<MetadataForm>({
    resolver: zodResolver(metadataSchema),
    defaultValues: EMPTY_METADATA,
    // Errors show once a field is left, then follow every change; what was typed stays.
    mode: 'onTouched',
  })
  const serverError = form.formState.errors.root?.server?.message
  const uploading = progress !== null
  // Upload stays off until there is a file and every field passes the rules (UI-4).
  const canUpload = file !== null && form.formState.isValid

  const choose = (chosen: File) => {
    const problem = problemWith(chosen, limits.data)
    setFileError(problem)
    setDuplicateId(null)
    if (problem) {
      setFile(null)
      return
    }
    setFile(chosen)
    if (!form.getValues('title').trim()) {
      form.setValue('title', titleFrom(chosen.name), {
        shouldValidate: true,
        shouldTouch: true,
      })
    }
  }

  const onSubmit = form.handleSubmit(async (values) => {
    if (!file) {
      setFileError('Choose a PDF file.')
      return
    }
    const metadata: UploadDocumentRequest = {
      ...toMetadata(values),
      grants: toGrantRequests(sharing),
    }
    const body = new FormData()
    body.append('file', file)
    body.append(
      'metadata',
      new Blob([JSON.stringify(metadata)], { type: 'application/json' }),
    )
    const controller = new AbortController()
    setAbort(controller)
    setDuplicateId(null)
    try {
      const created = await upload<DocumentResponse>(
        '/api/v1/documents',
        body,
        setProgress,
        controller.signal,
      )
      await queryClient.invalidateQueries({ queryKey: ['documents'] })
      navigate(`/documents/${created.id}`, {
        // The detail page polls the status until processing ends (UI-4).
        state: { notice: 'The document was uploaded.' },
      })
    } catch (error) {
      setProgress(null)
      if (error instanceof DOMException && error.name === 'AbortError') {
        form.setError('root.server', { message: 'The upload was cancelled.' })
        return
      }
      if (error instanceof ApiError && error.code === 'DUPLICATE_DOCUMENT') {
        const problem = error.problem as ProblemResponse & {
          documentId?: string
        }
        setDuplicateId(problem.documentId ?? null)
      }
      if (!showServerErrors(error, METADATA_FIELDS, form.setError)) {
        form.setError('root.server', {
          message: messageOf(error, 'The upload failed. Try again.'),
        })
      }
    }
  })

  return (
    <section className="max-w-2xl space-y-6">
      <PageHeader
        title="Upload a document"
        above={
          <Link to="/documents" className={LINK}>
            Documents
          </Link>
        }
        description={
          <>
            PDF only
            {limits.data &&
              `, up to ${formatBytes(limits.data.maxFileSizeBytes)}`}
            . The text must be selectable: scanned pages without a text layer
            cannot be searched.
          </>
        }
      />

      {serverError && (
        <Alert>
          {serverError}
          {duplicateId && (
            <>
              {' '}
              <Link
                className="font-medium underline underline-offset-2"
                to={`/documents/${duplicateId}`}
              >
                Open the existing document
              </Link>
            </>
          )}
        </Alert>
      )}

      <form onSubmit={onSubmit} noValidate className="space-y-6">
        <div className="space-y-2">
          <DropZone
            onDrop={async (event) => {
              const item = event.items.find((i) => i.kind === 'file')
              if (item?.kind === 'file') choose(await item.getFile())
            }}
            isDisabled={uploading}
            className="flex flex-col items-center gap-3 rounded-lg border-2 border-dashed border-line-strong bg-surface p-6 text-center outline-none data-[drop-target]:border-accent data-[drop-target]:bg-accent-soft data-[focus-visible]:outline-2 data-[focus-visible]:outline-focus"
          >
            <Text slot="label" className="max-w-full text-sm wrap-anywhere">
              {file ? (
                <>
                  <span className="font-medium">{file.name}</span> (
                  {formatBytes(file.size)})
                </>
              ) : (
                'Drop a PDF here, or'
              )}
            </Text>
            <FileTrigger
              acceptedFileTypes={['application/pdf', '.pdf']}
              onSelect={(files) => {
                const chosen = files?.[0]
                if (chosen) choose(chosen)
              }}
            >
              <Button variant="secondary" isDisabled={uploading}>
                {file ? 'Choose another file' : 'Choose a file'}
              </Button>
            </FileTrigger>
          </DropZone>
          {fileError && (
            <p role="alert" className="text-sm text-red-700 dark:text-red-400">
              {fileError}
            </p>
          )}
        </div>

        <FormTextField
          control={form.control}
          name="title"
          label="Title"
          description={TITLE_HINT}
          isDisabled={uploading}
        />
        <FormTextField
          control={form.control}
          name="description"
          label="Description (optional)"
          multiline
          isDisabled={uploading}
        />
        <div className="grid gap-4 sm:grid-cols-2">
          <FormSelect
            control={form.control}
            name="category"
            label="Category (optional)"
            options={CATEGORY_OPTIONS}
            emptyLabel="No category"
            isDisabled={uploading}
          />
          <FormTextField
            control={form.control}
            name="documentDate"
            label="Effective date (optional)"
            type="date"
            min={EARLIEST_DATE}
            max={latestDate()}
            description="When the content takes effect, up to a year ahead."
            isDisabled={uploading}
          />
        </div>

        <fieldset className="space-y-3 rounded-lg border border-line bg-surface p-4">
          <legend className="px-1 font-medium">Share with</legend>
          <p className="text-sm text-muted">
            Leave everything empty to keep the document to yourself and the
            admins. You can change this later.
          </p>
          <GrantsEditor
            value={sharing}
            onChange={setSharing}
            isDisabled={uploading}
          />
        </fieldset>

        {uploading && (
          <ProgressBar
            value={Math.round((progress ?? 0) * 100)}
            className="flex flex-col gap-1"
          >
            {({ percentage, valueText }) => (
              <>
                <div className="flex justify-between text-sm">
                  <Label>Uploading</Label>
                  <span>{valueText}</span>
                </div>
                <div className="h-2 overflow-hidden rounded-full bg-subtle">
                  <div
                    className="h-full bg-accent transition-[width]"
                    style={{ width: `${percentage ?? 0}%` }}
                  />
                </div>
              </>
            )}
          </ProgressBar>
        )}

        <div className="flex gap-3">
          <Button type="submit" isDisabled={uploading || !canUpload}>
            Upload
          </Button>
          {uploading ? (
            <Button variant="secondary" onPress={() => abort?.abort()}>
              Cancel upload
            </Button>
          ) : (
            <Link
              to="/documents"
              className="inline-flex items-center rounded-md px-4 py-2 text-sm font-medium underline-offset-2 hover:underline"
            >
              Back to documents
            </Link>
          )}
        </div>
      </form>
    </section>
  )
}
