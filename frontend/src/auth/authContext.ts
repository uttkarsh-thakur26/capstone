import { createContext } from 'react'
import type { RegistrationRequest, UserResponse } from '../api/types'

export type AuthState =
  | { status: 'loading' }
  | { status: 'anonymous' }
  | { status: 'authenticated'; user: UserResponse }

export interface Auth {
  state: AuthState
  login(email: string, password: string): Promise<UserResponse>
  register(request: RegistrationRequest): Promise<UserResponse>
  logout(): Promise<void>
}

export const AuthContext = createContext<Auth | null>(null)
