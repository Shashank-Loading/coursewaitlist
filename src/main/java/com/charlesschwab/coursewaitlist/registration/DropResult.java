package com.charlesschwab.coursewaitlist.registration;

/** Outcome of a drop request. {@code promotedLearnerId} is set only if a waitlisted learner got the freed seat. */
public record DropResult(DropStatus status, String message, String promotedLearnerId) {

	static DropResult of(DropStatus status, String message) {
		return new DropResult(status, message, null);
	}
}
