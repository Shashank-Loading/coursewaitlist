package com.charlesschwab.coursewaitlist.registration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * FIFO queue of unique learner ids with O(log n) position lookup and removal from anywhere.
 *
 * <p>
 * Each learner gets an increasing sequence number. A Fenwick tree counts the live sequence numbers, so a
 * learner's position is the number of live entries up to their own sequence number. When the sequence space
 * is used up the queue renumbers itself, which keeps the tree proportional to the queue size. Not thread-safe;
 * the caller holds the service lock.
 */
class WaitlistQueue {

	private static final int MIN_SLOTS = 16;

	private final Map<String, Integer> sequenceOf = new LinkedHashMap<>();
	private int[] tree = new int[MIN_SLOTS + 1];
	private int nextSequence = 1;

	boolean contains(String learnerId) {
		return sequenceOf.containsKey(learnerId);
	}

	int size() {
		return sequenceOf.size();
	}

	/** Adds at the back; returns false if already queued. */
	boolean add(String learnerId) {
		if (sequenceOf.containsKey(learnerId)) {
			return false;
		}
		if (nextSequence >= tree.length) {
			renumber();
		}
		sequenceOf.put(learnerId, nextSequence);
		update(nextSequence, 1);
		nextSequence++;
		return true;
	}

	boolean remove(String learnerId) {
		Integer sequence = sequenceOf.remove(learnerId);
		if (sequence == null) {
			return false;
		}
		update(sequence, -1);
		return true;
	}

	/** Earliest queued learner, or null when empty. */
	String first() {
		return sequenceOf.isEmpty() ? null : sequenceOf.keySet().iterator().next();
	}

	/** 1-based position, or 0 when not queued. */
	int positionOf(String learnerId) {
		Integer sequence = sequenceOf.get(learnerId);
		return sequence == null ? 0 : prefixCount(sequence);
	}

	List<String> toList() {
		return new ArrayList<>(sequenceOf.keySet());
	}

	private void renumber() {
		List<String> ids = toList();
		tree = new int[Math.max(MIN_SLOTS, ids.size() * 2) + 1];
		sequenceOf.clear();
		nextSequence = 1;
		for (String id : ids) {
			sequenceOf.put(id, nextSequence);
			update(nextSequence, 1);
			nextSequence++;
		}
	}

	private void update(int index, int delta) {
		for (int i = index; i < tree.length; i += i & -i) {
			tree[i] += delta;
		}
	}

	private int prefixCount(int index) {
		int count = 0;
		for (int i = index; i > 0; i -= i & -i) {
			count += tree[i];
		}
		return count;
	}
}
