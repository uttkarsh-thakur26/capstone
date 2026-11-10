import type { SVGProps } from 'react'

/** Line icons drawn for this app; decorative only, so hidden from screen readers. */
function Icon({ children, ...props }: SVGProps<SVGSVGElement>) {
  return (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={1.75}
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
      focusable="false"
      className="h-5 w-5 shrink-0"
      {...props}
    >
      {children}
    </svg>
  )
}

export const HomeIcon = () => (
  <Icon>
    <path d="M3 10.5 12 3l9 7.5" />
    <path d="M5 9v11h5v-6h4v6h5V9" />
  </Icon>
)

export const DocumentIcon = () => (
  <Icon>
    <path d="M14 3H6a1 1 0 0 0-1 1v16a1 1 0 0 0 1 1h12a1 1 0 0 0 1-1V8z" />
    <path d="M14 3v5h5M8.5 13h7M8.5 17h5" />
  </Icon>
)

export const SearchIcon = () => (
  <Icon>
    <circle cx="11" cy="11" r="6.5" />
    <path d="m20 20-4.4-4.4" />
  </Icon>
)

export const CompareIcon = () => (
  <Icon>
    <path d="M4 5h6v14H4zM14 5h6v14h-6z" />
    <path d="M6.5 9h1M6.5 12h1M16.5 9h1M16.5 12h1M16.5 15h1" />
  </Icon>
)

export const AssistantIcon = () => (
  <Icon>
    <path d="M4 5.5A1.5 1.5 0 0 1 5.5 4h13A1.5 1.5 0 0 1 20 5.5v9a1.5 1.5 0 0 1-1.5 1.5H10l-4.5 4v-4h0A1.5 1.5 0 0 1 4 14.5z" />
    <path d="M8.5 9h7M8.5 12h4.5" />
  </Icon>
)

export const ConversationsIcon = () => (
  <Icon>
    <path d="M4 6h16M4 12h16M4 18h10" />
  </Icon>
)

export const UsersIcon = () => (
  <Icon>
    <circle cx="9" cy="8" r="3.5" />
    <path d="M2.5 20a6.5 6.5 0 0 1 13 0" />
    <path d="M16 4.6a3.5 3.5 0 0 1 0 6.8M18 14.3a6.5 6.5 0 0 1 3.5 5.7" />
  </Icon>
)

export const UploadIcon = () => (
  <Icon>
    <path d="M12 16V4M7 9l5-5 5 5" />
    <path d="M4 16v3a1 1 0 0 0 1 1h14a1 1 0 0 0 1-1v-3" />
  </Icon>
)

export const MenuIcon = () => (
  <Icon>
    <path d="M4 7h16M4 12h16M4 17h16" />
  </Icon>
)

export const CloseIcon = () => (
  <Icon>
    <path d="M6 6l12 12M18 6 6 18" />
  </Icon>
)

export const ArrowUpIcon = () => (
  <Icon strokeWidth={2.25}>
    <path d="M12 19V5M6 11l6-6 6 6" />
  </Icon>
)

export const ArrowDownIcon = () => (
  <Icon>
    <path d="M12 5v14M6 13l6 6 6-6" />
  </Icon>
)

export const NoAnswerIcon = () => (
  <Icon>
    <circle cx="12" cy="12" r="8.5" />
    <path d="M8.5 15.5l7-7" />
  </Icon>
)

export const WarningIcon = () => (
  <Icon>
    <path d="M12 4 2.8 19.5h18.4z" />
    <path d="M12 10v4.5M12 17.2v.3" />
  </Icon>
)

export const AuditIcon = () => (
  <Icon>
    <path d="M8 4h11v16H5V7z" />
    <path d="M8 4v3H5" />
    <path d="M9 12h6M9 16h4" />
  </Icon>
)

export const ChartIcon = () => (
  <Icon>
    <path d="M4 20h16" />
    <path d="M7 16v-4M12 16V8M17 16v-7" />
  </Icon>
)

export const SidebarIcon = () => (
  <Icon>
    <rect x="3.5" y="4.5" width="17" height="15" rx="2" />
    <path d="M9.5 4.5v15" />
  </Icon>
)
