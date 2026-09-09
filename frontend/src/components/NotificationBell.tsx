import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { notifications } from '@/api/endpoints';

/**
 * The unread reminder count, in the header where people look for it.
 *
 * <p>The sidebar already links to Reminders, but a count only helps if it is
 * where the eye goes, and a page you have to remember to visit is a page you
 * do not visit.
 *
 * <p>Polls rather than pushes: reminders become due on a schedule, so a badge
 * that only updated on navigation would be stale exactly when it matters.
 */
export function NotificationBell() {
  const { data } = useQuery({
    queryKey: ['notifications', 'unread'],
    queryFn: () => notifications.unreadCount(),
    // Often enough to catch a reminder falling due, rarely enough to be
    // invisible in the network tab.
    refetchInterval: 60_000,
    refetchOnWindowFocus: true,
  });

  const unread = data?.unread ?? 0;
  const label = unread === 0 ? 'Reminders' : `Reminders, ${unread} unread`;

  return (
    <Link
      to="/reminders"
      aria-label={label}
      className="relative inline-flex items-center gap-2 rounded-card px-2 py-1 text-body text-text-secondary hover:bg-bg hover:text-text-primary"
    >
      {/* A line icon, matching design.md 1.8. */}
      <svg
        width="20"
        height="20"
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        strokeWidth="1.75"
        strokeLinecap="round"
        strokeLinejoin="round"
        aria-hidden="true"
      >
        <path d="M18 8a6 6 0 1 0-12 0c0 7-3 9-3 9h18s-3-2-3-9" />
        <path d="M13.73 21a2 2 0 0 1-3.46 0" />
      </svg>
      {unread > 0 && (
        // The number is inside the badge as text, so the count is never
        // conveyed by a coloured dot alone (design.md 3.7).
        <span className="tabular absolute -right-1 -top-1 min-w-[1.15rem] rounded-pill bg-danger px-1 text-center text-[0.7rem] font-semibold leading-[1.15rem] text-white">
          {unread > 9 ? '9+' : unread}
        </span>
      )}
    </Link>
  );
}
