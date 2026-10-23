package com.charlesschwab.coursewaitlist.registration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RegistrationServiceTest {

	private static final String C = "CS201";

	private LearnerRegistry learners;
	private RegistrationService service;

	@BeforeEach
	void setUp() {
		learners = new LearnerRegistry();
		service = new RegistrationService(C, 2, learners);
	}

	private void eligible(String... ids) {
		for (String id : ids) {
			learners.register(id, true);
		}
	}

	private void fillCourse() {
		eligible("a", "b");
		service.enroll("a");
		service.enroll("b");
	}

	private OfferingSnapshot snap() {
		return service.snapshot();
	}

	@Test
	void enrollsWhenCapacityRemainsAndLearnerEligible() {
		eligible("a");
		EnrollmentResult result = service.enroll("a");
		assertThat(result.status()).isEqualTo(EnrollmentStatus.ENROLLED);
		assertThat(snap().enrolled()).containsExactly("a");
		assertThat(snap().availableSeats()).isEqualTo(1);
	}

	@Test
	void eligibleLearnerJoinsWaitlistWhenFull() {
		fillCourse();
		eligible("c");
		EnrollmentResult result = service.enroll("c");
		assertThat(result.status()).isEqualTo(EnrollmentStatus.WAITLISTED);
		assertThat(result.waitlistPosition()).isEqualTo(1);
		assertThat(snap().enrolled()).containsExactly("a", "b");
		assertThat(snap().waitlist()).containsExactly("c");
	}

	@Test
	void duplicateEnrollmentCreatesNoDuplicateRecords() {
		eligible("a");
		service.enroll("a");
		assertThat(service.enroll("a").status()).isEqualTo(EnrollmentStatus.ALREADY_ENROLLED);
		assertThat(snap().enrolled()).containsExactly("a");
		assertThat(snap().waitlist()).isEmpty();
	}

	@Test
	void enrolledLearnerRequestingAgainWhenFullIsNotWaitlisted() {
		fillCourse();
		assertThat(service.enroll("a").status()).isEqualTo(EnrollmentStatus.ALREADY_ENROLLED);
		assertThat(snap().waitlist()).isEmpty();
	}

	@Test
	void duplicateWaitlistRequestKeepsOriginalPosition() {
		fillCourse();
		eligible("c", "d");
		service.enroll("c");
		service.enroll("d");
		EnrollmentResult dup = service.enroll("c");
		assertThat(dup.status()).isEqualTo(EnrollmentStatus.ALREADY_WAITLISTED);
		assertThat(dup.waitlistPosition()).isEqualTo(1);
		assertThat(snap().waitlist()).containsExactly("c", "d");
	}

	@Test
	void dropPromotesEarliestWaitlistedLearner() {
		fillCourse();
		eligible("c", "d", "e");
		service.enroll("c");
		service.enroll("d");
		service.enroll("e");

		DropResult first = service.drop("a");
		assertThat(first.status()).isEqualTo(DropStatus.DROPPED_ENROLLMENT);
		assertThat(first.promotedLearnerId()).isEqualTo("c");
		assertThat(service.drop("b").promotedLearnerId()).isEqualTo("d");

		assertThat(snap().enrolled()).containsExactly("c", "d");
		assertThat(snap().waitlist()).containsExactly("e");
	}

	@Test
	void dropWithEmptyWaitlistLeavesSeatOpen() {
		fillCourse();
		DropResult result = service.drop("a");
		assertThat(result.promotedLearnerId()).isNull();
		assertThat(snap().availableSeats()).isEqualTo(1);
	}

	@Test
	void reEnrollingAfterDropGoesToBackOfQueueWhenFull() {
		fillCourse();
		eligible("c");
		service.enroll("c");
		service.drop("a"); // c promoted; enrolled = b, c

		EnrollmentResult result = service.enroll("a");
		assertThat(result.status()).isEqualTo(EnrollmentStatus.WAITLISTED);
		assertThat(snap().enrolled()).containsExactly("b", "c");
		assertThat(snap().waitlist()).containsExactly("a");
	}

	@Test
	void ineligibleLearnerIsNeverEnrolledOrWaitlisted() {
		learners.register("x", false);
		assertThat(service.enroll("x").status()).isEqualTo(EnrollmentStatus.NOT_ELIGIBLE);

		fillCourse();
		assertThat(service.enroll("x").status()).isEqualTo(EnrollmentStatus.NOT_ELIGIBLE);
		assertThat(snap().enrolled()).containsExactly("a", "b");
		assertThat(snap().waitlist()).isEmpty();
	}

	@Test
	void learnerWhoLostEligibilityIsRemovedAtPromotionAndNextEligibleIsPromoted() {
		fillCourse();
		eligible("c", "d", "e");
		service.enroll("c");
		service.enroll("d");
		service.enroll("e");
		// c's eligibility is revoked after joining the waitlist
		learners.register("c", false);

		DropResult result = service.drop("a");
		assertThat(result.promotedLearnerId()).isEqualTo("d");
		assertThat(snap().enrolled()).containsExactly("b", "d");
		assertThat(snap().waitlist()).containsExactly("e");

		// c was removed, so regaining eligibility does not restore the place
		learners.register("c", true);
		assertThat(service.drop("b").promotedLearnerId()).isEqualTo("e");
	}

	@Test
	void allWaitlistedNoLongerEligibleAreRemovedAndSeatStaysOpen() {
		fillCourse();
		eligible("c");
		service.enroll("c");
		learners.register("c", false);

		DropResult result = service.drop("a");
		assertThat(result.promotedLearnerId()).isNull();
		assertThat(snap().enrolled()).containsExactly("b");
		assertThat(snap().waitlist()).isEmpty();
	}

	@Test
	void waitlistedLearnerCanDropWithoutAffectingEnrollment() {
		fillCourse();
		eligible("c", "d");
		service.enroll("c");
		service.enroll("d");
		assertThat(service.drop("c").status()).isEqualTo(DropStatus.DROPPED_FROM_WAITLIST);
		assertThat(snap().enrolled()).containsExactly("a", "b");
		assertThat(snap().waitlist()).containsExactly("d");
	}

	@Test
	void repeatedAndUnregisteredDropsAreRejectedWithoutSideEffects() {
		fillCourse();
		eligible("c");
		service.enroll("c");
		service.drop("a"); // c promoted

		OfferingSnapshot before = snap();
		assertThat(service.drop("a").status()).isEqualTo(DropStatus.NOT_REGISTERED);
		assertThat(service.drop("nobody").status()).isEqualTo(DropStatus.NOT_REGISTERED);
		assertThat(snap()).isEqualTo(before);
	}

	@Test
	void invalidRequestsAreRejected() {
		assertThat(service.enroll(null).status()).isEqualTo(EnrollmentStatus.INVALID_REQUEST);
		assertThat(service.enroll("  ").status()).isEqualTo(EnrollmentStatus.INVALID_REQUEST);
		assertThat(service.enroll("ghost").status()).isEqualTo(EnrollmentStatus.UNKNOWN_LEARNER);
		assertThat(service.drop(null).status()).isEqualTo(DropStatus.INVALID_REQUEST);
		assertThat(service.drop(" ").status()).isEqualTo(DropStatus.INVALID_REQUEST);
		assertThat(snap().enrolled()).isEmpty();
		assertThat(snap().waitlist()).isEmpty();
	}

	@Test
	void constructorRejectsInvalidOffering() {
		assertThatThrownBy(() -> new RegistrationService(" ", 1, learners)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new RegistrationService(C, 0, learners)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void concurrentEnrollmentsNeverExceedCapacityOrDuplicate() throws Exception {
		int capacity = 10;
		int learnersCount = 100;
		service = new RegistrationService(C, capacity, learners);
		List<Callable<Object>> tasks = new ArrayList<>();
		for (int i = 0; i < learnersCount; i++) {
			String id = "l" + i;
			eligible(id);
			// each learner submits twice to exercise duplicate handling under contention
			tasks.add(() -> service.enroll(id));
			tasks.add(() -> service.enroll(id));
		}
		runConcurrently(tasks);

		OfferingSnapshot snap = service.snapshot();
		assertThat(snap.enrolled()).hasSize(capacity).doesNotHaveDuplicates();
		assertThat(snap.waitlist()).hasSize(learnersCount - capacity).doesNotHaveDuplicates();
		assertThat(snap.enrolled()).doesNotContainAnyElementsOf(snap.waitlist());
	}

	@Test
	void concurrentDropsPromoteEachWaitlistedLearnerOnceInOrder() throws Exception {
		int capacity = 10;
		service = new RegistrationService(C, capacity, learners);
		List<String> seated = new ArrayList<>();
		List<String> queued = new ArrayList<>();
		for (int i = 0; i < capacity; i++) {
			seated.add("s" + i);
			eligible("s" + i);
			service.enroll("s" + i);
		}
		for (int i = 0; i < capacity; i++) {
			queued.add("w" + i);
			eligible("w" + i);
			service.enroll("w" + i);
		}
		List<Callable<Object>> tasks = new ArrayList<>();
		for (String id : seated) {
			tasks.add(() -> service.drop(id));
			tasks.add(() -> service.drop(id)); // repeated drop must be a no-op
		}
		List<Object> results = runConcurrently(tasks);

		long promotions = results.stream().map(r -> ((DropResult) r).promotedLearnerId())
				.filter(p -> p != null).count();
		assertThat(promotions).isEqualTo(capacity);
		OfferingSnapshot snap = service.snapshot();
		assertThat(snap.enrolled()).containsExactlyInAnyOrderElementsOf(queued);
		assertThat(snap.waitlist()).isEmpty();
	}

	@Test
	void racingEnrollsCannotStealSeatFreedForWaitlistHead() throws Exception {
		// Without an atomic drop+promote, an enroll could slip in between the seat opening and the promotion.
		for (int round = 0; round < 50; round++) {
			service = new RegistrationService(C, 1, learners);
			eligible("seated", "head");
			service.enroll("seated");
			service.enroll("head");

			List<Callable<Object>> tasks = new ArrayList<>();
			tasks.add(() -> service.drop("seated"));
			for (int i = 0; i < 20; i++) {
				String id = "new" + round + "_" + i;
				eligible(id);
				tasks.add(() -> service.enroll(id));
			}
			runConcurrently(tasks);

			OfferingSnapshot snap = service.snapshot();
			assertThat(snap.enrolled()).containsExactly("head");
			assertThat(snap.waitlist()).hasSize(20).doesNotContain("head").doesNotHaveDuplicates();
		}
	}

	private List<Object> runConcurrently(List<Callable<Object>> tasks) throws Exception {
		ExecutorService pool = Executors.newFixedThreadPool(16);
		CountDownLatch start = new CountDownLatch(1);
		try {
			List<Future<Object>> futures = new ArrayList<>();
			for (Callable<Object> task : tasks) {
				futures.add(pool.submit(() -> {
					start.await();
					return task.call();
				}));
			}
			start.countDown();
			List<Object> results = new ArrayList<>();
			for (Future<Object> f : futures) {
				results.add(f.get());
			}
			return results;
		}
		finally {
			pool.shutdownNow();
		}
	}
}
