import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ApiRequestError } from '@/api/client';
import { notifications } from '@/api/endpoints';
import { Button } from '@/components/Button';
import { Card } from '@/components/Card';
import { PageHeader } from '@/components/PageHeader';
import { EmptyState, ErrorState, SkeletonRows } from '@/components/States';
import { useToast } from '@/components/Toast';
import type { NotificationSummary } from '@/types/api';

/**
 * How long ago something happened, in the words a person would use.
 *
 * <p>A reminder's value is mostly "how recent is this", and an exact timestamp
 * makes the reader do that arithmetic themselves.
 */
function relativeTime(iso: string): string {
  const minutes = Math.round((Date.now() - new Date(iso).getTime()) / 60_000);
  if (minutes < 1) return 'just now';
  if (minutes < 60) return `${minutes} min ago`;
  const hours = Math.round(minutes / 60);
  if (hours < 24) return `${hours} hour${hours === 1 ? '' : 's'} ago`;
  const days = Math.round(hours / 24);
  return `${days} day${days === 1 ? '' : 's'} ago`;
}

function exactTime(iso: string): string {
  return new Date(iso).toLocaleString('en-IN', {
    day: 'numeric',
    month: 'short',
    hour: 'numeric',
    minute: '2-digit',
  });
}

export function NotificationsPage() {
  const toast = useToast();
  const queryClient = useQueryClient();

  const query = useQuery({
    queryKey: ['notifications'],
    queryFn: () => notifications.mine(),
  });

  const refresh = () => {
    void queryClient.invalidateQueries({ queryKey: ['notifications'] });
    void queryClient.invalidateQueries({ queryKey: ['notifications', 'unread'] });
  };

  const markAllRead = useMutation({
    mutationFn: () => notifications.markAllRead(),
    onSuccess: refresh,
    onError: (error) =>
      toast.error(error instanceof ApiRequestError ? error.message : 'Could not update reminders.'),
  });

  const markRead = useMutation({
    mutationFn: (notificationId: string) => notifications.markRead(notificationId),
    onSuccess: refresh,
  });

  const remove = useMutation({
    mutationFn: (notificationId: string) => notifications.removeReminder(notificationId),
    onSuccess: () => {
      toast.success('Reminder removed.');
      refresh();
    },
    onError: (error) =>
      toast.error(error instanceof ApiRequestError ? error.message : 'Could not remove it.'),
  });

  if (query.isPending) {
    return <SkeletonRows rows={4} />;
  }

  if (query.isError) {
    return (
      <ErrorState
        message="We could not load your reminders."
        onRetry={() => void query.refetch()}
      />
    );
  }

  const items: NotificationSummary[] = query.data ?? [];
  const unread = items.filter((item) => !item.read).length;

  return (
    <>
      <PageHeader
        title="Reminders"
        description="Notices about your upcoming appointments."
        actions={
          unread > 0 ? (
            <Button variant="secondary" onClick={() => markAllRead.mutate()}>
              Mark all read
            </Button>
          ) : undefined
        }
      />

      {items.length === 0 ? (
        <EmptyState
          title="No reminders yet"
          description="Once you book an appointment, reminders about it appear here."
        />
      ) : (
        <div className="flex flex-col gap-3">
          {items.map((item) => (
            <Card key={item.notificationId}>
              <div className="flex items-start justify-between gap-4">
                <div className="flex items-start gap-3">
                  {/* An unread marker, with a text label beside it so it is
                      never colour alone (design.md 3.7). */}
                  {!item.read && (
                    <span
                      aria-hidden="true"
                      className="mt-2 h-2 w-2 shrink-0 rounded-pill bg-accent"
                    />
                  )}
                  <div>
                    <p
                      className={`text-body ${
                        item.read ? 'text-text-secondary' : 'font-medium text-text-primary'
                      }`}
                    >
                      {item.message}
                    </p>
                    <p className="mt-1 text-meta text-text-muted">
                      {relativeTime(item.scheduledFor)} · {exactTime(item.scheduledFor)}
                      {!item.read && ' · Unread'}
                      {item.patientRequested && ' · Your reminder'}
                    </p>
                  </div>
                </div>
                <div className="flex shrink-0 gap-2">
                  {!item.read && (
                    <Button
                      variant="secondary"
                      onClick={() => markRead.mutate(item.notificationId)}
                    >
                      Mark read
                    </Button>
                  )}
                  {item.patientRequested && (
                    <Button variant="secondary" onClick={() => remove.mutate(item.notificationId)}>
                      Remove
                    </Button>
                  )}
                </div>
              </div>
            </Card>
          ))}
        </div>
      )}
    </>
  );
}
