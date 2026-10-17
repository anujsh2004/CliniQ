import { Link } from 'react-router-dom';

/**
 * The public page: what Cliniva does, for someone who has not signed in.
 *
 * <p>It carries its own palette and typefaces rather than the app's. The
 * signed-in product is a working tool and stays quiet; this page has one job,
 * which is to explain what the clinic offers and get the reader to the sign-in
 * form.
 */

interface Feature {
  label: string;
  title: string;
  body: string;
}

/** What a patient gets. Written from their side, not the system's. */
const FOR_PATIENTS: Feature[] = [
  {
    label: 'Real availability',
    title: 'See what is actually free',
    body: 'Every time shown is a real, bookable slot on that doctor’s calendar. No calling the clinic to ask, and no waiting for a callback to find out.',
  },
  {
    label: 'Held while you pay',
    title: 'The slot is yours for five minutes',
    body: 'Choosing a time takes it off the market immediately. Finish paying and it is confirmed; change your mind and it returns to other patients automatically. Nothing is lost either way.',
  },
  {
    label: 'Never double-booked',
    title: 'Two people cannot take one slot',
    body: 'If someone books the same minute you do, exactly one booking succeeds and the other is told straight away, with the grid refreshed. The clinic never has to sort it out afterwards.',
  },
  {
    label: 'Reminders',
    title: 'A nudge the day before, and an hour before',
    body: 'Reminders arrive in your account, not in a message you might miss. Want one three hours ahead as well? Add your own.',
  },
];

/** What the clinic gets. */
const FOR_CLINICS: Feature[] = [
  {
    label: 'Weekly hours',
    title: 'Set your hours once',
    body: 'Define the hours you work each week and the system generates bookable slots from them, a month ahead, topping them up nightly.',
  },
  {
    label: 'Changes and time off',
    title: 'Change your mind without breaking anything',
    body: 'Edit a window, remove one, or block a single date for a conference. Appointments already booked are never cancelled behind a patient’s back — you are told how many need rescheduling.',
  },
  {
    label: 'Your day at a glance',
    title: 'Today, in order, with contact details',
    body: 'The schedule shows who is coming and when, with the phone number beside each name, and marking a visit complete is one tap.',
  },
];

const STEPS = [
  { title: 'Pick a doctor', body: 'Browse by name and speciality, with the consultation fee shown up front.' },
  { title: 'Choose a time', body: 'Real slots for the date you pick, grouped into morning and afternoon.' },
  { title: 'Review and confirm', body: 'Doctor, date, time and fee restated before anything is committed.' },
  { title: 'Pay within five minutes', body: 'The slot is held until you do. Miss it and it goes back on offer.' },
];

function Eyebrow({ children }: { children: React.ReactNode }) {
  return (
    <p className="font-mono text-[0.7rem] uppercase tracking-[0.14em] text-[var(--ink-faint)]">
      {children}
    </p>
  );
}

function SectionHead({ marker, title }: { marker: string; title: string }) {
  return (
    <div className="mb-8 flex items-baseline gap-3 border-b-2 border-[var(--ink)] pb-3">
      <span className="font-mono text-[0.8rem] tracking-[0.1em] text-[var(--ink-faint)]">
        {marker}
      </span>
      <h2 className="font-display text-[clamp(1.5rem,3vw,1.9rem)] font-bold leading-tight text-[var(--ink)]">
        {title}
      </h2>
    </div>
  );
}

function FeatureCard({ feature }: { feature: Feature }) {
  return (
    <article className="rounded-modal border border-[var(--rule)] bg-[var(--surface)] p-6">
      <Eyebrow>{feature.label}</Eyebrow>
      <h3 className="mt-3 font-display text-[1.15rem] font-bold leading-snug text-[var(--ink)]">
        {feature.title}
      </h3>
      <p className="mt-2 text-[0.95rem] leading-relaxed text-[var(--ink-soft)]">{feature.body}</p>
    </article>
  );
}

