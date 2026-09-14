import { zodResolver } from '@hookform/resolvers/zod';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { Link, useNavigate } from 'react-router-dom';
import { z } from 'zod';
import { login } from '../../features/auth/api';
import { saveSession } from '../../features/auth/session';
import './console.css';

const schema = z.object({
  email: z.string().email(),
  password: z.string().min(1),
});

type FormValues = z.infer<typeof schema>;

export default function LoginPage() {
  const navigate = useNavigate();
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const { register, handleSubmit, formState: { errors } } = useForm<FormValues>({ resolver: zodResolver(schema) });

  const onSubmit = async (values: FormValues) => {
    setError(null);
    setSubmitting(true);
    try {
      const response = await login(values);
      saveSession(response);
      navigate('/console/organizations');
    } catch {
      setError('Login failed. Check your email and password.');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="console">
      <main className="console-main">
      <h2>Log in</h2>
      <form className="console-form" onSubmit={handleSubmit(onSubmit)} noValidate>
        <label>
          Email
          <input type="email" {...register('email')} />
        </label>
        <label>
          Password
          <input type="password" {...register('password')} />
        </label>
        {(errors.email || errors.password) && <p className="console-error">Enter a valid email and password.</p>}
        {error && <p className="console-error">{error}</p>}
        <button type="submit" disabled={submitting}>{submitting ? 'Logging in…' : 'Log in'}</button>
      </form>
      <p className="console-muted">
        No account yet? <Link to="/console/register">Register</Link>
      </p>
      </main>
    </div>
  );
}
