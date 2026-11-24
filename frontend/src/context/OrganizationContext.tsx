import React, { createContext, useContext, useState, useEffect, ReactNode } from 'react';
import { api } from '../services/api';
import { useAuth } from './AuthContext';

export interface OrganizationDto {
  id: number;
  name: string;
  slug: string;
  orgCode?: string;
  status: 'ACTIVE' | 'SUSPENDED';
  userRole?: string;
  currentUserRole?: string;
  createdAt?: string;
  updatedAt?: string;
}

interface OrganizationContextType {
  organizations: OrganizationDto[];
  currentOrg: OrganizationDto | null;
  loading: boolean;
  error: string | null;
  switchOrganization: (org: OrganizationDto) => void;
  refreshOrganizations: () => Promise<void>;
  createOrganization: (name: string, slug: string) => Promise<OrganizationDto>;
}

const OrganizationContext = createContext<OrganizationContextType | undefined>(undefined);

export function OrganizationProvider({ children }: { children: ReactNode }) {
  const { user } = useAuth();
  const [organizations, setOrganizations] = useState<OrganizationDto[]>([]);
  const [currentOrg, setCurrentOrg] = useState<OrganizationDto | null>(() => {
    try {
      const savedOrgId = typeof localStorage !== 'undefined' ? localStorage.getItem('prs_current_org_id') : null;
      const targetId = savedOrgId ? Number(savedOrgId) : (user?.organizationId ?? null);
      if (targetId && user?.organizationName) {
        return {
          id: targetId,
          name: user.organizationName,
          slug: user.organizationSlug || 'workspace',
          orgCode: user.organizationCode || 'SYN',
          status: 'ACTIVE',
          userRole: user.userRole || user.role,
        };
      }
    } catch (e) {}
    return null;
  });
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);

  const fetchOrganizations = async () => {
    if (!user) {
      setOrganizations([]);
      setCurrentOrg(null);
      setError(null);
      setLoading(false);
      return;
    }

    try {
      setLoading(true);
      setError(null);
      const res = await api.get<OrganizationDto[]>('/organizations');
      const orgs = res.data || [];
      setOrganizations(orgs);

      const savedOrgId = localStorage.getItem('prs_current_org_id');
      let target: OrganizationDto | undefined;

      if (savedOrgId) {
        target = orgs.find((o) => o.id === Number(savedOrgId));
      }

      if (!target && user.organizationId) {
        target = orgs.find((o) => o.id === user.organizationId);
      }

      if (!target && orgs.length > 0) {
        target = orgs.find((o) => o.status === 'ACTIVE') || orgs[0];
      }

      if (target) {
        setCurrentOrg(target);
        localStorage.setItem('prs_current_org_id', target.id.toString());
      } else {
        setCurrentOrg(null);
        localStorage.removeItem('prs_current_org_id');
      }
    } catch (err: any) {
      console.error('Failed to fetch organizations:', err);
      setError(err?.response?.data?.message || 'Failed to load authorized workspaces');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchOrganizations();
  }, [user?.username]);

  const switchOrganization = (org: OrganizationDto) => {
    setCurrentOrg(org);
    localStorage.setItem('prs_current_org_id', org.id.toString());
    // Trigger window event or dispatch so components can refresh
    window.dispatchEvent(new CustomEvent('prs_org_changed', { detail: org }));
  };

  const createOrganization = async (name: string, slug: string): Promise<OrganizationDto> => {
    const res = await api.post<OrganizationDto>('/organizations', { name, slug });
    const newOrg = res.data;
    await fetchOrganizations();
    switchOrganization(newOrg);
    return newOrg;
  };

  return (
    <OrganizationContext.Provider
      value={{
        organizations,
        currentOrg,
        loading,
        error,
        switchOrganization,
        refreshOrganizations: fetchOrganizations,
        createOrganization,
      }}
    >
      {children}
    </OrganizationContext.Provider>
  );
}

export function useOrganization() {
  const context = useContext(OrganizationContext);
  if (!context) {
    throw new Error('useOrganization must be used within an OrganizationProvider');
  }
  return context;
}
