import { useEffect, useRef, useState, type ReactNode } from 'react'
import { Button as AriaButton } from 'react-aria-components'
import { ArrowDownIcon } from '../components/icons'
import { FOCUS_RING } from '../components/ui'

/** How far from the bottom, in pixels, still counts as reading the latest message. */
const NEAR_BOTTOM_PX = 160

function distanceFromBottom(): number {
  const page = document.documentElement
  return page.scrollHeight - (window.scrollY + window.innerHeight)
}

function scrollBehavior(): ScrollBehavior {
  return window.matchMedia?.('(prefers-reduced-motion: reduce)').matches
    ? 'auto'
    : 'smooth'
}

/**
 * The frame of a chat: the reading column, and the question box fixed at the bottom of the window
 * while the messages scroll under it. A new message is scrolled into view, unless the reader has
 * scrolled up to read; then a "Jump to latest" button appears instead of moving the page. A
 * question just sent is always followed, wherever the reader had scrolled.
 */
export function ChatLayout({
  header,
  messageCount,
  composer,
  children,
}: {
  header: ReactNode
  /**
   * Grows whenever something is added: the number of messages, plus a half while a question is
   * pending, so a question sent and its answer each count.
   */
  messageCount: number
  composer: ReactNode
  children: ReactNode
}) {
  const end = useRef<HTMLDivElement>(null)
  const away = useRef(false)
  const latest = useRef(messageCount)
  const [readingEarlier, setReadingEarlier] = useState(false)
  const [seen, setSeen] = useState(messageCount)

  useEffect(() => {
    latest.current = messageCount
  }, [messageCount])

  useEffect(() => {
    let lastY = window.scrollY
    const onScroll = () => {
      // Only moving up means reading earlier messages: the page's own smooth scroll to a new
      // answer moves down, and passes through positions far from the bottom on its way.
      const movedUp = window.scrollY < lastY - 2
      lastY = window.scrollY
      if (distanceFromBottom() <= NEAR_BOTTOM_PX) {
        away.current = false
        setReadingEarlier(false)
        setSeen(latest.current)
      } else if (movedUp) {
        away.current = true
        setReadingEarlier(true)
      }
    }
    window.addEventListener('scroll', onScroll, { passive: true })
    return () => window.removeEventListener('scroll', onScroll)
  }, [])

  const first = useRef(true)
  useEffect(() => {
    if (first.current) {
      // Opening a conversation starts at its latest message.
      first.current = false
      end.current?.scrollIntoView?.({ block: 'end' })
      return
    }
    const justAsked = !Number.isInteger(messageCount)
    if (justAsked) away.current = false
    if (!away.current) {
      end.current?.scrollIntoView?.({
        behavior: scrollBehavior(),
        block: 'end',
      })
    }
  }, [messageCount])

  const jump = () => {
    away.current = false
    end.current?.scrollIntoView?.({ behavior: scrollBehavior(), block: 'end' })
    setReadingEarlier(false)
    setSeen(latest.current)
  }

  const showJump = readingEarlier && messageCount > seen

  return (
    <div className="mx-auto flex min-h-[calc(100dvh-5rem)] w-full max-w-[720px] flex-col lg:min-h-[calc(100dvh-2rem)]">
      {header}
      <div className="flex-1 pt-6 pb-8">{children}</div>
      <div className="sticky bottom-0 z-10 bg-gradient-to-t from-canvas from-80% to-transparent pt-4 pb-4">
        {showJump && (
          <div className="absolute -top-8 left-1/2 -translate-x-1/2">
            <AriaButton
              onPress={jump}
              className={`flex items-center gap-1.5 rounded-full border border-line bg-surface px-3 py-1.5 text-xs font-medium text-fg shadow-md data-[hovered]:bg-subtle ${FOCUS_RING}`}
            >
              <ArrowDownIcon />
              Jump to latest
            </AriaButton>
          </div>
        )}
        {composer}
      </div>
      {/* Below the question box, so scrolling here shows the last message above the box. */}
      <div ref={end} />
    </div>
  )
}
