import { useQueryClient } from '@tanstack/react-query'
import {
  useCallback,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from 'react'
import {
  api,
  onSessionEnded,
  refreshSession,
  setAccessToken,
} from '../api/client'
import type {
  RegistrationRequest,
  SessionResponse,
  UserResponse,
} from '../api/types'
import { AuthContext, type Auth, type AuthState } from './authContext'

/**
 * Who is signed in. On start the session is restored from the refresh cookie, so a reload keeps
 * the user signed in without storing a token in the browser (AUTH-6, UI-1). The role shown here
 * only shapes the navigation; the server decides every access (UI-2).
 */
export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient()
  const [state, setState] = useState<AuthState>({ status: 'loading' })

  const signedOut = useCallback(() => {
    setAccessToken(null)
    // Nothing fetched for one user may be shown to the next (UI-1).
    queryClient.clear()
    setState({ status: 'anonymous' })
  }, [queryClient])

  useEffect(() => {
    onSessionEnded(signedOut)
    let active = true
    refreshSession().then(
      (session) => {
        if (!active) return
        setState(
          session
            ? { status: 'authenticated', user: session.user }
            : { status: 'anonymous' },
        )
      },
      () => {
        if (active) setState({ status: 'anonymous' })
      },
    )
    return () => {
      active = false
      onSessionEnded(null)
    }
  }, [signedOut])

  const login = useCallback(async (email: string, password: string) => {
    const session = await api<SessionResponse>('/api/v1/auth/login', {
      method: 'POST',
      body: { email, password },
    })
    setAccessToken(session.accessToken)
    setState({ status: 'authenticated', user: session.user })
    return session.user
  }, [])

  const register = useCallback(
    (request: RegistrationRequest) =>
      api<UserResponse>('/api/v1/auth/register', {
        method: 'POST',
        body: request,
      }),
    [],
  )

  const logout = useCallback(async () => {
    try {
      await api<void>('/api/v1/auth/logout', { method: 'POST' })
    } finally {
      // Signed out locally even if the server could not be reached.
      signedOut()
    }
  }, [signedOut])

  const auth = useMemo<Auth>(
    () => ({ state, login, register, logout }),
    [state, login, register, logout],
  )
  return <AuthContext.Provider value={auth}>{children}</AuthContext.Provider>
}
