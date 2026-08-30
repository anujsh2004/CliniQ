#!/usr/bin/env node
/**
 * Load test for the two endpoints that decide whether the product feels
 * real-time: slot fetch and booking (product-description.md NFR-8,
 * tech-stack.md Phase 5).
 *
 * Deliberately dependency-free so it runs anywhere Node does, with nothing to
 * install and nothing to keep up to date.
 *
 *   node scripts/load-test.mjs [--concurrency 20] [--requests 300]
 */

const args = Object.fromEntries(
  process.argv.slice(2).flatMap((arg, index, all) =>
    arg.startsWith('--') ? [[arg.slice(2), all[index + 1]]] : [],
  ),
);

const API = args.api ?? 'http://localhost:8080/api/v1';
const CONCURRENCY = Number(args.concurrency ?? 20);
const REQUESTS = Number(args.requests ?? 300);
const PASSWORD = 'StrongPassword123';

/** NFR-8: slot fetch stays under this to support real-time UI feedback. */
const SLOT_FETCH_TARGET_MS = 500;

function unique(prefix) {
  return `${prefix}-${Date.now()}-${Math.floor(Math.random() * 100000)}`;
}

/** Each simulated person gets its own address: auth is rate limited per client. */
let clientCounter = 0;
function client() {
  clientCounter += 1;
  const block = Math.floor(clientCounter / 250) % 250;
  return { 'X-Forwarded-For': `198.18.${block}.${clientCounter % 250}` };
}