export function LandingPage() {
  return (
    <div className="landing">
      <header className="border-b border-[var(--rule)] bg-[var(--surface)]">
        <div className="mx-auto flex max-w-[64rem] items-center justify-between gap-4 px-6 py-4">
          <span className="font-display text-[1.15rem] font-bold tracking-tight text-[var(--brand)]">
            Cliniva
          </span>
          {/* The reason someone arrives here is to get in, so the way in is
              the first thing in reach. */}
          <nav className="flex items-center gap-2">
            <Link
              to="/login"
              className="rounded-card px-4 py-2 text-[0.9rem] font-medium text-[var(--ink-soft)] transition-colors hover:bg-[var(--surface-sunk)] hover:text-[var(--ink)]"
            >
              Sign in
            </Link>
            <Link
              to="/register"
              className="rounded-card bg-[var(--brand)] px-4 py-2 text-[0.9rem] font-semibold text-[#0e1117] transition-opacity hover:opacity-90"
            >
              Create account
            </Link>
          </nav>
        </div>
      </header>

      <main className="mx-auto max-w-[64rem] px-6 pb-24">
        {/* -------------------------------------------------- hero */}
        <section className="border-b border-[var(--rule-soft)] py-16 md:py-20">
          <Eyebrow>Clinic booking, for small practices</Eyebrow>
          <h1 className="mt-4 max-w-[18ch] text-balance font-display text-[clamp(2.4rem,6vw,3.6rem)] font-bold leading-[1.1] tracking-tight text-[var(--ink)]">
            Book a real appointment, not a callback.
          </h1>
          <p className="mt-5 max-w-[54ch] text-[1.05rem] leading-relaxed text-[var(--ink-soft)]">
            Cliniva shows a doctor’s genuine availability, holds the slot you choose while you pay,
            and guarantees no two patients are ever given the same time.
          </p>

          <div className="mt-8 flex flex-wrap gap-3">
            <Link
              to="/register"
              className="rounded-card bg-[var(--brand)] px-5 py-3 text-[0.95rem] font-semibold text-[#0e1117] transition-opacity hover:opacity-90"
            >
              Book an appointment
            </Link>
            <Link
              to="/login"
              className="rounded-card border border-[var(--rule)] px-5 py-3 text-[0.95rem] font-medium text-[var(--ink)] transition-colors hover:bg-[var(--surface-sunk)]"
            >
              I already have an account
            </Link>
          </div>

          <dl className="mt-12 flex flex-wrap gap-x-10 gap-y-4 border-t border-[var(--rule-soft)] pt-6">
            {[
              { term: 'Slot hold', value: '5 min' },
              { term: 'Booking window', value: '30 days' },
              { term: 'Reminders', value: '24h + 1h' },
              { term: 'Double bookings', value: 'Zero' },
            ].map((fact) => (
              <div key={fact.term}>
                <dt className="font-mono text-[0.7rem] uppercase tracking-[0.12em] text-[var(--ink-faint)]">
                  {fact.term}
                </dt>
                <dd className="tabular mt-1 font-display text-[1.4rem] font-bold text-[var(--ink)]">
                  {fact.value}
                </dd>
              </div>
            ))}
          </dl>
        </section>

        {/* -------------------------------------------------- patients */}
        <section className="pt-16">
          <SectionHead marker="01" title="For patients" />
          <div className="grid gap-4 md:grid-cols-2">
            {FOR_PATIENTS.map((feature) => (
              <FeatureCard key={feature.label} feature={feature} />
            ))}
          </div>
        </section>

        {/* -------------------------------------------------- booking steps */}
        <section className="pt-16">
          <SectionHead marker="02" title="Booking, start to finish" />
          {/* Numbered because it genuinely is a sequence: each step depends on
              the one before it. */}
          <ol className="grid gap-5">
            {STEPS.map((step, index) => (
              <li key={step.title} className="grid grid-cols-[2.25rem_1fr] gap-4">
                <span className="tabular grid h-9 w-9 place-items-center rounded-pill bg-[var(--brand-soft)] font-mono text-[0.8rem] text-[var(--brand)]">
                  {index + 1}
                </span>
                <div>
                  <h3 className="font-display text-[1.05rem] font-bold text-[var(--ink)]">
                    {step.title}
                  </h3>
                  <p className="mt-1 max-w-[60ch] text-[0.95rem] leading-relaxed text-[var(--ink-soft)]">
                    {step.body}
                  </p>
                </div>
              </li>
            ))}
          </ol>
        </section>

        {/* -------------------------------------------------- clinics */}
        <section className="pt-16">
          <SectionHead marker="03" title="For clinics" />
          <div className="grid gap-4 md:grid-cols-3">
            {FOR_CLINICS.map((feature) => (
              <FeatureCard key={feature.label} feature={feature} />
            ))}
          </div>
        </section>

        {/* -------------------------------------------------- guarantee */}
        <section className="pt-16">
          <SectionHead marker="04" title="The one promise" />
          <div className="rounded-modal border border-[var(--rule)] bg-[var(--surface)] p-8">
            <p className="max-w-[60ch] font-display text-[clamp(1.2rem,2.5vw,1.5rem)] font-semibold leading-snug text-[var(--ink)]">
              A slot is never given to two patients — even when both tap Confirm in the same
              millisecond.
            </p>
            <p className="mt-4 max-w-[62ch] text-[0.95rem] leading-relaxed text-[var(--ink-soft)]">
              The database decides, not the browser. One booking succeeds, the other is told
              immediately and shown the times still open. It is the guarantee everything else in
              Cliniva is built around, and it holds under load: 96 people booking against 32 slots
              produced exactly 32 appointments.
            </p>
            <p className="mt-5 inline-flex items-center gap-2 rounded-pill bg-[var(--brand-soft)] px-3 py-1 font-mono text-[0.75rem] text-[var(--good)]">
              Verified under concurrent load
            </p>
          </div>
        </section>

        {/* -------------------------------------------------- closing */}
        <section className="pt-16">
          <div className="flex flex-wrap items-center justify-between gap-6 rounded-modal border border-[var(--rule)] bg-[var(--surface-sunk)] p-8">
            <div>
              <h2 className="font-display text-[1.4rem] font-bold text-[var(--ink)]">
                Ready to book?
              </h2>
              <p className="mt-1 text-[0.95rem] text-[var(--ink-soft)]">
                Creating an account takes a minute.
              </p>
            </div>
            <div className="flex gap-3">
              <Link
                to="/register"
                className="rounded-card bg-[var(--brand)] px-5 py-3 text-[0.95rem] font-semibold text-[#0e1117] transition-opacity hover:opacity-90"
              >
                Create account
              </Link>
              <Link
                to="/login"
                className="rounded-card border border-[var(--rule)] px-5 py-3 text-[0.95rem] font-medium text-[var(--ink)] transition-colors hover:bg-[var(--surface)]"
              >
                Sign in
              </Link>
            </div>
          </div>
        </section>
      </main>

      <footer className="border-t border-[var(--rule)]">
        <div className="mx-auto max-w-[64rem] px-6 py-8">
          <p className="font-mono text-[0.75rem] text-[var(--ink-faint)]">
            Cliniva · clinic management for small practices
          </p>
        </div>
      </footer>
    </div>
  );
}
