import { zodResolver } from '@hookform/resolvers/zod'
import { useForm } from 'react-hook-form'
import { Link, Navigate, useNavigate } from 'react-router'
import { z } from 'zod'
import { ApiError } from '../api/client'
import { useAuth } from '../auth/useAuth'
import { Alert } from '../components/Alert'
import { Button } from '../components/Button'
import { messageOf, showServerErrors } from '../components/formErrors'
import { FormTextField } from '../components/FormTextField'
import { AuthLayout } from '../layout/AuthLayout'
import { LINK } from '../components/ui'

/** The server's rules (AUTH-4), checked here first only to answer faster. */
const schema = z.object({
  email: z
    .string()
    .trim()
    .min(1, 'Enter your email.')
    .pipe(z.email('Enter a valid email address.')),
  displayName: z
    .string()
    .trim()
    .min(1, 'Enter your name.')
    .max(100, 'Use at most 100 characters.'),
  // Characters, not UTF-16 units: an emoji counts once, as on the server.
  password: z.string().refine((value) => {
    const length = [...value].length
    return length >= 10 && length <= 128
  }, 'Use 10 to 128 characters.'),
})
type RegisterForm = z.infer<typeof schema>
const FIELDS = ['email', 'displayName', 'password'] as const

/** Self-registration always creates a student account (AUTH-1). */
export function RegisterPage() {
  const { state, register } = useAuth()
  const navigate = useNavigate()
  const form = useForm<RegisterForm>({
    resolver: zodResolver(schema),
    defaultValues: { email: '', displayName: '', password: '' },
  })
  const serverError = form.formState.errors.root?.server?.message

  if (state.status === 'authenticated') {
    return <Navigate to="/" replace />
  }

  const onSubmit = form.handleSubmit(async (values) => {
    try {
      const account = await register(values)
      navigate('/login', { state: { registeredEmail: account.email } })
    } catch (error) {
      const code = error instanceof ApiError ? error.code : undefined
      if (code === 'EMAIL_TAKEN' || code === 'EMAIL_DOMAIN_NOT_ALLOWED') {
        form.setError('email', {
          type: 'server',
          message: messageOf(error, 'This email cannot be used.'),
        })
      } else if (!showServerErrors(error, FIELDS, form.setError)) {
        form.setError('root.server', {
          message: messageOf(error, 'Registration failed. Try again.'),
        })
      }
    }
  })

  return (
    <AuthLayout title="Create an account">
      {serverError && <Alert>{serverError}</Alert>}
      <form onSubmit={onSubmit} noValidate className="space-y-4">
        <FormTextField
          control={form.control}
          name="email"
          label="Email"
          type="email"
          autoComplete="email"
        />
        <FormTextField
          control={form.control}
          name="displayName"
          label="Name"
          autoComplete="name"
        />
        <FormTextField
          control={form.control}
          name="password"
          label="Password"
          type="password"
          autoComplete="new-password"
          description="10 to 128 characters. A passphrase of several words works well."
        />
        <Button
          type="submit"
          className="w-full"
          isDisabled={form.formState.isSubmitting}
        >
          Create account
        </Button>
      </form>
      <p className="text-center text-sm">
        Already registered?{' '}
        <Link className={LINK} to="/login">
          Sign in
        </Link>
      </p>
    </AuthLayout>
  )
}
