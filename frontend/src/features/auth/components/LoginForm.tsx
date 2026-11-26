import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { useNavigate } from '@tanstack/react-router'
import { Loader2 } from 'lucide-react'
import { loginSchema, type LoginFormValues } from '@/features/auth/schemas/authSchemas'
import { useLogin } from '@/features/auth/hooks/useAuthMutations'
import { normalizeError } from '@/lib/errors'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { FormField } from '@/components/common/FormField'
import { PasswordField } from '@/features/auth/components/PasswordField'

export function LoginForm() {
  const navigate = useNavigate()
  const login = useLogin()

  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<LoginFormValues>({
    resolver: zodResolver(loginSchema),
    defaultValues: { officialEmail: '', password: '' },
  })

  async function onSubmit(values: LoginFormValues) {
    try {
      await login.mutateAsync(values)
    } catch (err) {
      const apiErr = normalizeError(err)
      // Map field errors to form fields
      for (const [field, messages] of Object.entries(apiErr.fieldErrors)) {
        const message = messages[0]
        if (field === 'officialEmail' || field === 'password') {
          setError(field, { message })
        }
      }
    }
  }

  const isPending = isSubmitting || login.isPending

  return (
    <form
      onSubmit={handleSubmit(onSubmit)}
      noValidate
      aria-label="Sign in form"
      className="space-y-5"
    >
      <FormField
        id="login-email"
        label="Official Email"
        error={errors.officialEmail?.message}
        required
      >
        <Input
          {...register('officialEmail')}
          id="login-email"
          type="email"
          autoComplete="username"
          placeholder="you@company.com"
          hasError={!!errors.officialEmail}
          disabled={isPending}
        />
      </FormField>

      <FormField id="login-password" label="Password" error={errors.password?.message} required>
        <PasswordField
          {...register('password')}
          id="login-password"
          autoComplete="current-password"
          placeholder="••••••••"
          hasError={!!errors.password}
          disabled={isPending}
        />
      </FormField>

      <div className="flex justify-end">
        <button
          type="button"
          onClick={() => void navigate({ to: '/forgot-password' })}
          className="rounded text-sm text-[--color-primary] hover:underline focus-visible:ring-2 focus-visible:ring-[--color-ring] focus-visible:outline-none"
        >
          Forgot password?
        </button>
      </div>

      <Button type="submit" className="w-full" size="lg" disabled={isPending}>
        {isPending ? (
          <>
            <Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" />
            Signing in…
          </>
        ) : (
          'Sign In'
        )}
      </Button>
    </form>
  )
}
