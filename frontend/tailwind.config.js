/**
 * Design tokens from design.md sections 1.3 to 1.7. Values live here as CSS
 * variables (see src/styles/index.css) so the same token drives Tailwind
 * utilities and any raw CSS.
 */
export default {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        bg: 'var(--color-bg)',
        surface: 'var(--color-surface)',
        border: 'var(--color-border)',
        'text-primary': 'var(--color-text-primary)',
        'text-secondary': 'var(--color-text-secondary)',
        'text-muted': 'var(--color-text-muted)',
        accent: {
          DEFAULT: 'var(--color-accent)',
          hover: 'var(--color-accent-hover)',
        },
        success: 'var(--color-success)',
        warning: 'var(--color-warning)',
        danger: 'var(--color-danger)',
        neutral: 'var(--color-neutral)',
      },
      borderRadius: {
        // design.md 1.6: 8px cards/inputs/buttons, 12px modals, pill badges.
        DEFAULT: '8px',
        card: '8px',
        modal: '12px',
        pill: '999px',
      },
      boxShadow: {
        sm: '0 1px 2px rgba(16,24,40,0.06)',
        md: '0 4px 12px rgba(16,24,40,0.10)',
      },
      fontFamily: {
        sans: ['Inter', 'system-ui', '-apple-system', 'Segoe UI', 'Roboto', 'sans-serif'],
      },
      fontSize: {
        // design.md 1.4 type scale.
        display: ['1.875rem', { lineHeight: '2.25rem', fontWeight: '600' }],
        section: ['1.3125rem', { lineHeight: '1.75rem', fontWeight: '600' }],
        cardTitle: ['0.9375rem', { lineHeight: '1.375rem', fontWeight: '600' }],
        body: ['0.875rem', { lineHeight: '1.25rem' }],
        meta: ['0.8125rem', { lineHeight: '1.125rem' }],
      },
      spacing: {
        // design.md 1.5: 4px base unit.
        1: '4px',
        2: '8px',
        3: '12px',
        4: '16px',
        6: '24px',
        8: '32px',
        12: '48px',
        16: '64px',
      },
    },
  },
  plugins: [],
};
