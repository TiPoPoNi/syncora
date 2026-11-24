import axios from 'axios';
import { handleMockRequest } from './mockApi';

// Determine if we should prioritize demo mode (e.g. on GitHub Pages or when VITE_DEMO_MODE is true)
const isGitHubPages = typeof window !== 'undefined' && window.location.hostname.endsWith('github.io');
const isExplicitDemo = import.meta.env.VITE_DEMO_MODE === 'true';
export const isDemoModeActive = isGitHubPages || isExplicitDemo;

export const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || (isDemoModeActive ? '/api' : 'http://localhost:8080/api'),
  timeout: 8000,
});

api.interceptors.request.use(async (config) => {
  // If explicitly in static demo mode (like on GitHub Pages), handle immediately via mockApi
  if (isDemoModeActive) {
    config.adapter = async () => handleMockRequest(config);
  }

  const userStr = localStorage.getItem('prs_user');
  if (userStr) {
    try {
      const user = JSON.parse(userStr);
      if (user.token) {
        config.headers.Authorization = `Bearer ${user.token}`;
      }
    } catch (e) {}
  }

  // Never send X-Organization-Id when discovering user organizations or authenticating
  const isOrgListing = config.url === '/organizations' && config.method?.toLowerCase() === 'get';
  const isAuth = config.url?.startsWith('/auth');
  if (!isOrgListing && !isAuth) {
    const currentOrgId = localStorage.getItem('prs_current_org_id');
    if (currentOrgId) {
      config.headers['X-Organization-Id'] = currentOrgId;
    }
  }
  return config;
});

api.interceptors.response.use(
  (response) => response,
  async (error) => {
    // If backend request failed due to Network Error / Offline server, automatically fallback to mock data!
    if ((!error.response || error.code === 'ERR_NETWORK') && error.config && !error.config._mockFallback) {
      error.config._mockFallback = true;
      try {
        const mockRes = await handleMockRequest(error.config);
        return mockRes;
      } catch (e) {
        return Promise.reject(error);
      }
    }

    if (error.response?.status === 401) {
      const isLoginRequest = error.config?.url?.includes('/auth/login');
      if (!isLoginRequest) {
        localStorage.removeItem('prs_user');
        localStorage.removeItem('prs_current_org_id');
        if (typeof window !== 'undefined' && !window.location.hash.includes('/login') && window.location.pathname !== '/login') {
          window.location.hash = '#/login?session_expired=true';
        }
      }
    } else if (error.response?.status === 403) {
      // If 403 is due to organization membership mismatch, clear stale org id
      const msg = error.response?.data?.message || '';
      if (msg.includes('membership in organization') || msg.includes('Organization not found')) {
        localStorage.removeItem('prs_current_org_id');
      }
    }
    return Promise.reject(error);
  }
);
