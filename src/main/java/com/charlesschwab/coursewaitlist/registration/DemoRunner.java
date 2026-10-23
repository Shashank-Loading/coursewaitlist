package com.charlesschwab.coursewaitlist.registration;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Console walkthrough; run with {@code --registration.demo=true}. */
@Component
@ConditionalOnProperty("registration.demo")
class DemoRunner implements CommandLineRunner {

	private final RegistrationService service;
	private final LearnerRegistry learners;

	DemoRunner(RegistrationService service, LearnerRegistry learners) {
		this.service = service;
		this.learners = learners;
	}

	@Override
	public void run(String... args) {
		for (String id : new String[] { "alice", "bob", "carol", "dave" }) {
			learners.register(id, true);
		}
		learners.register("erin", false);
		for (String id : new String[] { "alice", "bob", "carol", "dave", "erin", "alice" }) {
			System.out.println("enroll " + id + " -> " + service.enroll(id));
		}
		System.out.println("drop alice -> " + service.drop("alice"));
		System.out.println("drop alice -> " + service.drop("alice"));
		System.out.println(service.snapshot());
	}
}
