import { zodResolver } from '@hookform/resolvers/zod'
import { useForm } from 'react-hook-form'
import { Link, Navigate, useLocation, useNavigate } from 'react-router'
import { z } from 'zod'
import { useAuth } from '../auth/useAuth'
import { Alert } from '../components/Alert'
import { Button } from '../components/Button'
import { messageOf } from '../components/formErrors'
import { FormTextField } from '../components/FormTextField'
import { AuthLayout } from '../layout/AuthLayout'

const schema = z.object({
  email: z
    .string()
    .trim()
    .min(1, 'Enter your email.')
    .pipe(z.email('Enter a valid email address.')),
  password: z.string().min(1, 'Enter your password.'),
})
type LoginForm = z.infer<typeof schema>

interface LoginState {
  from?: { pathname: string; search?: string }
  registeredEmail?: string
}

export function LoginPage() {
  const { state, login } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const { from, registeredEmail } = (location.state ?? {}) as LoginState
  const target = from ? `${from.pathname}${from.search ?? ''}` : '/'

  const form = useForm<LoginForm>({
    resolver: zodResolver(schema),
    defaultValues: { email: registeredEmail ?? '', password: '' },
  })
  const serverError = form.formState.errors.root?.server?.message

  if (state.status === 'authenticated') {
    return <Navigate to={target} replace />
  }

  const onSubmit = form.handleSubmit(async ({ email, password }) => {
    try {
      await login(email, password)
      navigate(target, { replace: true })
    } catch (error) {
      // One message for every failed sign-in; the server does not say which part was wrong (AUTH-5).
      form.setError('root.server', {
        message: messageOf(error, 'Sign-in failed. Try again.'),
      })
    }
  })

  return (
    <AuthLayout title="Sign in">
      {registeredEmail && (
        <Alert tone="success">
          Your account is ready. Sign in with your email and password.
        </Alert>
      )}
      {serverError && <Alert>{serverError}</Alert>}
      <form onSubmit={onSubmit} noValidate className="space-y-4">
        <FormTextField
          control={form.control}
          name="email"
          label="Email"
          type="email"
          autoComplete="username"
        />
        <FormTextField
          control={form.control}
          name="password"
          label="Password"
          type="password"
          autoComplete="current-password"
        />
        <Button
          type="submit"
          className="w-full"
          isDisabled={form.formState.isSubmitting}
        >
          Sign in
        </Button>
      </form>
      <p className="text-center text-sm">
        No account yet?{' '}
        <Link className="underline underline-offset-2" to="/register">
          Register
        </Link>
      </p>
    </AuthLayout>
  )
}
