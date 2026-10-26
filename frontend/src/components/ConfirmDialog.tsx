import type { ReactNode } from 'react'
import { Dialog, Heading, Modal, ModalOverlay } from 'react-aria-components'
import { Button } from './Button'

/**
 * A modal question with a cancel and a confirm button. React Aria keeps focus inside it, closes it
 * with Escape and returns focus to the button that opened it (UI-8).
 */
export function ConfirmDialog({
  isOpen,
  onOpenChange,
  title,
  children,
  confirmLabel,
  onConfirm,
  isPending,
}: {
  isOpen: boolean
  onOpenChange: (isOpen: boolean) => void
  title: string
  children: ReactNode
  confirmLabel: string
  onConfirm: () => void
  isPending?: boolean
}) {
  return (
    <ModalOverlay
      isOpen={isOpen}
      onOpenChange={onOpenChange}
      isDismissable={!isPending}
      className="fixed inset-0 z-10 flex items-center justify-center bg-slate-900/50 p-4"
    >
      <Modal className="w-full max-w-md rounded-lg bg-white p-6 shadow-xl dark:bg-slate-900">
        <Dialog role="alertdialog" className="space-y-4 outline-none">
          <Heading slot="title" className="text-lg font-semibold">
            {title}
          </Heading>
          <div className="text-sm">{children}</div>
          <div className="flex justify-end gap-3">
            <Button
              variant="secondary"
              onPress={() => onOpenChange(false)}
              isDisabled={isPending}
            >
              Cancel
            </Button>
            <Button variant="danger" onPress={onConfirm} isDisabled={isPending}>
              {confirmLabel}
            </Button>
          </div>
        </Dialog>
      </Modal>
    </ModalOverlay>
  )
}
