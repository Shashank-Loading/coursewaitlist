package com.charlesschwab.coursewaitlist.registration;

import java.util.Set;

/** Static definition of the single offering: its capacity and required prior courses. */
public record CourseOffering(String courseId, int capacity, Set<String> prerequisites) {

	public CourseOffering {
		if (courseId == null || courseId.isBlank()) {
			throw new IllegalArgumentException("courseId is required");
		}
		if (capacity < 1) {
			throw new IllegalArgumentException("capacity must be at least 1");
		}
		prerequisites = Set.copyOf(prerequisites);
	}
}
