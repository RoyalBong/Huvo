/** Tailwind config — tokens only (Huvo_Frontend_Context.md §4). */
/** @type {import('tailwindcss').Config} */
module.exports = {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  darkMode: 'class',
  theme: {
    extend: {
      colors: {
        // Semantic surface layering — values come from CSS variables so dark
        // (hero) and light (equally complete) modes share one token set.
        bg: 'rgb(var(--c-bg) / <alpha-value>)',
        'surface-1': 'rgb(var(--c-surface-1) / <alpha-value>)',
        'surface-2': 'rgb(var(--c-surface-2) / <alpha-value>)',
        'surface-3': 'rgb(var(--c-surface-3) / <alpha-value>)',
        edge: 'rgb(var(--c-border) / <alpha-value>)',
        fg: 'rgb(var(--c-fg) / <alpha-value>)',
        muted: 'rgb(var(--c-muted) / <alpha-value>)',
        faint: 'rgb(var(--c-faint) / <alpha-value>)',
        // Primary: soft indigo / electric violet (§4.1)
        primary: {
          DEFAULT: 'rgb(var(--c-primary) / <alpha-value>)',
          ink: 'rgb(var(--c-primary-ink) / <alpha-value>)',
          fg: 'rgb(var(--c-primary-fg) / <alpha-value>)',
        },
        // Accent: cyan/teal for success-of-live indicators (§4.1)
        accent: {
          DEFAULT: 'rgb(var(--c-accent) / <alpha-value>)',
          fg: 'rgb(var(--c-accent-fg) / <alpha-value>)',
        },
        // Semantic (§4.1)
        success: {
          DEFAULT: 'rgb(var(--c-success) / <alpha-value>)',
          fg: 'rgb(var(--c-success-fg) / <alpha-value>)',
        },
        warning: {
          DEFAULT: 'rgb(var(--c-warning) / <alpha-value>)',
          fg: 'rgb(var(--c-warning-fg) / <alpha-value>)',
        },
        danger: {
          DEFAULT: 'rgb(var(--c-danger) / <alpha-value>)',
          fg: 'rgb(var(--c-danger-fg) / <alpha-value>)',
        },
        info: {
          DEFAULT: 'rgb(var(--c-info) / <alpha-value>)',
          fg: 'rgb(var(--c-info-fg) / <alpha-value>)',
        },
      },
      fontFamily: {
        sans: ['Inter', 'ui-sans-serif', 'system-ui', '-apple-system', 'Segoe UI', 'sans-serif'],
        mono: ['JetBrains Mono', 'ui-monospace', 'SFMono-Regular', 'Menlo', 'monospace'],
      },
      borderRadius: {
        card: '1rem',
      },
      boxShadow: {
        soft: '0 1px 2px rgb(0 0 0 / 0.25), 0 12px 32px -16px rgb(0 0 0 / 0.5)',
        lift: '0 2px 4px rgb(0 0 0 / 0.28), 0 24px 48px -20px rgb(0 0 0 / 0.55)',
        glow: '0 0 0 1px rgb(var(--c-primary) / 0.35), 0 0 24px -6px rgb(var(--c-primary) / 0.4)',
      },
      transitionTimingFunction: {
        // Fast and purposeful — §4.3 (150–300ms, spring-like easing)
        snappy: 'cubic-bezier(0.22, 1, 0.36, 1)',
      },
      keyframes: {
        'live-pulse': {
          '0%, 100%': { opacity: '1', transform: 'scale(1)' },
          '50%': { opacity: '0.55', transform: 'scale(0.85)' },
        },
        'fade-up': {
          from: { opacity: '0', transform: 'translateY(6px)' },
          to: { opacity: '1', transform: 'translateY(0)' },
        },
        'fade-in': {
          from: { opacity: '0' },
          to: { opacity: '1' },
        },
      },
      animation: {
        'live-pulse': 'live-pulse 2s ease-in-out infinite',
        'fade-up': 'fade-up 220ms cubic-bezier(0.22, 1, 0.36, 1) both',
        'fade-in': 'fade-in 160ms cubic-bezier(0.22, 1, 0.36, 1) both',
      },
    },
  },
  plugins: [],
};
