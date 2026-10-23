package com.charlesschwab.coursewaitlist.controller;

import com.charlesschwab.coursewaitlist.registration.DropResult;
import com.charlesschwab.coursewaitlist.registration.EnrollmentResult;
import com.charlesschwab.coursewaitlist.registration.LearnerRegistry;
import com.charlesschwab.coursewaitlist.registration.OfferingSnapshot;
import com.charlesschwab.coursewaitlist.registration.RegistrationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Thin HTTP adapter over {@link RegistrationService}, intended for manual/automated testing. */
@RestController
@RequestMapping("/api")
class RegistrationController {

	record LearnerRequest(String learnerId, boolean eligible) {
	}

	record EnrollRequest(String learnerId) {
	}

	record LearnerResponse(String learnerId, boolean eligible, String message) {
	}

	private final RegistrationService service;
	private final LearnerRegistry learners;

	RegistrationController(RegistrationService service, LearnerRegistry learners) {
		this.service = service;
		this.learners = learners;
	}

	/** Test helper: creates or replaces a learner and their eligibility flag. */
	@PostMapping("/learners")
	ResponseEntity<Object> registerLearner(@RequestBody LearnerRequest request) {
		if (request.learnerId() == null || request.learnerId().isBlank()) {
			return ResponseEntity.badRequest().body("learnerId is required");
		}
		learners.register(request.learnerId(), request.eligible());
		return ResponseEntity.status(HttpStatus.CREATED).body(new LearnerResponse(request.learnerId(),
				request.eligible(), "Learner " + request.learnerId() + " created (eligible=" + request.eligible() + ")"));
	}

	@PostMapping("/enrollments")
	ResponseEntity<EnrollmentResult> enroll(@RequestBody EnrollRequest request) {
		EnrollmentResult result = service.enroll(request.learnerId());
		HttpStatus http = switch (result.status()) {
			case ENROLLED, WAITLISTED -> HttpStatus.CREATED;
			case ALREADY_ENROLLED, ALREADY_WAITLISTED -> HttpStatus.CONFLICT;
			case NOT_ELIGIBLE -> HttpStatus.UNPROCESSABLE_ENTITY;
			case UNKNOWN_LEARNER -> HttpStatus.NOT_FOUND;
			case INVALID_REQUEST -> HttpStatus.BAD_REQUEST;
		};
		return ResponseEntity.status(http).body(result);
	}

	@DeleteMapping("/enrollments/{learnerId}")
	ResponseEntity<DropResult> drop(@PathVariable String learnerId) {
		DropResult result = service.drop(learnerId);
		HttpStatus http = switch (result.status()) {
			case DROPPED_ENROLLMENT, DROPPED_FROM_WAITLIST -> HttpStatus.OK;
			case NOT_REGISTERED -> HttpStatus.NOT_FOUND;
			case INVALID_REQUEST -> HttpStatus.BAD_REQUEST;
		};
		return ResponseEntity.status(http).body(result);
	}

	@GetMapping("/offering")
	OfferingSnapshot offering() {
		return service.snapshot();
	}
}
