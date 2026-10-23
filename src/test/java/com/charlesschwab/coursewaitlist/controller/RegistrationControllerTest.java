package com.charlesschwab.coursewaitlist.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@WebAppConfiguration
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class RegistrationControllerTest {

	@Autowired
	private WebApplicationContext context;

	private MockMvc mvc() {
		return MockMvcBuilders.webAppContextSetup(context).build();
	}

	private ResultActions json(MockMvc mvc, String url, String body) throws Exception {
		return mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(body));
	}

	private void learner(MockMvc mvc, String id, boolean eligible) throws Exception {
		json(mvc, "/api/learners",
				"{\"learnerId\":\"" + id + "\",\"eligible\":" + eligible + "}")
				.andExpect(status().isCreated()).andExpect(jsonPath("$.learnerId").value(id))
				.andExpect(jsonPath("$.eligible").value(eligible))
				.andExpect(jsonPath("$.message").value("Learner " + id + " created (eligible=" + eligible + ")"));
	}

	private ResultActions enroll(MockMvc mvc, String id) throws Exception {
		return json(mvc, "/api/enrollments", "{\"learnerId\":\"" + id + "\"}");
	}

	@Test
	void fullFlowOverHttp() throws Exception {
		MockMvc mvc = mvc();
		for (String id : new String[] { "a", "b", "c" }) {
			learner(mvc, id, true);
		}
		learner(mvc, "x", false);

		enroll(mvc, "a").andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("ENROLLED"));
		enroll(mvc, "b").andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("WAITLISTED"))
				.andExpect(jsonPath("$.waitlistPosition").value(1));
		enroll(mvc, "c").andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("WAITLISTED"))
				.andExpect(jsonPath("$.waitlistPosition").value(2));
		enroll(mvc, "a").andExpect(status().isConflict())
				.andExpect(jsonPath("$.status").value("ALREADY_ENROLLED"));
		enroll(mvc, "x").andExpect(status().isUnprocessableEntity());
		enroll(mvc, "ghost").andExpect(status().isNotFound());
		enroll(mvc, " ").andExpect(status().isBadRequest());

		mvc.perform(delete("/api/enrollments/a")).andExpect(status().isOk())
				.andExpect(jsonPath("$.promotedLearnerId").value("b"));
		mvc.perform(delete("/api/enrollments/a")).andExpect(status().isNotFound())
				.andExpect(jsonPath("$.status").value("NOT_REGISTERED"));

		mvc.perform(get("/api/offering")).andExpect(status().isOk())
				.andExpect(jsonPath("$.courseId").value("CS201"))
				.andExpect(jsonPath("$.capacity").value(1))
				.andExpect(jsonPath("$.enrolled[0]").value("b"))
				.andExpect(jsonPath("$.waitlist[0]").value("c"));
	}

}
