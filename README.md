# Course Waitlist

Enrollment, drop, and FIFO waitlist promotion for one course offering (in-memory, Spring Boot 4 / Java 21+).

## Setup and run
Requires JDK 21+ (tested on 25). The Maven wrapper is included.

```bash
./mvnw test                                        # run all tests (20)
./mvnw test -Dtest=RegistrationServiceTest         # service logic and concurrency tests only
./mvnw test -Dtest=RegistrationControllerTest      # REST flow test only
./mvnw spring-boot:run -Dspring-boot.run.arguments="--registration.demo=true --registration.capacity=2"   # console demo
./mvnw spring-boot:run                             # start the REST server on :8080
```
If port 8080 is busy, add `-Dspring-boot.run.arguments=--server.port=8089`.

The single offering is configured with `registration.course-id` (default `CS201`) and `registration.capacity` (default `1`).

## REST endpoints (for testing)
Start with `./mvnw spring-boot:run`, then (port 8080):

| Method & path | Purpose | Success / error codes |
|---|---|---|
| `POST /api/learners` `{"learnerId","eligible":true}` | test helper: create/replace a learner and eligibility flag; returns `{learnerId, eligible, message}` | 201 / 400 |
| `POST /api/enrollments` `{"learnerId"}` | enroll or waitlist | 201 / 409 duplicate, 422 not eligible, 404 unknown learner, 400 invalid |
| `DELETE /api/enrollments/{learnerId}` | drop (promotes next eligible) | 200 / 404 not registered |
| `GET /api/offering` | course id, capacity, enrolled, waitlist | 200 |

```bash
H='Content-Type: application/json'
curl -X POST localhost:8080/api/learners -H "$H" -d '{"learnerId":"alice","eligible":true}'
curl -X POST localhost:8080/api/enrollments -H "$H" -d '{"learnerId":"alice"}'
curl localhost:8080/api/offering
```

## Documents
- [Postman collection](docs/coursewaitlist.postman_collection.json) – requests for the REST endpoints (server on `localhost:8080`)
- [Design specification](docs/DESIGN.md) – boundary, invariants, policies, concurrency
- [AI prompt and use record](docs/AI_USE_RECORD.md)
- **Rubric scorecard:** `Course-Registration-Rubric-Score.md` (the AI-Assisted scorecard named in the brief) was not provided and could not be accessed, so the scorecard step was skipped. No file with that name is included, and no scores or criteria were invented.

## Limitations / follow-up risks
- **In-memory only:** a restart loses all enrollments, waitlist entries and learners. A persisted version needs a DB
  transaction with a row lock on the offering and a unique constraint on (offering, learner).
- **Single process:** the lock only protects one JVM; running two instances would break the capacity invariant.
- **Eligibility is a plain flag** set outside the service (via `LearnerRegistry` / the test endpoint); there is no
  grade or prerequisite model, and no check that the flag is changed through a trusted path.
- **Silent removal:** a waitlisted learner who is no longer eligible at promotion is removed without notification and
  loses their place if they regain eligibility.
- **Seats open only through drop:** capacity is fixed at startup, so raising it would not promote anyone.
- **Unbounded waitlist**, and `positionOf` (used only for duplicate waitlist requests) scans the queue in O(n); promotion is amortized O(1): it only scans past learners who became ineligible while waiting, and each of them is removed as it is passed. Fine for one offering.
- **Concurrency tests are probabilistic:** they demonstrate, not prove, race-freedom; correctness rests on the single lock.
- **REST layer is a test aid:** no authentication, and the learner endpoint lets anyone set eligibility.
- Only one offering is supported, per the scope.
