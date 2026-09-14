import { zodResolver } from '@hookform/resolvers/zod';
import { useForm } from 'react-hook-form';
import { z } from 'zod';

const bookingSchema = z.object({
  customerId: z.string().uuid(),
  staffId: z.string().uuid(),
  serviceId: z.string().uuid(),
  startTime: z.string().datetime(),
});

export type BookingFormValues = z.infer<typeof bookingSchema>;

export function BookingForm({ onSubmit }: { onSubmit: (values: BookingFormValues) => void }) {
  const { register, handleSubmit, formState: { errors } } = useForm<BookingFormValues>({ resolver: zodResolver(bookingSchema) });
  return <form onSubmit={handleSubmit(onSubmit)} noValidate>
    <input aria-label="Customer ID" {...register('customerId')} />
    <input aria-label="Staff ID" {...register('staffId')} />
    <input aria-label="Service ID" {...register('serviceId')} />
    <input aria-label="Start time" type="datetime-local" {...register('startTime')} />
    {Object.keys(errors).length > 0 && <p role="alert">Please provide valid booking details.</p>}
    <button type="submit">Book</button>
  </form>;
}
