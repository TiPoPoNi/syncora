import React, { createContext, useContext, useState, ReactNode } from 'react';

export interface UserMembership {
  organizationId: number;
  organizationName: string;
  organizationSlug: string;
  organizationCode?: string;
  role: string;
  status: string;
  employeeId?: string;
}

export interface User {
  id?: number;
  username: string;
  role: string;
  token: string;
  email?: string;
  userRole?: string;
  organizationId?: number;
  organizationName?: string;
  organizationSlug?: string;
  organizationCode?: string;
  employeeId?: string;
  memberships?: UserMembership[];
}

interface AuthContextType {
  user: User | null;
  token: string | null;
  isAuthenticated: boolean;
  login: (userData: User) => void;
  logout: () => void;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(() => {
    const saved = localStorage.getItem('prs_user');
    return saved ? JSON.parse(saved) : null;
  });

  const login = (userData: User) => {
    setUser(userData);
    localStorage.setItem('prs_user', JSON.stringify(userData));
    if (userData.organizationId) {
      localStorage.setItem('prs_current_org_id', userData.organizationId.toString());
    }
  };

  const logout = () => {
    setUser(null);
    localStorage.removeItem('prs_user');
    localStorage.removeItem('prs_current_org_id');
  };

  return (
    <AuthContext.Provider
      value={{
        user,
        token: user?.token || null,
        isAuthenticated: !!user,
        login,
        logout,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (context === undefined) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
}
