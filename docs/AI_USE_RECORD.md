# AI Prompt and Use Record

**Tool:** GitHub Copilot agent in VS Code, used as an engineering assistant for planning, implementation, test
generation, and documentation. No credentials or sensitive data were included in any prompt.

**Candid summary:** the AI agent did essentially all of the first-draft work (inspect starter, choose design, write
code and tests, run them, draft docs). The human's prompts were short; several changed the design, and two of the
human's decisions overrode the AI's choices (rows 8 and 9). Prompts below are quoted or closely abbreviated, not
reconstructed into longer ones.

## Prompts, AI responses, outcomes, and verification

| # | Prompt (human) | AI response (summary) | Outcome | Files / decisions informed | Verification |
|---|---|---|---|---|---|
| 1 | "Prepare yourself for finishing a project... plan it and execute it properly." | Replied that it was ready. | n/a | none | n/a |
| 2 | Full assessment brief + "follow the steps and finish the project." | Inspected the starter (empty Spring Boot 4 / Java 21 shell, no model/web/DB). Chose the in-memory fallback: one lock, `LinkedHashSet`s for enrolled and waitlist, result objects instead of exceptions, a skip policy, 15 service tests incl. 2 concurrency tests. Wrote code, tests, DESIGN, README, this record. | **Accepted** by the human without changes. AI **edited** one of its own tests on review: `duplicateEnrollmentCreatesNoDuplicateRecords` was convoluted and nearly tautological, so it was replaced with a direct check. | `RegistrationService`, `LearnerRegistry`, `RegistrationServiceTest`, `docs/DESIGN.md` | `./mvnw test` passed; console demo output matched expectations (see evidence below). |
| 3 | "did every aspect of this document is taken care of?" | Said mostly yes; listed gaps: rubric file not provided, record not a faithful transcript, two missing tests (re-enroll after drop, enrolled-requesting-again when full). | **Accepted**; AI added the two tests and rewrote this record. | `RegistrationServiceTest`, this file | Tests re-run, all passing. |
| 4 | "create rest endpoints for testing" | Added `spring-boot-starter-webmvc` (+ test starter), a thin controller mapping result statuses to HTTP codes, and a MockMvc test. | **Accepted.** This **reverses** the AI's earlier decision to treat REST as out of scope; REST is kept as a test aid only. | `pom.xml`, `controller/RegistrationController`, `RegistrationControllerTest`, README | MockMvc test; server started and exercised with curl, then stopped. |
| 5 | "put these controllers in controller folder" | Moved controller and its test to the `controller` package; imported the public types. | **Accepted.** | `controller/*` | Clean build + tests passed. |
| 6 | "change the eligibility logic... use something like true or false" | Replaced the completed-courses prerequisite with a per-learner `eligible` boolean in `LearnerRegistry`. | **Accepted** (simpler to explain and test). | `LearnerRegistry`, service, tests, DESIGN | Tests updated and passing. |
| 7 | "make the course as well dynamic, method should accept course as well" | Rewrote the service for many offerings keyed by `courseId` (`createCourse`, per-offering locks, `UNKNOWN_COURSE`), updated controller, tests and docs. | **Accepted at the time; later reverted** (row 10). | service, controller, tests | 22 tests passed at that point. |
| 8 | Requests to move `courseId` into request bodies, fix capacity at 1, drop `courseId` from learner creation, then move it into DELETE too, then "revert the change just made". | Applied each change; kept `courseId` in the DELETE path again after the revert. | **Accepted** except the DELETE-with-body change, **rejected** by the human and reverted. | controller, controller test, README | Tests passed after each step (22). |
| 9 | "eligible user must be able to join the queue. But while promotion he should be promoted only if the learner is eligible else this learner should be removed", then clarified "i am not allowing ineligible user to enter into queue". | First implemented "ineligible may join a full waitlist" and two tests failed, because the AI had misread the second message. Corrected: ineligible learners are rejected on entry; at promotion a learner who has become ineligible is **removed** and the next eligible one is promoted. | **Human decision replaced the AI's original skip-and-retain-position policy.** The AI's first reading of the request was wrong and was corrected after the failing tests. | `RegistrationService.enroll` / `promoteNext`, 3 rewritten tests, DESIGN "Assumptions and policies" | Failing tests exposed the misreading; after the fix, all passed. |
| 10 | Scope boundary ("Implement only one offering's...") → "make the change according to scope" → chose "Revert to a single fixed offering". | Pointed out that multi-course goes past "one offering"; on the human's choice, removed `createCourse`, `CourseResult`/`CourseStatus`, `UNKNOWN_COURSE`, and the course-scoped API. Service now takes `(courseId, capacity, learners)` from config (`registration.course-id`, `registration.capacity`, defaults `CS201` / `1`). | **Accepted** (human chose it from two options). | whole service, controller, tests, README, DESIGN | `./mvnw -q clean test` passed (see evidence below). |
| 11 | Questions on what the policy/scope lines in the brief mean | Explained; no code change. DESIGN gained a promotion-rule section, API table and tradeoffs. | **Accepted.** | `docs/DESIGN.md` | Doc-only. |
| 12 | "please analyse the critical sections in those two methods and apply lock there itself" | Identified the critical sections: in `enroll`, the membership checks, capacity check and insert; in `drop`, removal plus promotion. Moved result-message building outside the lock via a status `switch`, and kept the eligibility lookup inside so the `ALREADY_ENROLLED` vs `NOT_ELIGIBLE` precedence is unchanged. | **Accepted.** | `RegistrationService.enroll/drop/snapshot/positionOf`, DESIGN "Concurrency" | Full suite passed (concurrency tests included). |
| 13 | "If the starter uses concurrent requests... add a focused concurrency test where practical. pls handle this" | Noted the starter has no locking to preserve; added `racingEnrollsCannotStealSeatFreedForWaitlistHead` (drop vs 20 racing enrolls, 50 rounds). | **Accepted.** | `RegistrationServiceTest`, README, this file | 20 tests pass; replacing the lock with a per-call `new Object()` made the new test and the concurrent-drops test fail, then the original was restored. |

