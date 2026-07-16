package com.starskyxiii.collapsible_groups.group;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GroupChangeEventTest {
	@Test
	void publishesToMultipleSubscribersInRegistrationOrder() {
		List<String> calls = new ArrayList<>();
		try (
			GroupChangeEvent.Subscription first = GroupChangeEvent.subscribe(
				GroupChangeEvent.Kind.FULL,
				() -> calls.add("first")
			);
			GroupChangeEvent.Subscription second = GroupChangeEvent.subscribe(
				GroupChangeEvent.Kind.FULL,
				() -> calls.add("second")
			)
		) {
			GroupChangeEvent.publish(GroupChangeEvent.Kind.FULL);
		}

		assertEquals(List.of("first", "second"), calls);
	}

	@Test
	void subscriptionsAreScopedByKindAndCanBeClosed() {
		List<String> calls = new ArrayList<>();
		GroupChangeEvent.Subscription enabled = GroupChangeEvent.subscribe(
			GroupChangeEvent.Kind.ENABLED,
			() -> calls.add("enabled")
		);
		try (GroupChangeEvent.Subscription structure = GroupChangeEvent.subscribe(
			GroupChangeEvent.Kind.STRUCTURE,
			() -> calls.add("structure")
		)) {
			enabled.close();
			GroupChangeEvent.publish(GroupChangeEvent.Kind.ENABLED);
			GroupChangeEvent.publish(GroupChangeEvent.Kind.STRUCTURE);
		}

		assertEquals(List.of("structure"), calls);
	}
}
