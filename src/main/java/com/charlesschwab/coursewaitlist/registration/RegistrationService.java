package com.charlesschwab.coursewaitlist.registration;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;

/**
 * Enrollment, drop and FIFO waitlist promotion for a single course offering.
 *
 * <p>
 * The enrolled set and the {@link WaitlistQueue} are guarded by one lock. The lock covers only the
 * check-and-mutate step of enroll/drop (including the drop + promotion pair); input validation and result
 * message building happen outside it. See docs/DESIGN.md.
 */
public class RegistrationService {

	private final String courseId;
	private final int capacity;
	private final LearnerRegistry learners;
	private final Object lock = new Object();
	private final Set<String> enrolled = new LinkedHashSet<>();
	private final WaitlistQueue waitlist = new WaitlistQueue();

	public RegistrationService(String courseId, int capacity, LearnerRegistry learners) {
		if (courseId == null || courseId.isBlank()) {
			throw new IllegalArgumentException("courseId is required");
		}
		if (capacity < 1) {
			throw new IllegalArgumentException("capacity must be at least 1");
		}
		this.courseId = courseId;
		this.capacity = capacity;
		this.learners = learners;
	}

	public EnrollmentResult enroll(String learnerId) {
		if (StringUtils.isBlank(learnerId)) {
			return EnrollmentResult.of(EnrollmentStatus.INVALID_REQUEST, "learnerId is required");
		}
		EnrollmentStatus status;
		int position = 0;
		// Critical section: the membership checks, capacity check and insert must be one atomic step.
		synchronized (lock) {
			if (enrolled.contains(learnerId)) {
				status = EnrollmentStatus.ALREADY_ENROLLED;
			}
			else if (waitlist.contains(learnerId)) {
				status = EnrollmentStatus.ALREADY_WAITLISTED;
				position = waitlist.positionOf(learnerId);
			}
			else if (!learners.isKnown(learnerId)) {
				status = EnrollmentStatus.UNKNOWN_LEARNER;
			}
			else if (!learners.isEligible(learnerId)) {
				status = EnrollmentStatus.NOT_ELIGIBLE;
			}
			else if (enrolled.size() < capacity) {
				enrolled.add(learnerId);
				status = EnrollmentStatus.ENROLLED;
			}
			else {
				waitlist.add(learnerId);
				status = EnrollmentStatus.WAITLISTED;
				position = waitlist.size();
			}
		}
		return switch (status) {
			case ALREADY_ENROLLED -> EnrollmentResult.of(status, learnerId + " is already enrolled in " + courseId);
			case ALREADY_WAITLISTED -> new EnrollmentResult(status,
					learnerId + " is already on the waitlist for " + courseId, position);
			case UNKNOWN_LEARNER -> EnrollmentResult.of(status, "Unknown learner " + learnerId);
			case NOT_ELIGIBLE -> EnrollmentResult.of(status,
					learnerId + " is not eligible for " + courseId + " (prerequisite not met)");
			case ENROLLED -> EnrollmentResult.of(status, learnerId + " enrolled in " + courseId);
			case WAITLISTED -> new EnrollmentResult(status,
					courseId + " is full; " + learnerId + " added to waitlist", position);
			case INVALID_REQUEST -> throw new IllegalStateException("handled before locking");
		};
	}

	public DropResult drop(String learnerId) {
		if (StringUtils.isBlank(learnerId)) {
			return DropResult.of(DropStatus.INVALID_REQUEST, "learnerId is required");
		}
		DropStatus status;
		String promoted = null;
		// Critical section: removal and promotion are one step, so a racing enroll cannot take the freed seat.
		synchronized (lock) {
			if (waitlist.remove(learnerId)) {
				status = DropStatus.DROPPED_FROM_WAITLIST;
			}
			else if (enrolled.remove(learnerId)) {
				status = DropStatus.DROPPED_ENROLLMENT;
				promoted = promoteNext();
			}
			else {
				status = DropStatus.NOT_REGISTERED;
			}
		}
		return switch (status) {
			case DROPPED_FROM_WAITLIST -> DropResult.of(status, learnerId + " removed from waitlist for " + courseId);
			case NOT_REGISTERED -> DropResult.of(status,
					learnerId + " is neither enrolled nor waitlisted in " + courseId);
			case DROPPED_ENROLLMENT -> new DropResult(status,
					promoted == null
							? learnerId + " dropped " + courseId + "; seat is open"
							: learnerId + " dropped " + courseId + "; " + promoted + " promoted from waitlist",
					promoted);
			case INVALID_REQUEST -> throw new IllegalStateException("handled before locking");
		};
	}

	public OfferingSnapshot snapshot() {
		List<String> enrolledCopy;
		List<String> waitlistCopy;
		synchronized (lock) {
			enrolledCopy = new ArrayList<>(enrolled);
			waitlistCopy = waitlist.toList();
		}
		return new OfferingSnapshot(courseId, capacity, enrolledCopy, waitlistCopy);
	}

	/**
	 * Promotes the earliest waitlisted learner who is eligible. Ineligible learners
	 * met on the
	 * way are removed from the waitlist. Caller must hold the lock.
	 */
	private String promoteNext() {
		while (waitlist.size() > 0 && enrolled.size() < capacity) {
			String candidate = waitlist.first();
			waitlist.remove(candidate);
			if (learners.isEligible(candidate)) {
				enrolled.add(candidate);
				return candidate;
			}
		}
		return null;
	}
}