async function post(path, body, headers = {}) {
  const response = await fetch(`${API}${path}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...headers },
    body: JSON.stringify(body),
  });
  const text = await response.text();
  if (!response.ok) {
    throw new Error(`POST ${path} -> ${response.status}: ${text.slice(0, 200)}`);
  }
  return JSON.parse(text).data;
}

async function seedPatient() {
  const headers = client();
  const email = `${unique('load')}@example.com`;
  const phone = `+9197${Math.floor(10000000 + Math.random() * 89999999)}`;
  await post(
    '/auth/register',
    { name: 'Load Tester', email, phone, password: PASSWORD, role: 'PATIENT' },
    headers,
  );
  const login = await post('/auth/login', { email, password: PASSWORD }, headers);
  return { token: login.accessToken, headers };
}

async function seedDoctorWithSlots() {
  const headers = client();
  const email = `${unique('loaddoc')}@example.com`;
  const phone = `+9196${Math.floor(10000000 + Math.random() * 89999999)}`;
  const name = `Dr. Load ${unique('')}`;
  await post('/auth/register', { name, email, phone, password: PASSWORD, role: 'DOCTOR' }, headers);
  const login = await post('/auth/login', { email, password: PASSWORD }, headers);
  const auth = { Authorization: `Bearer ${login.accessToken}`, ...headers };

  const doctor = await post(
    '/doctors',
    {
      name,
      specialization: 'Dentist',
      licenseNumber: unique('LICLOAD').toUpperCase(),
      consultationFee: 500,
      clinic: { name: 'Load Test Clinic', address: 'MG Road, Chennai', phone: '+919876543210' },
    },
    auth,
  );

  // A weekday inside the slot generation horizon, at a short slot duration so
  // there are plenty of slots to contend over.
  const date = new Date();
  date.setDate(date.getDate() + 2);
  const days = ['SUNDAY', 'MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY'];
  await post(
    `/doctors/${doctor.doctorId}/availability`,
    {
      dayOfWeek: days[date.getDay()],
      startTime: '09:00:00',
      endTime: '17:00:00',
      slotDurationMinutes: 15,
    },
    auth,
  );

  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return { doctorId: doctor.doctorId, date: `${date.getFullYear()}-${month}-${day}` };
}

function percentile(sorted, p) {
  if (sorted.length === 0) return 0;
  const index = Math.min(sorted.length - 1, Math.ceil((p / 100) * sorted.length) - 1);
  return sorted[index];
}

function report(label, latencies, failures, elapsedMs, targetMs) {
  const sorted = [...latencies].sort((a, b) => a - b);
  const throughput = Math.round((latencies.length / elapsedMs) * 1000);
  console.log(`\n${label}`);
  console.log(
    `  requests ${latencies.length + failures}, failures ${failures}, ${throughput} req/s`,
  );
  console.log(
    `  min ${Math.round(sorted[0] ?? 0)}ms | p50 ${Math.round(percentile(sorted, 50))}ms | ` +
      `p95 ${Math.round(percentile(sorted, 95))}ms | p99 ${Math.round(percentile(sorted, 99))}ms | ` +
      `max ${Math.round(sorted[sorted.length - 1] ?? 0)}ms`,
  );
  if (targetMs) {
    const passed = percentile(sorted, 95) <= targetMs;
    console.log(`  target p95 <= ${targetMs}ms: ${passed ? 'PASS' : 'FAIL'}`);
    return passed;
  }
  return failures === 0;
}

/** Runs `total` calls with at most `concurrency` in flight at once. */
async function drive(total, concurrency, task) {
  const latencies = [];
  let failures = 0;
  let issued = 0;

  async function worker() {
    while (issued < total) {
      issued += 1;
      const started = performance.now();
      try {
        await task();
        latencies.push(performance.now() - started);
      } catch {
        failures += 1;
      }
    }
  }

  const started = performance.now();
  await Promise.all(Array.from({ length: concurrency }, worker));
  return { latencies, failures, elapsedMs: performance.now() - started };
}

async function main() {
  console.log(`Load test against ${API}`);
  console.log(`concurrency ${CONCURRENCY}, up to ${REQUESTS} requests per scenario`);

  const doctor = await seedDoctorWithSlots();
  const patient = await seedPatient();
  const readHeaders = { Authorization: `Bearer ${patient.token}`, ...patient.headers };
  const slotUrl = `${API}/doctors/${doctor.doctorId}/slots?date=${doctor.date}`;

  // Scenario 1: slot fetch, the hot read path a patient hits while choosing a
  // time. This is the one NFR-8 puts a number on.
  const slots = await drive(REQUESTS, CONCURRENCY, async () => {
    const response = await fetch(slotUrl, { headers: readHeaders });
    if (!response.ok) throw new Error(String(response.status));
    await response.text();
  });
  const slotsPassed = report(
    'Slot fetch (NFR-8)',
    slots.latencies,
    slots.failures,
    slots.elapsedMs,
    SLOT_FETCH_TARGET_MS,
  );

  // Scenario 2: the doctor list, which is cached in Redis.
  const doctorList = await drive(REQUESTS, CONCURRENCY, async () => {
    const response = await fetch(`${API}/doctors?page=0&size=10`, { headers: readHeaders });
    if (!response.ok) throw new Error(String(response.status));
    await response.text();
  });
  report(
    'Doctor list (cached)',
    doctorList.latencies,
    doctorList.failures,
    doctorList.elapsedMs,
    SLOT_FETCH_TARGET_MS,
  );

  // Scenario 3: many patients booking at once. Most lose the race, which is
  // correct; what matters is that the endpoint stays responsive, never returns
  // an unexpected status, and never sells a slot twice.
  const listed = await (await fetch(slotUrl, { headers: readHeaders })).json();
  const available = listed.data.slots.filter((slot) => slot.status === 'AVAILABLE');
  console.log(`\n${available.length} slots available for the booking scenario`);

  const bookers = await Promise.all(Array.from({ length: CONCURRENCY }, seedPatient));
  let booked = 0;
  let rejected = 0;
  let cursor = 0;

  const booking = await drive(
    Math.min(REQUESTS, available.length * 3),
    CONCURRENCY,
    async () => {
      const booker = bookers[cursor % bookers.length];
      const slot = available[cursor % available.length];
      cursor += 1;
      const response = await fetch(`${API}/appointments`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${booker.token}`,
          ...booker.headers,
        },
        body: JSON.stringify({ doctorId: doctor.doctorId, slotId: slot.slotId, reason: 'Load test' }),
      });
      await response.text();
      if (response.status === 201) booked += 1;
      else if (response.status === 409) rejected += 1;
      else throw new Error(`unexpected ${response.status}`);
    },
  );
  report('Booking under contention', booking.latencies, booking.failures, booking.elapsedMs, null);
  console.log(`  ${booked} booked, ${rejected} rejected as already booked`);

  // The guarantee, checked from outside the application: no slot was sold
  // twice, and the number of successful bookings matches the slots now taken.
  const after = await (await fetch(slotUrl, { headers: readHeaders })).json();
  const nowBooked = after.data.slots.filter((slot) => slot.status === 'BOOKED').length;
  const stillFree = after.data.slots.filter((slot) => slot.status === 'AVAILABLE').length;
  const consistent = nowBooked === booked;
  console.log(`  slots now: ${nowBooked} booked, ${stillFree} available`);
  console.log(`  bookings match booked slots exactly: ${consistent ? 'PASS' : 'FAIL'}`);

  process.exit(slotsPassed && consistent && booking.failures === 0 ? 0 : 1);
}

main().catch((error) => {
  console.error('Load test failed to run:', error.message);
  process.exit(1);
});
