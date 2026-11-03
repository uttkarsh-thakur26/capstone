import type { ReactNode } from 'react'
import { Dialog, Heading, Modal, ModalOverlay } from 'react-aria-components'
import { Button } from './Button'
import { MODAL, MODAL_OVERLAY } from './ui'

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
      className={MODAL_OVERLAY}
    >
      <Modal className={MODAL}>
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
