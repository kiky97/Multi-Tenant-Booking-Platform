import { zodResolver } from '@hookform/resolvers/zod';
import { useForm } from 'react-hook-form';
import { Link } from 'react-router-dom';
import { z } from 'zod';
import { useAvailability, useCreateAvailability } from '../../features/availability/useAvailability';
import { selectedOrganization } from '../../features/organizations/organizationStorage';
import { useCreateService, useServices } from '../../features/services/useServices';
import { useCreateStaff, useStaff } from '../../features/staff/useStaff';
import type { DayOfWeek } from '../../types/booking';

const serviceSchema = z.object({
  name: z.string().min(1),
  durationMinutes: z.number().int().positive(),
  price: z.number().min(0),
});
type ServiceFormValues = z.infer<typeof serviceSchema>;

const staffSchema = z.object({
  name: z.string().min(1),
  email: z.string().email().optional().or(z.literal('')),
});
type StaffFormValues = z.infer<typeof staffSchema>;

const availabilitySchema = z.object({
  staffId: z.string().uuid(),
  dayOfWeek: z.string().min(1),
  startTime: z.string().min(1),
  endTime: z.string().min(1),
});
type AvailabilityFormValues = z.infer<typeof availabilitySchema>;

const DAYS: DayOfWeek[] = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY'];

function ServiceSection({ organizationId }: { organizationId: string }) {
  const { data: services = [] } = useServices(organizationId);
  const createService = useCreateService(organizationId);
  const { register, handleSubmit, reset, formState: { errors } } = useForm<ServiceFormValues>({ resolver: zodResolver(serviceSchema) });

  const onSubmit = async (values: ServiceFormValues) => {
    await createService.mutateAsync(values);
    reset();
  };

  return (
    <section className="console-step">
      <h3>Services</h3>
      <ul className="console-list">
        {services.map((service) => (
          <li key={service.id}>
            <span>{service.name} — {service.durationMinutes} min — {service.price}</span>
          </li>
        ))}
        {services.length === 0 && <li className="console-muted">No services yet.</li>}
      </ul>
      <form className="console-form" onSubmit={handleSubmit(onSubmit)} noValidate>
        <label>Name<input type="text" {...register('name')} /></label>
        <label>Duration (minutes)<input type="number" {...register('durationMinutes', { valueAsNumber: true })} /></label>
        <label>Price<input type="number" step="0.01" {...register('price', { valueAsNumber: true })} /></label>
        {Object.keys(errors).length > 0 && <p className="console-error">Fill in name, duration, and price.</p>}
        <button type="submit" disabled={createService.isPending}>Add service</button>
      </form>
    </section>
  );
}

function StaffSection({ organizationId }: { organizationId: string }) {
  const { data: staff = [] } = useStaff(organizationId);
  const createStaff = useCreateStaff(organizationId);
  const { register, handleSubmit, reset, formState: { errors } } = useForm<StaffFormValues>({ resolver: zodResolver(staffSchema) });

  const onSubmit = async (values: StaffFormValues) => {
    await createStaff.mutateAsync({ name: values.name, email: values.email || undefined });
    reset();
  };

  return (
    <section className="console-step">
      <h3>Staff</h3>
      <ul className="console-list">
        {staff.map((member) => (
          <li key={member.id}><span>{member.name} {member.email ? `— ${member.email}` : ''}</span></li>
        ))}
        {staff.length === 0 && <li className="console-muted">No staff yet.</li>}
      </ul>
      <form className="console-form" onSubmit={handleSubmit(onSubmit)} noValidate>
        <label>Name<input type="text" {...register('name')} /></label>
        <label>Email (optional)<input type="email" {...register('email')} /></label>
        {Object.keys(errors).length > 0 && <p className="console-error">Enter a name and, if provided, a valid email.</p>}
        <button type="submit" disabled={createStaff.isPending}>Add staff</button>
      </form>
    </section>
  );
}

function AvailabilitySection({ organizationId }: { organizationId: string }) {
  const { data: staff = [] } = useStaff(organizationId);
  const { data: availability = [] } = useAvailability(organizationId);
  const createAvailability = useCreateAvailability(organizationId);
  const { register, handleSubmit, reset, formState: { errors } } = useForm<AvailabilityFormValues>({ resolver: zodResolver(availabilitySchema) });

  const onSubmit = async (values: AvailabilityFormValues) => {
    await createAvailability.mutateAsync(values as AvailabilityFormValues & { dayOfWeek: DayOfWeek });
    reset();
  };

  const staffName = (staffId: string) => staff.find((member) => member.id === staffId)?.name ?? staffId;

  return (
    <section className="console-step">
      <h3>Availability</h3>
      <ul className="console-list">
        {availability.map((slot) => (
          <li key={slot.id}>
            <span>{staffName(slot.staffId)} — {slot.dayOfWeek} {slot.startTime}–{slot.endTime}</span>
          </li>
        ))}
        {availability.length === 0 && <li className="console-muted">No availability yet.</li>}
      </ul>
      {staff.length === 0 ? (
        <p className="console-muted">Add a staff member first.</p>
      ) : (
        <form className="console-form" onSubmit={handleSubmit(onSubmit)} noValidate>
          <label>
            Staff
            <select {...register('staffId')}>
              {staff.map((member) => <option key={member.id} value={member.id}>{member.name}</option>)}
            </select>
          </label>
          <label>
            Day of week
            <select {...register('dayOfWeek')}>
              {DAYS.map((day) => <option key={day} value={day}>{day}</option>)}
            </select>
          </label>
          <label>Start time<input type="time" {...register('startTime')} /></label>
          <label>End time<input type="time" {...register('endTime')} /></label>
          {Object.keys(errors).length > 0 && <p className="console-error">Choose a staff member, day, and time range.</p>}
          <button type="submit" disabled={createAvailability.isPending}>Add availability</button>
        </form>
      )}
    </section>
  );
}

export default function SetupPage() {
  const organizationId = selectedOrganization();
  if (!organizationId) {
    return <p>Select an <Link to="/console/organizations">organization</Link> first.</p>;
  }

  return (
    <div>
      <h2>Setup</h2>
      <ServiceSection organizationId={organizationId} />
      <StaffSection organizationId={organizationId} />
      <AvailabilitySection organizationId={organizationId} />
    </div>
  );
}
