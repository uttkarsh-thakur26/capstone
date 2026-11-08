import { zodResolver } from '@hookform/resolvers/zod'
import { useForm } from 'react-hook-form'
import { Dialog, Heading, Modal, ModalOverlay } from 'react-aria-components'
import { api } from '../api/client'
import type { DocumentResponse, UpdateDocumentRequest } from '../api/types'
import { Alert } from '../components/Alert'
import { Button } from '../components/Button'
import { messageOf, showServerErrors } from '../components/formErrors'
import { FormTextField } from '../components/FormTextField'
import {
  CATEGORY_OPTIONS,
  EARLIEST_DATE,
  latestDate,
  METADATA_FIELDS,
  metadataSchema,
  TITLE_HINT,
  toMetadata,
  type MetadataForm,
} from './metadata'
import { FormSelect } from '../components/FormSelect'
import { MODAL_OVERLAY } from '../components/ui'

/**
 * Edits title, description, category and effective date (DOC-7). The four are sent together, so a
 * field left empty is cleared (ADR-031).
 */
export function EditDocumentDialog({
  document,
  isOpen,
  onOpenChange,
  onSaved,
}: {
  document: DocumentResponse
  isOpen: boolean
  onOpenChange: (isOpen: boolean) => void
  onSaved: (document: DocumentResponse) => void
}) {
  const form = useForm<MetadataForm>({
    resolver: zodResolver(metadataSchema),
    // The same rules and timing as the upload form (ADR-040).
    mode: 'onTouched',
    values: {
      title: document.title,
      description: document.description ?? '',
      category: document.category ?? '',
      documentDate: document.documentDate ?? '',
    },
  })
  const serverError = form.formState.errors.root?.server?.message
  const saving = form.formState.isSubmitting

  const onSubmit = form.handleSubmit(async (values) => {
    try {
      const saved = await api<DocumentResponse>(
        `/api/v1/documents/${document.id}`,
        {
          method: 'PATCH',
          body: toMetadata(values) satisfies UpdateDocumentRequest,
        },
      )
      onSaved(saved)
      onOpenChange(false)
    } catch (error) {
      if (!showServerErrors(error, METADATA_FIELDS, form.setError)) {
        form.setError('root.server', {
          message: messageOf(error, 'The changes were not saved. Try again.'),
        })
      }
    }
  })

  return (
    <ModalOverlay
      isOpen={isOpen}
      onOpenChange={onOpenChange}
      isDismissable={!saving}
      className={MODAL_OVERLAY}
    >
      <Modal className="max-h-full w-full max-w-lg overflow-auto rounded-lg border border-line bg-surface p-6 text-fg shadow-xl">
        <Dialog className="space-y-4 outline-none">
          <Heading slot="title" className="text-lg font-semibold">
            Edit details
          </Heading>
          {serverError && <Alert>{serverError}</Alert>}
          <form onSubmit={onSubmit} noValidate className="space-y-4">
            <FormTextField
              control={form.control}
              name="title"
              label="Title"
              description={TITLE_HINT}
            />
            <FormTextField
              control={form.control}
              name="description"
              label="Description (optional)"
              multiline
            />
            <FormSelect
              control={form.control}
              name="category"
              label="Category (optional)"
              options={CATEGORY_OPTIONS}
              emptyLabel="No category"
            />
            <FormTextField
              control={form.control}
              name="documentDate"
              label="Effective date (optional)"
              type="date"
              min={EARLIEST_DATE}
              max={latestDate()}
            />
            <div className="flex justify-end gap-3">
              <Button
                variant="secondary"
                onPress={() => onOpenChange(false)}
                isDisabled={saving}
              >
                Cancel
              </Button>
              <Button
                type="submit"
                isDisabled={saving || !form.formState.isValid}
              >
                Save
              </Button>
            </div>
          </form>
        </Dialog>
      </Modal>
    </ModalOverlay>
  )
}
