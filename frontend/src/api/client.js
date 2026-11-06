import axios from 'axios';

/**
 * One Axios instance for the whole app.
 *  - baseURL: "/api/v1" (the Vite dev server proxies /api to Spring Boot)
 *  - request interceptor: attaches "Authorization: Bearer <token>"
 *  - response interceptor: a 401 on an authenticated request means the token expired or
 *    the account was disabled -> notify AuthContext, which logs the user out.
 */
const TOKEN_KEY = 'northcart.token';

export const tokenStorage = {
  get: () => {
    try {
      return localStorage.getItem(TOKEN_KEY);
    } catch {
      return null;
    }
  },
  set: (token) => {
    try {
      localStorage.setItem(TOKEN_KEY, token);
    } catch {
      /* storage unavailable (private mode): the session just won't survive a reload */
    }
  },
  clear: () => {
    try {
      localStorage.removeItem(TOKEN_KEY);
    } catch {
      /* ignore */
    }
  },
};

let onUnauthorized = () => {};
/** AuthContext registers what should happen when the server rejects our token. */
export function setUnauthorizedHandler(handler) {
  onUnauthorized = handler;
}

export const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api/v1',
  timeout: 20000,
  headers: { 'Content-Type': 'application/json' },
});

api.interceptors.request.use((config) => {
  const token = tokenStorage.get();
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

api.interceptors.response.use(
  (response) => response,
  (error) => {
    const hadToken = Boolean(error.config?.headers?.Authorization);
    if (error.response?.status === 401 && hadToken) {
      onUnauthorized(error.response.data?.message);
    }
    return Promise.reject(error);
  },
);
