package com.charlesschwab.coursewaitlist.registration;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/** In-memory learner records: whether each known learner meets the prerequisite (course-independent). */
@Component
public class LearnerRegistry {

	private final Map<String, Boolean> eligibility = new ConcurrentHashMap<>();

	public void register(String learnerId, boolean eligible) {
		eligibility.put(learnerId, eligible);
	}

	public boolean isKnown(String learnerId) {
		return eligibility.containsKey(learnerId);
	}

	public boolean isEligible(String learnerId) {
		return eligibility.getOrDefault(learnerId, false);
	}
}