## Review notes on AI output
- Policy consistency: ineligible learners are rejected on request and removed at promotion. Covered by
  `ineligibleLearnerIsNeverEnrolledOrWaitlisted`,
  `learnerWhoLostEligibilityIsRemovedAtPromotionAndNextEligibleIsPromoted`, and
  `allWaitlistedNoLongerEligibleAreRemovedAndSeatStaysOpen`.
- Eligibility is re-evaluated at promotion time (not cached at join), which is what makes the removal rule
  observable; tests simulate a change with `LearnerRegistry.register`.
- Concurrency tests submit duplicates and repeated drops from 16 threads released by a latch and assert exact counts,
  no duplicates, and no overlap between enrolled and waitlist.
- **Human review:**
  1. Created REST endpoints to check that the functionality works as expected.
  2. Tested those endpoints against all the scenarios in the provided document, to confirm the functionality is
     correct.
  3. Note: I initially thought of adding courses dynamically, but on re-reading the document I understood that only
     one offering is required, so I changed the implementation to a single offering.
  4. Checked the prerequisite and waitlist assumptions against the document. Ineligible learners are not allowed to
     enroll or join the waitlist, and this rule is applied consistently everywhere (enrollment and promotion).
  5. Reviewed the generated code and replaced some of it with existing library methods instead of custom ones, for
     example `StringUtils.isBlank()` (Apache Commons Lang) in place of the custom `isBlank()` helper.
  6. Verified the locking scenarios and applied the lock only around the critical sections of `enroll` and `drop`
     (the check-and-mutate step, and removal plus promotion) instead of around the entire methods.
  7. Re-ran the full test suite myself with `./mvnw test` and confirmed it passes.
  8. Added a Postman collection for testing the REST endpoints (`docs/coursewaitlist.postman_collection.json`).

## Verification evidence
- `./mvnw -q clean test` on the current code → 20 tests, 0 failures: 18 in `RegistrationServiceTest`, 1 in
  `RegistrationControllerTest` (HTTP flow), 1 context-load test.
- Console demo, run on the current code with
  `./mvnw spring-boot:run -Dspring-boot.run.arguments="--registration.demo=true --spring.main.web-application-type=none --registration.capacity=2"`:
  alice/bob ENROLLED; carol/dave WAITLISTED (positions 1, 2); erin NOT_ELIGIBLE; duplicate alice ALREADY_ENROLLED;
  drop alice → carol promoted; repeat drop alice → NOT_REGISTERED; final `enrolled=[bob, carol], waitlist=[dave]`.
- README curl examples were re-run against a fresh server on port 8089 after the refactor: alice ENROLLED, bob
  WAITLISTED (position 1), drop alice → bob promoted, final `enrolled=[bob], waitlist=[]`.
- Lock check: temporarily replacing the shared lock with a fresh `new Object()` per call made the concurrency tests
  fail (`racingEnrollsCannotStealSeatFreedForWaitlistHead`, `concurrentDropsPromoteEachWaitlistedLearnerOnceInOrder`);
  the original code was restored and the full suite passes again.
- Limitation: concurrency tests are probabilistic; they demonstrate but do not prove absence of races (correctness
  rests on the single-lock design).

## Known gaps
- `Course-Registration-Rubric-Score.md` was not available, so no scorecard has been completed.
