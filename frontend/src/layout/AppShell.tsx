import type { ReactNode } from 'react'
import {
  Button as AriaButton,
  Dialog,
  DialogTrigger,
  Modal,
  ModalOverlay,
} from 'react-aria-components'
import { Link, NavLink, Outlet, useMatches, useNavigation } from 'react-router'
import type { UserResponse } from '../api/types'
import { useAuth } from '../auth/useAuth'
import { Button, ButtonLink } from '../components/Button'
import {
  AssistantIcon,
  CloseIcon,
  ConversationsIcon,
  DocumentIcon,
  HomeIcon,
  MenuIcon,
  SearchIcon,
  CompareIcon,
  UploadIcon,
  UsersIcon,
} from '../components/icons'
import { FOCUS_RING, FOCUS_RING_NATIVE } from '../components/ui'
import { ProductName, ProductVersion } from './ProductName'
import { RoleBadge } from './RoleBadge'
import { ThemeSwitch } from './ThemeSwitch'

const linkClass = ({ isActive }: { isActive: boolean }) =>
  `flex items-center gap-3 rounded-md px-3 py-2 text-sm font-medium ${FOCUS_RING_NATIVE} ${
    isActive
      ? 'bg-accent-soft text-link'
      : 'text-muted hover:bg-subtle hover:text-fg'
  }`

/**
 * The frame of every signed-in page: a sidebar from large screens up, a top bar with a menu
 * drawer below that. The navigation shows only what the user's role may use, for convenience;
 * the server enforces every rule (UI-2).
 */
export function AppShell() {
  const { state } = useAuth()
  const navigation = useNavigation()
  // A chat page keeps its question box at the bottom of the window: no footer under it.
  const chat = useMatches().some(
    (match) => (match.handle as { chat?: boolean } | undefined)?.chat,
  )
  if (state.status !== 'authenticated') return null
  const { user } = state

  return (
    <div className="min-h-screen">
      {navigation.state === 'loading' && (
        // The next page's code is on its way (lazy routes): a thin bar at the top says so.
        <div
          role="progressbar"
          aria-label="Opening the page"
          className="fixed inset-x-0 top-0 z-50 h-0.5 animate-pulse bg-accent"
        />
      )}
      <a
        href="#main"
        className="sr-only z-40 focus:not-sr-only focus:fixed focus:top-2 focus:left-2 focus:rounded-md focus:bg-surface focus:px-3 focus:py-2 focus:shadow"
      >
        Skip to content
      </a>

      <aside className="fixed inset-y-0 left-0 hidden w-64 border-r border-line bg-surface lg:flex">
        <Sidebar user={user} />
      </aside>

      <header className="sticky top-0 z-20 flex h-14 items-center justify-between gap-3 border-b border-line bg-surface px-4 lg:hidden">
        <Link
          to="/"
          className={`truncate rounded-sm text-base font-semibold ${FOCUS_RING_NATIVE}`}
        >
          <ProductName />
        </Link>
        <MenuDrawer user={user} />
      </header>

      <div className="flex min-h-screen flex-col lg:pl-64">
        <main
          id="main"
          tabIndex={-1}
          className={`mx-auto w-full max-w-6xl flex-1 px-4 outline-none sm:px-6 lg:px-10 ${chat ? 'pt-6 lg:pt-8' : 'py-6 lg:py-10'}`}
        >
          <Outlet />
        </main>
        {!chat && (
          <footer className="px-4 py-4 text-center text-xs text-muted sm:px-6 lg:px-10">
            <ProductVersion />
          </footer>
        )}
      </div>
    </div>
  )
}

/**
 * Below the large breakpoint the sidebar opens as a modal drawer: focus stays inside it, Escape
 * closes it and focus returns to the menu button (UI-8).
 */
function MenuDrawer({ user }: { user: UserResponse }) {
  return (
    <DialogTrigger>
      <AriaButton
        aria-label="Open menu"
        className={`-mr-2 rounded-md p-2 text-fg data-[hovered]:bg-subtle ${FOCUS_RING}`}
      >
        <MenuIcon />
      </AriaButton>
      <ModalOverlay
        isDismissable
        className="fixed inset-0 z-30 bg-slate-950/50 lg:hidden"
      >
        <Modal className="fixed inset-y-0 left-0 flex w-72 max-w-[85vw] border-r border-line bg-surface shadow-xl">
          <Dialog aria-label="Menu" className="flex w-full outline-none">
            {({ close }) => (
              <Sidebar
                user={user}
                onNavigate={close}
                closeButton={
                  <AriaButton
                    aria-label="Close menu"
                    onPress={close}
                    className={`-mr-1 rounded-md p-1.5 text-muted data-[hovered]:bg-subtle data-[hovered]:text-fg ${FOCUS_RING}`}
                  >
                    <CloseIcon />
                  </AriaButton>
                }
              />
            )}
          </Dialog>
        </Modal>
      </ModalOverlay>
    </DialogTrigger>
  )
}

function Sidebar({
  user,
  onNavigate,
  closeButton,
}: {
  user: UserResponse
  onNavigate?: () => void
  closeButton?: ReactNode
}) {
  const { logout } = useAuth()
  const canUpload = user.role !== 'STUDENT'
  const item = (to: string, icon: ReactNode, label: string, end = false) => (
    <NavLink to={to} end={end} className={linkClass} onClick={onNavigate}>
      {icon}
      {label}
    </NavLink>
  )

  return (
    <div className="flex w-full flex-col overflow-y-auto px-3 py-4">
      <div className="flex items-center justify-between gap-2 px-3 pb-5">
        <Link
          to="/"
          onClick={onNavigate}
          className={`rounded-sm text-lg font-semibold tracking-tight ${FOCUS_RING_NATIVE}`}
        >
          <ProductName />
        </Link>
        {closeButton}
      </div>

      {canUpload && (
        <ButtonLink
          to="/documents/upload"
          onClick={onNavigate}
          className="mx-1 mb-5"
        >
          <UploadIcon />
          Upload
        </ButtonLink>
      )}

      <nav aria-label="Main" className="flex flex-col gap-1">
        {item('/', <HomeIcon />, 'Home', true)}
        {item('/documents', <DocumentIcon />, 'Documents')}
        {item('/search', <SearchIcon />, 'Search')}
        {item('/compare', <CompareIcon />, 'Compare')}
        {item('/assistant', <AssistantIcon />, 'Assistant')}
        {item('/conversations', <ConversationsIcon />, 'Conversations')}
        {user.role === 'ADMIN' && (
          <>
            <p className="mt-5 mb-1 px-3 text-xs font-medium tracking-wide text-muted uppercase">
              Administration
            </p>
            {item('/admin/users', <UsersIcon />, 'Users')}
          </>
        )}
      </nav>

      <div className="mt-auto space-y-4 border-t border-line px-1 pt-4">
        <div className="min-w-0 space-y-1 px-2">
          <p className="flex items-center gap-2 text-sm font-medium">
            <span className="truncate">{user.displayName}</span>
            <RoleBadge role={user.role} />
          </p>
          <p className="truncate text-xs text-muted">{user.email}</p>
        </div>
        <div className="px-2">
          <ThemeSwitch />
        </div>
        <Button
          variant="secondary"
          size="sm"
          className="w-full"
          onPress={() => void logout()}
        >
          Sign out
        </Button>
      </div>
    </div>
  )
}
