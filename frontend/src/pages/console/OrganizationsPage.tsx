import { zodResolver } from '@hookform/resolvers/zod';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { useNavigate } from 'react-router-dom';
import { z } from 'zod';
import { selectOrganization, selectedOrganization } from '../../features/organizations/organizationStorage';
import { useCreateOrganization, useOrganizations } from '../../features/organizations/useOrganizations';

const schema = z.object({
  name: z.string().min(1),
  timezone: z.string().min(1),
});

type FormValues = z.infer<typeof schema>;

export default function OrganizationsPage() {
  const navigate = useNavigate();
  const { data: organizations = [], isLoading, error: loadError } = useOrganizations();
  const createOrganization = useCreateOrganization();
  const [currentId, setCurrentId] = useState(selectedOrganization());
  const { register, handleSubmit, reset, formState: { errors } } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: { timezone: Intl.DateTimeFormat().resolvedOptions().timeZone },
  });

  const choose = (organizationId: string) => {
    selectOrganization(organizationId);
    setCurrentId(organizationId);
    navigate('/console/setup');
  };

  const onSubmit = async (values: FormValues) => {
    const organization = await createOrganization.mutateAsync(values);
    reset({ name: '', timezone: values.timezone });
    choose(organization.id);
  };

  return (
    <div>
      <h2>Organizations</h2>
      {isLoading && <p className="console-muted">Loading…</p>}
      {loadError && <p className="console-error">Only provider accounts with an organization membership can see this list.</p>}
      <ul className="console-list">
        {organizations.map((organization) => (
          <li key={organization.id}>
            <span>{organization.name} <span className="console-muted">({organization.timezone})</span></span>
            <button type="button" onClick={() => choose(organization.id)}>
              {currentId === organization.id ? 'Selected' : 'Select'}
            </button>
          </li>
        ))}
        {organizations.length === 0 && !isLoading && <li className="console-muted">No organizations yet.</li>}
      </ul>

      <h3>Create an organization</h3>
      <p className="console-muted">Requires a provider account (business login).</p>
      <form className="console-form" onSubmit={handleSubmit(onSubmit)} noValidate>
        <label>
          Name
          <input type="text" {...register('name')} />
        </label>
        <label>
          Timezone (IANA)
          <input type="text" placeholder="Europe/Zurich" {...register('timezone')} />
        </label>
        {Object.keys(errors).length > 0 && <p className="console-error">Name and timezone are required.</p>}
        {createOrganization.isError && <p className="console-error">Could not create the organization. Are you logged in as a provider?</p>}
        <button type="submit" disabled={createOrganization.isPending}>
          {createOrganization.isPending ? 'Creating…' : 'Create organization'}
        </button>
      </form>
    </div>
  );
}
