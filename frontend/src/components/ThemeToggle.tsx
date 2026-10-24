import React, { useEffect, useState } from 'react';
import { Moon, Sun } from 'lucide-react';

export type Theme = 'dark' | 'light';

const THEME_STORAGE_KEY = 'aegis-theme';

export const ThemeToggle: React.FC = () => {
  // Only the theme name ('dark' | 'light') may be stored in localStorage.
  // Invariant: Never store any transaction data, event IDs, or decision payloads in client storage.
  const [theme, setTheme] = useState<Theme>(() => {
    try {
      const stored = localStorage.getItem(THEME_STORAGE_KEY);
      if (stored === 'light' || stored === 'dark') {
        return stored;
      }
    } catch {
      // Storage access blocked or restricted
    }
    return 'dark'; // Dark theme is default per DESIGN.md §2.1
  });

  useEffect(() => {
    const root = document.documentElement;
    root.setAttribute('data-theme', theme);
    if (theme === 'light') {
      root.classList.add('light');
      root.classList.remove('dark');
    } else {
      root.classList.add('dark');
      root.classList.remove('light');
    }

    try {
      localStorage.setItem(THEME_STORAGE_KEY, theme);
    } catch {
      // Storage access restricted
    }
  }, [theme]);

  const toggleTheme = () => {
    setTheme((prev) => (prev === 'dark' ? 'light' : 'dark'));
  };

  return (
    <button
      type="button"
      onClick={toggleTheme}
      aria-label={`Switch to ${theme === 'dark' ? 'light' : 'dark'} theme`}
      className="p-1.5 rounded-sm border border-border bg-surface hover:bg-surface-raised text-text transition-colors duration-150 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-accent"
      title={`Toggle theme (current: ${theme})`}
    >
      {theme === 'dark' ? (
        <Sun className="w-4 h-4 text-text-muted hover:text-text transition-colors" aria-hidden="true" />
      ) : (
        <Moon className="w-4 h-4 text-text-muted hover:text-text transition-colors" aria-hidden="true" />
      )}
    </button>
  );
};
