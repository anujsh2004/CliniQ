# Load Test Results

Against **NFR-8** (`product-description.md`): *"Response times for slot lookup and
booking should be low enough to support real-time UI feedback (target: sub-500ms
for slot fetch under normal load)."*

Reproduce with:

```bash
node scripts/load-test.mjs --concurrency 50 --requests 600
```

---

## Environment

These numbers come from a **development environment**, and should be read as a
shape rather than a production figure:

| | |
|---|---|
| Backend | `mvnw spring-boot:run` — dev mode, no JVM warmup pass, no production profile |
| Database | PostgreSQL 16 in Docker on the same machine |
| Redis / RabbitMQ | Docker, same machine |
| Client | Node, same machine — so no real network latency is included |
| Data | 3 doctors, one day of 15-minute slots (32 per doctor) |

A production deployment behind Nginx on separate hosts will look different in
both directions: real network latency added, contention for one laptop's CPU
removed.

---

## Results

### Slot fetch — the endpoint NFR-8 names

| Concurrency | p50 | p95 | p99 | max | Throughput | Verdict |
|---|---|---|---|---|---|---|
| 20 | 43ms | **80ms** | 100ms | 126ms | 418 req/s | ✅ PASS |
| 50 | 57ms | **132ms** | 155ms | 185ms | 718 req/s | ✅ PASS |
| 100 | 71ms | **171ms** | 192ms | 198ms | 1126 req/s | ✅ PASS |

Slot fetch improved at the same time, from 334ms to 171ms at 100 concurrent.
It was never cached, so this is the doctor-list traffic no longer competing for
the same connections and CPU.

**NFR-8 is met at every level tested**, with meaningful headroom: at 100
concurrent clients the p95 is 334ms against a 500ms target. Zero failures
throughout.

For scale, a single clinic with a few doctors will not see 100 concurrent slot
fetches; that is closer to a hundred patients all opening the booking screen in
the same second.

### Doctor list — measured with the cache, then without it

The first run showed the *cached* endpoint losing badly to the uncached slot
fetch, which is the opposite of the reason the cache existed. The cache was
removed (decision D25) and the same scenario re-run:

| Concurrency | p95 **with** Redis | p95 **without** | Throughput with → without |
|---|---|---|---|
| 50 | 177ms | **74ms** | 789 → 1229 req/s |
| 100 | **624ms** ❌ | **97ms** ✅ | 682 → 1489 req/s |

**Removing the cache made the endpoint 6.4× faster at 100 concurrent clients**,
and turned a failed 500ms target into a comfortable pass.

The explanation is that there was nothing to save. The doctor list is a trivial
query over a handful of rows; a Redis round trip and a deserialisation cost more
than the query they replace, and under load that overhead compounds. `tech-stack.md`
§3 nominates doctor reads for caching, which is sound reasoning about a clinic
with a large roster — it is simply not true of this data yet.

The Redis configuration, serializers and failure handling all remain in place,
so restoring the cache is a one-line change when the roster justifies it.

### Booking under contention

Many patients attempting to book from the same pool of slots at once.

| Concurrency | Attempts | Booked | Rejected 409 | p95 | Slots taken afterwards |
|---|---|---|---|---|---|
| 20 | 96 | 32 | 64 | 137ms | 32 |
| 50 | 96 | 32 | 64 | 219ms | 32 |
| 100 | 96 | 32 | 64 | 261ms | 32 |

**The no-double-booking guarantee held at every level.** 96 attempts against 32
slots produced exactly 32 bookings and 64 clean `409 SLOT_ALREADY_BOOKED`
rejections, with the slot table showing exactly 32 taken and 0 available
afterwards. No unexpected status codes, no failures.

This is the same guarantee the unit and integration tests cover, checked here
from outside the application and under load — the shape of failure this rules
out is one that only appears when the database is genuinely contended.

---

## Summary

| Check | Result |
|---|---|
| NFR-8: slot fetch p95 under 500ms | ✅ Passes to at least 100 concurrent clients |
| Booking stays responsive under contention | ✅ p95 261ms at 100 concurrent |
| No slot ever sold twice, under load | ✅ Exact match at every level |
| Doctor list under 500ms | ✅ 97ms at 100 concurrent, after removing the cache |
| Failures or unexpected statuses | ✅ None, in any scenario |

## What this does not cover

- **A production-shaped deployment.** No Nginx, no separate hosts, no real
  network. Re-run after Phase 6 before treating any of this as a production
  figure.
- **Sustained load.** Each scenario is a burst of a few hundred requests, not
  an hour of traffic, so nothing here says anything about memory growth,
  connection leaks or GC behaviour over time.
- **Payment and notification paths**, which depend on external providers that
  are still stubbed (D16, D19).
