import { EmptyState } from '@/components/States';
import { PageHeader } from '@/components/PageHeader';

/**
 * Shell-stage placeholder: the route, layout and breakpoints are real, the data
 * is not. Each of these is replaced by the branch that owns the feature.
 */
export function Placeholder({ title, description }: { title: string; description: string }) {
  return (
    <>
      <PageHeader title={title} />
      <EmptyState title="Not wired up yet" description={description} />
    </>
  );
}
