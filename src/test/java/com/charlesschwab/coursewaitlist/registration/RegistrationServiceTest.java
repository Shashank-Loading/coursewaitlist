package com.charlesschwab.coursewaitlist.registration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RegistrationServiceTest {

	private static final String PREREQ = "CS101";

	private LearnerRegistry learners;
	private RegistrationService service;

	@BeforeEach
	void setUp() {
		learners = new LearnerRegistry();
		service = new RegistrationService(new CourseOffering("CS201", 2, Set.of(PREREQ)), learners);
	}

	private void eligible(String... ids) {
		for (String id : ids) {
			learners.register(id, Set.of(PREREQ));
		}
	}

	private void fillCourse() {
		eligible("a", "b");
		service.enroll("a");
		service.enroll("b");
	}

	@Test
	void enrollsWhenCapacityRemainsAndPrerequisiteMet() {
		eligible("a");
		EnrollmentResult result = service.enroll("a");
		assertThat(result.status()).isEqualTo(EnrollmentStatus.ENROLLED);
		assertThat(service.snapshot().enrolled()).containsExactly("a");
		assertThat(service.snapshot().availableSeats()).isEqualTo(1);
	}

	@Test
	void eligibleLearnerJoinsWaitlistWhenFull() {
		fillCourse();
		eligible("c");
		EnrollmentResult result = service.enroll("c");
		assertThat(result.status()).isEqualTo(EnrollmentStatus.WAITLISTED);
		assertThat(result.waitlistPosition()).isEqualTo(1);
		assertThat(service.snapshot().enrolled()).containsExactly("a", "b");
		assertThat(service.snapshot().waitlist()).containsExactly("c");
	}

	@Test
	void duplicateEnrollmentCreatesNoDuplicateRecords() {
		eligible("a");
		service.enroll("a");
		assertThat(service.enroll("a").status()).isEqualTo(EnrollmentStatus.ALREADY_ENROLLED);
		assertThat(service.snapshot().enrolled()).containsExactly("a");
		assertThat(service.snapshot().waitlist()).isEmpty();
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
		assertThat(service.snapshot().waitlist()).containsExactly("c", "d");
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
		DropResult second = service.drop("b");
		assertThat(second.promotedLearnerId()).isEqualTo("d");

		assertThat(service.snapshot().enrolled()).containsExactly("c", "d");
		assertThat(service.snapshot().waitlist()).containsExactly("e");
	}

	@Test
	void dropWithEmptyWaitlistLeavesSeatOpen() {
		fillCourse();
		DropResult result = service.drop("a");
		assertThat(result.promotedLearnerId()).isNull();
		assertThat(service.snapshot().availableSeats()).isEqualTo(1);
	}

	@Test
	void prerequisiteFailureIsNotEnrolledOrWaitlisted() {
		learners.register("x", Set.of());
		assertThat(service.enroll("x").status()).isEqualTo(EnrollmentStatus.NOT_ELIGIBLE);

		fillCourse();
		assertThat(service.enroll("x").status()).isEqualTo(EnrollmentStatus.NOT_ELIGIBLE);
		assertThat(service.snapshot().waitlist()).isEmpty();
	}

	@Test
	void ineligibleLearnerAtFrontIsSkippedButKeepsPosition() {
		fillCourse();
		eligible("c", "d");
		service.enroll("c");
		service.enroll("d");
		// c's record changes after joining (e.g. a completed course is revoked)
		learners.register("c", Set.of());

		DropResult result = service.drop("a");
		assertThat(result.promotedLearnerId()).isEqualTo("d");
		assertThat(service.snapshot().enrolled()).containsExactly("b", "d");
		assertThat(service.snapshot().waitlist()).containsExactly("c");

		// once eligible again, c is first in line for the next seat
		learners.register("c", Set.of(PREREQ));
		assertThat(service.drop("b").promotedLearnerId()).isEqualTo("c");
	}

	@Test
	void allWaitlistedIneligibleLeavesSeatOpenAndQueueIntact() {
		fillCourse();
		eligible("c");
		service.enroll("c");
		learners.register("c", Set.of());

		DropResult result = service.drop("a");
		assertThat(result.promotedLearnerId()).isNull();
		assertThat(service.snapshot().enrolled()).containsExactly("b");
		assertThat(service.snapshot().waitlist()).containsExactly("c");
	}

	@Test
	void waitlistedLearnerCanDropWithoutAffectingEnrollment() {
		fillCourse();
		eligible("c", "d");
		service.enroll("c");
		service.enroll("d");
		assertThat(service.drop("c").status()).isEqualTo(DropStatus.DROPPED_FROM_WAITLIST);
		assertThat(service.snapshot().enrolled()).containsExactly("a", "b");
		assertThat(service.snapshot().waitlist()).containsExactly("d");
	}

	@Test
	void repeatedAndUnknownDropsAreRejectedWithoutSideEffects() {
		fillCourse();
		eligible("c");
		service.enroll("c");
		service.drop("a"); // c promoted

		OfferingSnapshot before = service.snapshot();
		assertThat(service.drop("a").status()).isEqualTo(DropStatus.NOT_REGISTERED);
		assertThat(service.drop("nobody").status()).isEqualTo(DropStatus.NOT_REGISTERED);
		assertThat(service.snapshot()).isEqualTo(before);
	}

	@Test
	void invalidRequestsAreRejected() {
		assertThat(service.enroll(null).status()).isEqualTo(EnrollmentStatus.INVALID_REQUEST);
		assertThat(service.enroll("  ").status()).isEqualTo(EnrollmentStatus.INVALID_REQUEST);
		assertThat(service.enroll("ghost").status()).isEqualTo(EnrollmentStatus.UNKNOWN_LEARNER);
		assertThat(service.drop(null).status()).isEqualTo(DropStatus.INVALID_REQUEST);
		assertThat(service.drop("").status()).isEqualTo(DropStatus.INVALID_REQUEST);
		assertThat(service.snapshot().enrolled()).isEmpty();
		assertThat(service.snapshot().waitlist()).isEmpty();
	}

	@Test
	void offeringRejectsInvalidCapacity() {
		assertThatThrownBy(() -> new CourseOffering("CS201", 0, Set.of()))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void concurrentEnrollmentsNeverExceedCapacityOrDuplicate() throws Exception {
		int capacity = 10;
		int learnersCount = 100;
		service = new RegistrationService(new CourseOffering("CS201", capacity, Set.of(PREREQ)), learners);
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
		service = new RegistrationService(new CourseOffering("CS201", capacity, Set.of(PREREQ)), learners);
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
