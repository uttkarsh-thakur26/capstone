import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation } from '@tanstack/react-query'
import { useForm } from 'react-hook-form'
import { Dialog, Heading, Modal, ModalOverlay } from 'react-aria-components'
import { z } from 'zod'
import { api } from '../api/client'
import type { AdminUserResponse } from '../api/types'
import { Alert } from '../components/Alert'
import { Button } from '../components/Button'
import { messageOf, showServerErrors } from '../components/formErrors'
import { FormTextField } from '../components/FormTextField'
import { MODAL, MODAL_OVERLAY } from '../components/ui'

/** AUTH-4: 10 to 128 characters, counted as the server counts them, in code points. */
const length = (value: string) => [...value].length

const schema = z
  .object({
    newPassword: z
      .string()
      .refine((value) => length(value) >= 10, 'Use at least 10 characters.')
      .refine((value) => length(value) <= 128, 'Use at most 128 characters.'),
    repeat: z.string(),
  })
  .refine((values) => values.newPassword === values.repeat, {
    path: ['repeat'],
    message: 'The two passwords are not the same.',
  })

type PasswordForm = z.infer<typeof schema>

/**
 * An admin sets a new password for another account (14.1). The server ends every session of that
 * account, so whoever knew the old password is signed out; the admin then tells the person the new
 * one in person. The password is never shown again or stored anywhere but as its hash.
 */
export function SetPasswordDialog({
  user,
  onClose,
  onDone,
}: {
  user: AdminUserResponse | null
  onClose: () => void
  onDone: (user: AdminUserResponse) => void
}) {
  const form = useForm<PasswordForm>({
    resolver: zodResolver(schema),
    defaultValues: { newPassword: '', repeat: '' },
  })
  const save = useMutation({
    mutationFn: ({ id, newPassword }: { id: string; newPassword: string }) =>
      api(`/api/v1/admin/users/${id}/password`, {
        method: 'POST',
        body: { newPassword },
      }),
  })
  const serverError = form.formState.errors.root?.server?.message

  const close = () => {
    form.reset()
    save.reset()
    onClose()
  }
  const onSubmit = form.handleSubmit(async ({ newPassword }) => {
    if (!user) return
    try {
      await save.mutateAsync({ id: user.id, newPassword })
      form.reset()
      // The mutation keeps what it sent; forget the password once it is saved.
      save.reset()
      onDone(user)
    } catch (error) {
      if (!showServerErrors(error, ['newPassword'], form.setError)) {
        form.setError('root.server', {
          message: messageOf(error, 'The password was not set. Try again.'),
        })
      }
    }
  })

  return (
    <ModalOverlay
      isOpen={user !== null}
      onOpenChange={(open) => !open && close()}
      // While the password is being saved, neither a click outside nor Escape closes the dialog:
      // its result belongs to this user and this dialog.
      isDismissable={!save.isPending}
      isKeyboardDismissDisabled={save.isPending}
      className={MODAL_OVERLAY}
    >
      <Modal className={MODAL}>
        <Dialog className="space-y-4 outline-none">
          <Heading slot="title" className="text-lg font-semibold">
            Set a new password
          </Heading>
          <p className="text-sm text-muted">
            For {user?.displayName} ({user?.email}). Their sessions end at once,
            and they sign in with the new password, which you give them
            yourself.
          </p>
          {serverError && <Alert>{serverError}</Alert>}
          <form onSubmit={onSubmit} noValidate className="space-y-4">
            <FormTextField
              control={form.control}
              name="newPassword"
              label="New password"
              type="password"
              autoComplete="new-password"
              description="10 to 128 characters."
              isDisabled={save.isPending}
            />
            <FormTextField
              control={form.control}
              name="repeat"
              label="New password again"
              type="password"
              autoComplete="new-password"
              isDisabled={save.isPending}
            />
            <div className="flex justify-end gap-3">
              <Button
                variant="secondary"
                onPress={close}
                isDisabled={save.isPending}
              >
                Cancel
              </Button>
              <Button type="submit" isDisabled={save.isPending}>
                Set password
              </Button>
            </div>
          </form>
        </Dialog>
      </Modal>
    </ModalOverlay>
  )
}
