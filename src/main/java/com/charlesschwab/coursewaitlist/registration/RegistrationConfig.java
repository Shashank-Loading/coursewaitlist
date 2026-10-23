package com.charlesschwab.coursewaitlist.registration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class RegistrationConfig {

	@Bean
	RegistrationService registrationService(LearnerRegistry learners,
			@Value("${registration.course-id:CS201}") String courseId,
			@Value("${registration.capacity:1}") int capacity) {
		return new RegistrationService(courseId, capacity, learners);
	}
}
