import {
  createContext,
  type ReactNode,
  useContext,
  useEffect,
  useMemo,
  useState,
} from 'react';

import { authApi, usersApi } from '@/api';
import type { AppUser, AuthConfig } from '@/types/api';

interface AuthContextValue {
  config: AuthConfig | null;
  currentUser: AppUser | null;
  users: AppUser[];
  loading: boolean;
  logout: () => Promise<void>;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [config, setConfig] = useState<AuthConfig | null>(null);
  const [currentUser, setCurrentUser] = useState<AppUser | null>(null);
  const [users, setUsers] = useState<AppUser[]>([]);
  const [loading, setLoading] = useState<boolean>(true);

  useEffect(() => {
    let active = true;
    const initialize = async (): Promise<void> => {
      try {
        const configRequest: Promise<AuthConfig> = authApi.getConfig();
        const userRequest: Promise<AppUser | null> = authApi
          .getCurrentUser()
          .catch(() => null);
        const [authConfig, user]: [AuthConfig, AppUser | null] =
          await Promise.all([configRequest, userRequest]);
        if (!active) return;
        setConfig(authConfig);
        setCurrentUser(user);

        if (user) {
          setLoading(false);
          try {
            const directory: AppUser[] = await usersApi.listUsers();
            if (active) setUsers(directory);
          } catch {
            if (active) setUsers([]);
          }
        } else {
          setUsers([]);
        }
      } finally {
        if (active) setLoading(false);
      }
    };

    const handleUnauthorized = (): void => {
      setCurrentUser(null);
      setUsers([]);
    };
    window.addEventListener('auth:unauthorized', handleUnauthorized);
    void initialize();
    return () => {
      active = false;
      window.removeEventListener('auth:unauthorized', handleUnauthorized);
    };
  }, []);

  const logout = async (): Promise<void> => {
    await authApi.logout();
    setCurrentUser(null);
    setUsers([]);
  };

  const value: AuthContextValue = useMemo(
    () => ({
      config,
      currentUser,
      users,
      loading,
      logout,
    }),
    [config, currentUser, users, loading],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const context: AuthContextValue | null = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within AuthProvider');
  }
  return context;
}
