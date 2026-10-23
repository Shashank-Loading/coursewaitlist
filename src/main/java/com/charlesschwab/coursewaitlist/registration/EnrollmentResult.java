package com.charlesschwab.coursewaitlist.registration;

/** Outcome of an enroll request. {@code waitlistPosition} is 1-based and only set for waitlist outcomes. */
public record EnrollmentResult(EnrollmentStatus status, String message, Integer waitlistPosition) {

	static EnrollmentResult of(EnrollmentStatus status, String message) {
		return new EnrollmentResult(status, message, null);
	}
}
