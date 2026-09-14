export const selectOrganization = (organizationId: string): void => localStorage.setItem('organizationId', organizationId);
export const selectedOrganization = (): string | null => localStorage.getItem('organizationId');
