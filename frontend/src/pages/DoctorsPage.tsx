import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { doctors } from '@/api/endpoints';
import { Button } from '@/components/Button';
import { Card } from '@/components/Card';
import { PageHeader } from '@/components/PageHeader';
import { EmptyState, ErrorState, SkeletonRows } from '@/components/States';
import { formatFee } from '@/lib/format';

export function DoctorsPage() {
  const [page, setPage] = useState(0);

  const query = useQuery({
    queryKey: ['doctors', page],
    queryFn: () => doctors.list(page),
  });

  return (
    <>
      <PageHeader title="Doctors" description="Pick a doctor to see when they are free." />

      {query.isPending && <SkeletonRows rows={4} />}

      {query.isError && (
        <ErrorState message="We could not load the doctor list." onRetry={() => query.refetch()} />
      )}

      {query.isSuccess &&
        (query.data.content.length === 0 ? (
          <EmptyState
            title="No doctors yet"
            description="Once the clinic adds doctors, they will appear here."
          />
        ) : (
          <>
            <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
              {query.data.content.map((doctor) => (
                <Card key={doctor.doctorId} className="flex flex-col gap-3">
                  <div>
                    <p className="text-cardTitle text-text-primary">{doctor.name}</p>
                    <p className="text-meta text-text-secondary">{doctor.specialization}</p>
                  </div>
                  <p className="tabular text-body text-text-secondary">
                    {formatFee(doctor.consultationFee)} per visit
                  </p>
                  <Link to={`/doctors/${doctor.doctorId}`} className="mt-auto">
                    <Button className="w-full">View availability</Button>
                  </Link>
                </Card>
              ))}
            </div>

            {query.data.totalPages > 1 && (
              <nav className="mt-6 flex items-center justify-between" aria-label="Pagination">
                <Button
                  variant="secondary"
                  disabled={page === 0}
                  onClick={() => setPage((current) => current - 1)}
                >
                  Previous
                </Button>
                <span className="tabular text-meta text-text-secondary">
                  Page {query.data.page + 1} of {query.data.totalPages}
                </span>
                <Button
                  variant="secondary"
                  disabled={page >= query.data.totalPages - 1}
                  onClick={() => setPage((current) => current + 1)}
                >
                  Next
                </Button>
              </nav>
            )}
          </>
        ))}
    </>
  );
}
