import { useContext } from 'react'
import { AuthContext, type Auth } from './authContext'

export function useAuth(): Auth {
  const auth = useContext(AuthContext)
  if (!auth) {
    throw new Error('useAuth needs an AuthProvider above it')
  }
  return auth
}
