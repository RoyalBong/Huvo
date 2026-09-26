import { create } from 'zustand';

export type Theme = 'dark' | 'light';

const STORAGE_KEY = 'huvo-theme';

function initialTheme(): Theme {
  if (typeof localStorage === 'undefined') return 'dark';
  return localStorage.getItem(STORAGE_KEY) === 'light' ? 'light' : 'dark';
}

function applyTheme(theme: Theme): void {
  const root = document.documentElement;
  root.classList.toggle('light', theme === 'light');
  root.classList.toggle('dark', theme === 'dark');
}

interface ThemeState {
  theme: Theme;
  setTheme: (theme: Theme) => void;
  toggle: () => void;
}

/**
 * Dark mode is the primary experience; light mode is equally complete (§4.1).
 * The document class is also set by a tiny inline script in index.html to
 * avoid a flash of the wrong theme before React boots.
 */
export const useTheme = create<ThemeState>((set, get) => {
  const start = initialTheme();
  applyTheme(start);

  return {
    theme: start,
    setTheme: (theme) => {
      applyTheme(theme);
      localStorage.setItem(STORAGE_KEY, theme);
      set({ theme });
    },
    toggle: () => get().setTheme(get().theme === 'dark' ? 'light' : 'dark'),
  };
});
