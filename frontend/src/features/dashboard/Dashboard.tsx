import { selectedOrganization } from '../organizations/organizationStorage';
import { useServices } from '../services/useServices';

export function Dashboard() {
  const organizationId = selectedOrganization();
  const { data: services = [], isLoading } = useServices(organizationId);
  if (!organizationId) return <p>Select an organization to view its dashboard.</p>;
  if (isLoading) return <p>Loading services…</p>;
  return <section><h2>Services</h2><p>{services.length} services in this organization.</p></section>;
}
