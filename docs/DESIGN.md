# Course Waitlist – Design Specification

## Scope and service boundary
One course offering: enroll, drop, FIFO waitlist promotion. The starter was an empty Spring Boot 4 / Java 21
shell (no data model, web layer, or DB), so this uses the in-memory fallback. No frontend, catalog, payments,
identity, multi-term, or database. The offering id and capacity come from configuration
(`registration.course-id`, `registration.capacity`; defaults `CS201` and `1`).

`RegistrationService` (package `com.charlesschwab.coursewaitlist.registration`) is the boundary:

| Method | Result |
|---|---|
| `enroll(learnerId)` | `EnrollmentResult(status, message, waitlistPosition)` |
| `drop(learnerId)` | `DropResult(status, message, promotedLearnerId)` |
| `snapshot()` | immutable `OfferingSnapshot(courseId, capacity, enrolled, waitlist)` |

Supporting types: `LearnerRegistry` (learnerId → `eligible` boolean, in-memory), `RegistrationConfig` (Spring
wiring), `DemoRunner` (optional console walkthrough), and `controller/RegistrationController` (thin REST adapter
for testing).

Status codes — enroll: `ENROLLED, WAITLISTED, ALREADY_ENROLLED, ALREADY_WAITLISTED, NOT_ELIGIBLE, UNKNOWN_LEARNER,
INVALID_REQUEST`. Drop: `DROPPED_ENROLLMENT, DROPPED_FROM_WAITLIST, NOT_REGISTERED, INVALID_REQUEST`. Business
outcomes are returned as results, not exceptions; only an invalid offering configuration throws.

## API design (REST, for testing only)
| Method & path | Maps to | HTTP result |
|---|---|---|
| `POST /api/learners` `{learnerId, eligible}` | `LearnerRegistry.register` (test helper) | 201 / 400 |
| `POST /api/enrollments` `{learnerId}` | `enroll` | 201 enrolled or waitlisted, 409 duplicate, 422 not eligible, 404 unknown learner, 400 invalid |
| `DELETE /api/enrollments/{learnerId}` | `drop` | 200 / 404 not registered / 400 |
| `GET /api/offering` | `snapshot` | 200 |

The controller only translates result statuses to HTTP codes; all rules live in `RegistrationService`.

## Data model
- `enrolled`: `LinkedHashSet<String>` (set ⇒ no duplicate records).
- `waitlist`: `LinkedHashSet<String>` (insertion order ⇒ FIFO; set ⇒ no duplicates).
- `capacity`: fixed at construction (≥ 1).

## Invariants (hold after every operation)
1. `|enrolled| ≤ capacity`.
2. A learner is in at most one of `enrolled` / `waitlist`, at most once.
3. Everyone enrolled was eligible at the time of enrollment/promotion.
4. Waitlist order is join order; promotion never reorders remaining entries.
5. Rejected requests (invalid, duplicate, unknown, ineligible, repeated drop) change no state.

## Waitlist-promotion rule
When an enrolled learner drops, the seat is offered to the waitlist in join order (FIFO). Each candidate is removed
from the queue and checked: if eligible they are enrolled and promotion stops; if not, they are discarded and the
next candidate is tried. If the queue empties without an eligible learner, the seat stays open. Promotion runs in
the same locked step as the drop.

## Assumptions and policies
- **Prerequisite assumption:** each learner has an `eligible` true/false flag in `LearnerRegistry`; `true` means the
  learner meets the course prerequisite. A learner must be
  registered (`UNKNOWN_LEARNER` otherwise). How the flag is determined (grades, transcripts) is outside this slice.
- **Waitlist assumption (single policy: skip and remove):**
  - *On request:* an ineligible learner cannot enroll or join the waitlist; they get `NOT_ELIGIBLE`, whether or not
    the course is full.
  - *At promotion:* eligibility is re-checked when a seat opens. A waitlisted learner who is no longer eligible is
    skipped **and removed** from the waitlist (not kept waiting), and the next eligible learner is promoted. If no
    eligible learner remains, the seat stays open.
  - A removed learner who becomes eligible again must enroll again and goes to the back of the queue.
- Dropping while waitlisted removes the learner from the queue (`DROPPED_FROM_WAITLIST`); no promotion occurs.
- Re-enrolling after a drop is a fresh request (goes to the back of the queue if full).
- Seats open only via drop; capacity is fixed.
- A duplicate request while waitlisted reports the current position and does not requeue.

## Concurrency
Single in-memory process; one private lock guards both collections. The critical section in `enroll` is the
membership checks, capacity check and insert; in `drop` it is the removal plus the promotion step. Each runs inside
`synchronized(lock)`, so capacity checks and mutations are atomic and a freed seat cannot be taken by a racing
enroll before the waitlist head is promoted. Input validation and result-message building happen outside the lock,
and `snapshot()` copies both collections under the lock. Chosen over concurrent
collections because the invariant spans two collections. One offering ⇒ no lock-ordering concerns.

## Tradeoffs / persistence
- The eligibility policy is deliberately hard-coded (skip and remove); no configurable policy engine, per the brief.
- Eligibility is a plain boolean per learner rather than a prerequisite/grade model, keeping the slice small.
- Removed ineligible learners are dropped silently (no notification), and lose their queue position.
- One offering only; capacity is set at startup.
In-memory only: state is lost on restart. A persisted version would replace the lock with a DB transaction
(row lock on the offering, unique constraints on (offering, learner), a sequence for queue order).
`positionOf` is O(n) and promotion scans the queue; acceptable for one offering.
