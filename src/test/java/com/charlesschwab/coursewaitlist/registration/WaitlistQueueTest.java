package com.charlesschwab.coursewaitlist.registration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

class WaitlistQueueTest {

	@Test
	void positionsFollowFifoAndShiftAfterRemoval() {
		WaitlistQueue queue = new WaitlistQueue();
		queue.add("a");
		queue.add("b");
		queue.add("c");
		assertFalse(queue.add("b"));
		assertEquals(2, queue.positionOf("b"));
		assertTrue(queue.remove("a"));
		assertEquals(1, queue.positionOf("b"));
		assertEquals(2, queue.positionOf("c"));
		assertEquals(0, queue.positionOf("a"));
		assertEquals("b", queue.first());
	}

	@Test
	void matchesPlainListUnderRandomOperationsIncludingRenumbering() {
		WaitlistQueue queue = new WaitlistQueue();
		List<String> model = new ArrayList<>();
		Random random = new Random(42);
		for (int i = 0; i < 5000; i++) {
			String id = "L" + random.nextInt(60);
			if (random.nextBoolean()) {
				assertEquals(!model.contains(id), queue.add(id));
				if (!model.contains(id)) {
					model.add(id);
				}
			}
			else {
				assertEquals(model.remove(id), queue.remove(id));
			}
			assertEquals(model.size(), queue.size());
			assertEquals(model, queue.toList());
			for (int p = 0; p < model.size(); p++) {
				assertEquals(p + 1, queue.positionOf(model.get(p)));
			}
		}
	}
}
