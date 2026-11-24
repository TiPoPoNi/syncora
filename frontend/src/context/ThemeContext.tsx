import React, { createContext, useContext, useEffect, useState } from 'react';
import { useAuth } from './AuthContext';
import i18n from '../i18n';

export type ThemeMode = 'LIGHT' | 'DARK' | 'SYSTEM';
export type TextSize = 'DEFAULT' | 'LARGE';
export type DisplayDensity = 'COMFORTABLE' | 'COMPACT';

export interface UserPreferences {
  theme: ThemeMode;
  language: string;
  timezone: string;
  dateFormat: string;
  firstDayOfWeek: string;
  textSize: TextSize;
  displayDensity: DisplayDensity;
  reducedMotion: boolean;
  highContrastFocus: boolean;
  inAppNotifications: boolean;
  emailNotifications: boolean;
  approvalNotifications: boolean;
  taskUpdatesNotifications: boolean;
  deadlineRemindersNotifications: boolean;
}

const defaultPreferences: UserPreferences = {
  theme: 'SYSTEM',
  language: 'en',
  timezone: 'UTC',
  dateFormat: 'YYYY-MM-DD',
  firstDayOfWeek: 'MONDAY',
  textSize: 'DEFAULT',
  displayDensity: 'COMFORTABLE',
  reducedMotion: false,
  highContrastFocus: false,
  inAppNotifications: true,
  emailNotifications: false,
  approvalNotifications: true,
  taskUpdatesNotifications: true,
  deadlineRemindersNotifications: true,
};

interface ThemeContextType {
  preferences: UserPreferences;
  resolvedTheme: 'light' | 'dark';
  updatePreferences: (newPrefs: Partial<UserPreferences>) => Promise<void>;
  restoreDefaults: () => Promise<void>;
  loading: boolean;
}

const ThemeContext = createContext<ThemeContextType | undefined>(undefined);

