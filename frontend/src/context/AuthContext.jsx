// TICKET-ADV112 / TICKET-ADV127 — AuthContext with memoised provider value
import React, { createContext, useContext, useState, useCallback, useMemo } from 'react';

export const AuthContext = createContext({ user: null, login: () => {}, logout: () => {} });

export function AuthProvider({ children }) {
  const [user, setUser] = useState(() => {
    const token = typeof sessionStorage !== 'undefined' ? sessionStorage.getItem('reconx-token') : null;
    if (!token) return null;
    return { token, role: sessionStorage.getItem('reconx-role') };
  });

  const login = useCallback((token, role) => {
    sessionStorage.setItem('reconx-token', token);
    sessionStorage.setItem('reconx-role', role);
    setUser({ token, role });
  }, []);

  const logout = useCallback(() => {
    sessionStorage.removeItem('reconx-token');
    sessionStorage.removeItem('reconx-role');
    setUser(null);
  }, []);

  // TICKET-ADV127: Memoise context value object to prevent unnecessary re-renders in consumers
  const value = useMemo(() => ({ user, login, logout }), [user, login, logout]);

  return (
    <AuthContext.Provider value={value}>
      {children}
    </AuthContext.Provider>
  );
}

export const useAuth = () => useContext(AuthContext);
