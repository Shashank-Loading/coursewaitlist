package com.charlesschwab.coursewaitlist.registration;

import java.util.List;

/** Immutable copy of the offering state, in enrollment / queue order. */
public record OfferingSnapshot(int capacity, List<String> enrolled, List<String> waitlist) {

	public int availableSeats() {
		return capacity - enrolled.size();
	}
}