export function ThemeProvider({ children }: { children: React.ReactNode }) {
  const { token, isAuthenticated } = useAuth();
  const [preferences, setPreferences] = useState<UserPreferences>(() => {
    // Bootstrap from localStorage
    const savedTheme = (localStorage.getItem('syncora_theme') as ThemeMode) || 'SYSTEM';
    const savedLang = localStorage.getItem('syncora_language') || 'en';
    const savedTextSize = (localStorage.getItem('syncora_text_size') as TextSize) || 'DEFAULT';
    const savedDensity = (localStorage.getItem('syncora_density') as DisplayDensity) || 'COMFORTABLE';
    const savedReducedMotion = localStorage.getItem('syncora_reduced_motion') === 'true';

    return {
      ...defaultPreferences,
      theme: savedTheme,
      language: savedLang,
      textSize: savedTextSize,
      displayDensity: savedDensity,
      reducedMotion: savedReducedMotion,
    };
  });

  const [resolvedTheme, setResolvedTheme] = useState<'light' | 'dark'>('light');
  const [loading, setLoading] = useState(false);

  // 1. Resolve dark vs light mode
  useEffect(() => {
    const updateResolvedTheme = () => {
      let isDark = false;
      if (preferences.theme === 'DARK') {
        isDark = true;
      } else if (preferences.theme === 'SYSTEM') {
        isDark = window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches;
      }
      setResolvedTheme(isDark ? 'dark' : 'light');

      const root = document.documentElement;
      if (isDark) {
        root.classList.add('dark');
      } else {
        root.classList.remove('dark');
      }
    };

    updateResolvedTheme();

    if (preferences.theme === 'SYSTEM' && window.matchMedia) {
      const mediaQuery = window.matchMedia('(prefers-color-scheme: dark)');
      const listener = () => updateResolvedTheme();
      mediaQuery.addEventListener('change', listener);
      return () => mediaQuery.removeEventListener('change', listener);
    }
  }, [preferences.theme]);

  // 2. Apply typography & accessibility classes to root
  useEffect(() => {
    const root = document.documentElement;

    // Text size
    if (preferences.textSize === 'LARGE') {
      root.classList.add('text-lg-mode');
      root.style.fontSize = '18px';
    } else {
      root.classList.remove('text-lg-mode');
      root.style.fontSize = '';
    }

    // Display density
    if (preferences.displayDensity === 'COMPACT') {
      root.classList.add('density-compact');
    } else {
      root.classList.remove('density-compact');
    }

    // Reduced motion
    if (preferences.reducedMotion) {
      root.classList.add('reduced-motion');
    } else {
      root.classList.remove('reduced-motion');
    }

    // High contrast focus
    if (preferences.highContrastFocus) {
      root.classList.add('high-contrast-focus');
    } else {
      root.classList.remove('high-contrast-focus');
    }

    // Language sync
    if (preferences.language && i18n.language !== preferences.language) {
      i18n.changeLanguage(preferences.language);
    }
  }, [preferences]);

  // 3. Fetch preferences from backend on authentication
  useEffect(() => {
    if (!isAuthenticated || !token) return;

    const fetchPreferences = async () => {
      setLoading(true);
      try {
        const res = await fetch('/api/users/me/preferences', {
          headers: {
            Authorization: `Bearer ${token}`,
          },
        });
        if (res.ok) {
          const data = await res.json();
          setPreferences((prev) => ({
            ...prev,
            ...data,
          }));

          // Cache in localStorage for boot
          if (data.theme) localStorage.setItem('syncora_theme', data.theme);
          if (data.language) localStorage.setItem('syncora_language', data.language);
          if (data.textSize) localStorage.setItem('syncora_text_size', data.textSize);
          if (data.displayDensity) localStorage.setItem('syncora_density', data.displayDensity);
          if (data.reducedMotion !== undefined) localStorage.setItem('syncora_reduced_motion', String(data.reducedMotion));
        }
      } catch (err) {
        console.error('Failed to load user preferences:', err);
      } finally {
        setLoading(false);
      }
    };

    fetchPreferences();
  }, [isAuthenticated, token]);

  // 4. Update preferences
  const updatePreferences = async (newPrefs: Partial<UserPreferences>) => {
    const updated = { ...preferences, ...newPrefs };
    setPreferences(updated);

    // Bootstrap storage
    if (newPrefs.theme) localStorage.setItem('syncora_theme', newPrefs.theme);
    if (newPrefs.language) {
      localStorage.setItem('syncora_language', newPrefs.language);
      i18n.changeLanguage(newPrefs.language);
    }
    if (newPrefs.textSize) localStorage.setItem('syncora_text_size', newPrefs.textSize);
    if (newPrefs.displayDensity) localStorage.setItem('syncora_density', newPrefs.displayDensity);
    if (newPrefs.reducedMotion !== undefined) localStorage.setItem('syncora_reduced_motion', String(newPrefs.reducedMotion));

    if (isAuthenticated && token) {
      try {
        await fetch('/api/users/me/preferences', {
          method: 'PUT',
          headers: {
            'Content-Type': 'application/json',
            Authorization: `Bearer ${token}`,
          },
          body: JSON.stringify(updated),
        });
      } catch (err) {
        console.error('Failed to save preferences to backend:', err);
      }
    }
  };

  const restoreDefaults = async () => {
    setPreferences(defaultPreferences);
    localStorage.removeItem('syncora_theme');
    localStorage.removeItem('syncora_language');
    localStorage.removeItem('syncora_text_size');
    localStorage.removeItem('syncora_density');
    localStorage.removeItem('syncora_reduced_motion');
    i18n.changeLanguage('en');

    if (isAuthenticated && token) {
      try {
        await fetch('/api/users/me/preferences/reset', {
          method: 'POST',
          headers: {
            Authorization: `Bearer ${token}`,
          },
        });
      } catch (err) {
        console.error('Failed to reset preferences in backend:', err);
      }
    }
  };

  return (
    <ThemeContext.Provider
      value={{
        preferences,
        resolvedTheme,
        updatePreferences,
        restoreDefaults,
        loading,
      }}
    >
      {children}
    </ThemeContext.Provider>
  );
}

export function useTheme() {
  const context = useContext(ThemeContext);
  if (!context) {
    throw new Error('useTheme must be used within a ThemeProvider');
  }
  return context;
}
