import { createContext, useCallback, useEffect, useMemo, useState } from 'react';
import toast from 'react-hot-toast';
import { setUnauthorizedHandler, tokenStorage } from '../api/client';
import { authApi, userApi } from '../api/endpoints';

/**
 * Who is logged in, available to every component via useAuth().
 *
 * Token storage: localStorage, so a page reload keeps you logged in. Trade-off: any
 * JavaScript on the page can read it, so XSS must be prevented (React escapes output by
 * default; we never use dangerouslySetInnerHTML). The stronger alternative is an httpOnly
 * refresh-token cookie, the planned upgrade.
 */
export const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null);
  const [initializing, setInitializing] = useState(Boolean(tokenStorage.get()));

  const logout = useCallback((message) => {
    tokenStorage.clear();
    setUser(null);
    if (message) toast.error(message);
  }, []);

  // Any 401 from the API (expired token, disabled account) logs the user out.
  useEffect(() => {
    setUnauthorizedHandler((message) => logout(message || 'Your session has expired. Please log in again.'));
  }, [logout]);

  // On page load: if a token is stored, ask the server who we are.
  useEffect(() => {
    if (!tokenStorage.get()) return;
    userApi
      .me()
      .then((profile) => setUser(profile.user))
      .catch(() => tokenStorage.clear())
      .finally(() => setInitializing(false));
  }, []);

  const handleAuthResponse = useCallback((response) => {
    tokenStorage.set(response.accessToken);
    setUser(response.user);
    return response.user;
  }, []);

  const login = useCallback(
    (email, password) => authApi.login({ email, password }).then(handleAuthResponse),
    [handleAuthResponse],
  );

  const register = useCallback((body) => authApi.register(body).then(handleAuthResponse), [handleAuthResponse]);

  const value = useMemo(
    () => ({
      user,
      initializing,
      isAuthenticated: Boolean(user),
      isAdmin: Boolean(user?.roles?.includes('ADMIN')),
      isCustomer: Boolean(user?.roles?.includes('CUSTOMER')),
      login,
      register,
      logout,
      setUser,
    }),
    [user, initializing, login, register, logout],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
