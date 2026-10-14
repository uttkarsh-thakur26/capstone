import { createContext } from 'react'
import type { RegistrationRequest, UserResponse } from '../api/types'

export type AuthState =
  | { status: 'loading' }
  /** `signedOut` is set when the user signed out on purpose, as opposed to a session that ended. */
  | { status: 'anonymous'; signedOut?: boolean }
  | { status: 'authenticated'; user: UserResponse }

export interface Auth {
  state: AuthState
  login(email: string, password: string): Promise<UserResponse>
  register(request: RegistrationRequest): Promise<UserResponse>
  logout(): Promise<void>
}

export const AuthContext = createContext<Auth | null>(null)
