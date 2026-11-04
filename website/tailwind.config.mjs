/** @type {import('tailwindcss').Config} */
export default {
  content: ['./src/**/*.{astro,html,js,ts}'],
  darkMode: ['class', '[data-theme="dark"]'],
  theme: {
    extend: {
      colors: {
        canvas: 'var(--canvas)',
        surface: 'var(--surface)',
        'surface-raised': 'var(--surface-raised)',
        border: 'var(--border)',
        text: 'var(--text)',
        'text-muted': 'var(--text-muted)',
        accent: 'var(--accent)',
        'accent-solid': 'var(--accent-solid)',
        'accent-hover': 'var(--accent-hover)',
        approve: 'var(--approve)',
        'approve-bg': 'var(--approve-bg)',
        review: 'var(--review)',
        'review-bg': 'var(--review-bg)',
        decline: 'var(--decline)',
        'decline-text': 'var(--decline-text)',
        'decline-bg': 'var(--decline-bg)',
        info: 'var(--info)',
        'info-bg': 'var(--info-bg)',
      },
      fontFamily: {
        sans: ['Inter', '-apple-system', 'BlinkMacSystemFont', '"Segoe UI"', 'Roboto', 'sans-serif'],
        mono: ['"JetBrains Mono"', 'Menlo', 'Consolas', 'monospace'],
      },
      borderRadius: { sm: '4px', md: '6px', lg: '10px' },
    },
  },
  plugins: [],
};
