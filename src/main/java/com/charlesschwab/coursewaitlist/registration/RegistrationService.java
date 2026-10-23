package com.charlesschwab.coursewaitlist.registration;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Enrollment, drop and FIFO waitlist promotion for one course offering.
 *
 * <p>All state lives in two insertion-ordered sets guarded by a single lock, so every public
 * operation (including the drop + promotion pair) is atomic. See docs/DESIGN.md.
 */
public class RegistrationService {

	private final CourseOffering offering;
	private final LearnerRegistry learners;
	private final Set<String> enrolled = new LinkedHashSet<>();
	private final Set<String> waitlist = new LinkedHashSet<>();
	private final Object lock = new Object();

	public RegistrationService(CourseOffering offering, LearnerRegistry learners) {
		this.offering = offering;
		this.learners = learners;
	}

	public EnrollmentResult enroll(String learnerId) {
		if (isBlank(learnerId)) {
			return EnrollmentResult.of(EnrollmentStatus.INVALID_REQUEST, "learnerId is required");
		}
		synchronized (lock) {
			if (enrolled.contains(learnerId)) {
				return EnrollmentResult.of(EnrollmentStatus.ALREADY_ENROLLED, learnerId + " is already enrolled");
			}
			if (waitlist.contains(learnerId)) {
				return new EnrollmentResult(EnrollmentStatus.ALREADY_WAITLISTED,
						learnerId + " is already on the waitlist", positionOf(learnerId));
			}
			if (!learners.isKnown(learnerId)) {
				return EnrollmentResult.of(EnrollmentStatus.UNKNOWN_LEARNER, "Unknown learner " + learnerId);
			}
			if (!isEligible(learnerId)) {
				return EnrollmentResult.of(EnrollmentStatus.NOT_ELIGIBLE,
						learnerId + " has not completed prerequisites " + offering.prerequisites());
			}
			if (enrolled.size() < offering.capacity()) {
				enrolled.add(learnerId);
				return EnrollmentResult.of(EnrollmentStatus.ENROLLED, learnerId + " enrolled");
			}
			waitlist.add(learnerId);
			return new EnrollmentResult(EnrollmentStatus.WAITLISTED,
					"Course full; " + learnerId + " added to waitlist", waitlist.size());
		}
	}

	public DropResult drop(String learnerId) {
		if (isBlank(learnerId)) {
			return DropResult.of(DropStatus.INVALID_REQUEST, "learnerId is required");
		}
		synchronized (lock) {
			if (waitlist.remove(learnerId)) {
				return DropResult.of(DropStatus.DROPPED_FROM_WAITLIST, learnerId + " removed from waitlist");
			}
			if (!enrolled.remove(learnerId)) {
				return DropResult.of(DropStatus.NOT_REGISTERED, learnerId + " is neither enrolled nor waitlisted");
			}
			String promoted = promoteNext();
			String message = promoted == null
					? learnerId + " dropped; seat is open"
					: learnerId + " dropped; " + promoted + " promoted from waitlist";
			return new DropResult(DropStatus.DROPPED_ENROLLMENT, message, promoted);
		}
	}

	public OfferingSnapshot snapshot() {
		synchronized (lock) {
			return new OfferingSnapshot(offering.capacity(), new ArrayList<>(enrolled), new ArrayList<>(waitlist));
		}
	}

	/**
	 * Promotes the earliest waitlisted learner who is still eligible. Ineligible learners are
	 * skipped but keep their queue position. Caller must hold the lock.
	 */
	private String promoteNext() {
		Iterator<String> queue = waitlist.iterator();
		while (queue.hasNext() && enrolled.size() < offering.capacity()) {
			String candidate = queue.next();
			if (isEligible(candidate)) {
				queue.remove();
				enrolled.add(candidate);
				return candidate;
			}
		}
		return null;
	}

	private boolean isEligible(String learnerId) {
		return learners.completedCourses(learnerId).containsAll(offering.prerequisites());
	}

	private int positionOf(String learnerId) {
		return new ArrayList<>(waitlist).indexOf(learnerId) + 1;
	}

	private static boolean isBlank(String value) {
		return value == null || value.isBlank();
	}
}
