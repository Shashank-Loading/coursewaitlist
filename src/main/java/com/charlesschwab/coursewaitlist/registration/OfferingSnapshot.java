package com.charlesschwab.coursewaitlist.registration;

import java.util.List;

/** Immutable copy of one offering's state, in enrollment / queue order. */
public record OfferingSnapshot(String courseId, int capacity, List<String> enrolled, List<String> waitlist) {

	public int availableSeats() {
		return capacity - enrolled.size();
	}
}
