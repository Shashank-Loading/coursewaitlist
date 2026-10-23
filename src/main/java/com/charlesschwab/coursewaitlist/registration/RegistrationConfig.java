package com.charlesschwab.coursewaitlist.registration;

import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class RegistrationConfig {

	@Bean
	CourseOffering courseOffering(@Value("${registration.course-id:CS201}") String courseId,
			@Value("${registration.capacity:2}") int capacity,
			@Value("${registration.prerequisites:CS101}") Set<String> prerequisites) {
		return new CourseOffering(courseId, capacity, prerequisites);
	}

	@Bean
	RegistrationService registrationService(CourseOffering offering, LearnerRegistry learners) {
		return new RegistrationService(offering, learners);
	}
}
