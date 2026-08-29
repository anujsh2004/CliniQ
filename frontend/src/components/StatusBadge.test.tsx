import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { StatusBadge } from './StatusBadge';

describe('StatusBadge', () => {
  it('always carries a text label, never colour alone', () => {
    // design.md 3.7: colour-blind users must be able to read status, which is
    // the whole reason the label is not optional.
    render(<StatusBadge status="CONFIRMED" />);
    expect(screen.getByText('Confirmed')).toBeInTheDocument();
  });

  it('reads enum values as words rather than database constants', () => {
    render(<StatusBadge status="PENDING_PAYMENT" />);
    expect(screen.getByText('Pending payment')).toBeInTheDocument();
  });

  it.each([
    ['CONFIRMED', 'text-success'],
    ['AVAILABLE', 'text-success'],
    ['PENDING_PAYMENT', 'text-warning'],
    ['CANCELLED', 'text-danger'],
    ['NO_SHOW', 'text-danger'],
    ['BOOKED', 'text-neutral'],
    ['EXPIRED', 'text-neutral'],
  ])('maps %s to its variant from the design system', (status, expectedClass) => {
    // The variant is derived from the status, never passed in, so one status
    // cannot look different in two places (design.md 4.1).
    const { container } = render(<StatusBadge status={status} />);
    expect(container.firstElementChild?.className).toContain(expectedClass);
  });

  it('falls back to the neutral variant for an unknown status', () => {
    const { container } = render(<StatusBadge status="SOMETHING_NEW" />);
    expect(container.firstElementChild?.className).toContain('text-neutral');
    expect(screen.getByText('Something new')).toBeInTheDocument();
  });
});
