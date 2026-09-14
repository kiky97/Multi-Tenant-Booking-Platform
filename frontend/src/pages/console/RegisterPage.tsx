import { zodResolver } from '@hookform/resolvers/zod';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { Link } from 'react-router-dom';
import { z } from 'zod';
import { registerCustomer, registerProvider } from '../../features/auth/api';
import './console.css';

const schema = z.object({
  email: z.string().email(),
  password: z.string().min(8, 'Password must be at least 8 characters'),
  displayName: z.string().min(1, 'Name is required'),
  phoneNumber: z.string().optional(),
});

type FormValues = z.infer<typeof schema>;

type AccountType = 'PROVIDER' | 'CUSTOMER';

export default function RegisterPage() {
  const [accountType, setAccountType] = useState<AccountType>('PROVIDER');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [result, setResult] = useState<string | null>(null);
  const { register, handleSubmit, reset, formState: { errors } } = useForm<FormValues>({ resolver: zodResolver(schema) });

  const onSubmit = async (values: FormValues) => {
    setError(null);
    setSubmitting(true);
    try {
      if (accountType === 'PROVIDER') {
        const account = await registerProvider(values);
        setResult(`Business account created for ${account.email}. Log in to create an organization.`);
      } else {
        const customer = await registerCustomer(values);
        setResult(`Customer account created. Customer ID: ${customer.id} — give this to staff when booking.`);
      }
      reset();
    } catch {
      setError('Registration failed. The email may already be in use.');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="console">
      <main className="console-main">
      <h2>Register</h2>
      <div className="console-auth-toggle">
        <button type="button" className={accountType === 'PROVIDER' ? 'active' : ''} onClick={() => setAccountType('PROVIDER')}>
          Business (Provider)
        </button>
        <button type="button" className={accountType === 'CUSTOMER' ? 'active' : ''} onClick={() => setAccountType('CUSTOMER')}>
          Customer
        </button>
      </div>
      <form className="console-form" onSubmit={handleSubmit(onSubmit)} noValidate>
        <label>
          Email
          <input type="email" {...register('email')} />
        </label>
        <label>
          Password
          <input type="password" {...register('password')} />
        </label>
        <label>
          Full name
          <input type="text" {...register('displayName')} />
        </label>
        {accountType === 'CUSTOMER' && (
          <label>
            Phone number (optional)
            <input type="tel" {...register('phoneNumber')} />
          </label>
        )}
        {Object.keys(errors).length > 0 && <p className="console-error">Please fix the highlighted fields.</p>}
        {error && <p className="console-error">{error}</p>}
        {result && <p className="console-success">{result}</p>}
        <button type="submit" disabled={submitting}>{submitting ? 'Registering…' : 'Register'}</button>
      </form>
      <p className="console-muted">
        Already have an account? <Link to="/console/login">Log in</Link>
      </p>
      </main>
    </div>
  );
}
