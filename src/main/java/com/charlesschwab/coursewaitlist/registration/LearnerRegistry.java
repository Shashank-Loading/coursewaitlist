package com.charlesschwab.coursewaitlist.registration;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/** In-memory learner records: which courses each known learner has completed. */
@Component
public class LearnerRegistry {

	private final Map<String, Set<String>> completedCourses = new ConcurrentHashMap<>();

	public void register(String learnerId, Set<String> completed) {
		completedCourses.put(learnerId, Set.copyOf(completed));
	}

	public boolean isKnown(String learnerId) {
		return completedCourses.containsKey(learnerId);
	}

	public Set<String> completedCourses(String learnerId) {
		return completedCourses.getOrDefault(learnerId, Set.of());
	}
}
