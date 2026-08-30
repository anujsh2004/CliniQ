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
| 50 | 86ms | **169ms** | 218ms | 251ms | 509 req/s | ✅ PASS |
| 100 | 168ms | **334ms** | 428ms | 566ms | 541 req/s | ✅ PASS |

**NFR-8 is met at every level tested**, with meaningful headroom: at 100
concurrent clients the p95 is 334ms against a 500ms target. Zero failures
throughout.

For scale, a single clinic with a few doctors will not see 100 concurrent slot
fetches; that is closer to a hundred patients all opening the booking screen in
the same second.

### Doctor list — cached in Redis

| Concurrency | p50 | p95 | p99 | Throughput | Verdict |
|---|---|---|---|---|---|
| 20 | 22ms | 79ms | 113ms | 713 req/s | ✅ |
| 50 | 47ms | 177ms | 253ms | 789 req/s | ✅ |
| 100 | 81ms | **624ms** | 699ms | 682 req/s | ❌ over 500ms |

**The cached endpoint is slower than the uncached one at high concurrency.**
That is worth stating plainly, because it is the opposite of the reason the
cache was added.

The likely explanation is that at this data size there is nothing to save. The
doctor list is a trivial query over three rows; going to Redis adds a network
hop and a deserialisation on every request, and at 100 concurrent clients that
costs more than the query it replaces. The cache should start paying for itself
as the doctor roster grows and the database has real work to do — but on today's
data it is overhead, not optimisation.

**This is recorded as decision D25 in `docs/open-decisions.md`** rather than
acted on unilaterally: the fix is either to drop the cache for this endpoint or
to keep it in anticipation of scale, and that is a judgement about where the
product is going.

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
| Cached doctor list under 500ms | ❌ Fails at 100 concurrent — see D25 |
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
