package com.starskyxiii.collapsible_groups.compat.emi;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class EmiSnapshotTransactionTest {
	@Test void capturesAndPublishesUnderLocksButPreparesOutsideThem() {
		Object state = new Object(), reload = new Object();
		var stamp = new EmiSnapshotTransaction.Stamp(1, new Object(), new Object());
		var published = new AtomicInteger();
		var transaction = EmiSnapshotTransaction.capture(state, reload, () -> stamp, () -> {
			assertTrue(Thread.holdsLock(state));
			assertTrue(Thread.holdsLock(reload));
			return 10;
		});
		assertTrue(transaction.prepareAndPublish(value -> {
			assertFalse(Thread.holdsLock(state));
			assertFalse(Thread.holdsLock(reload));
			return value + 1;
		}, value -> {
			assertTrue(Thread.holdsLock(state));
			assertTrue(Thread.holdsLock(reload));
			published.set(value);
		}));
		assertEquals(11, published.get());
	}

	@Test void reloadFailureClearAndEqualContentReplacementRejectCandidate() {
		Object state = new Object(), reload = new Object(), session = new Object();
		List<String> source = new java.util.ArrayList<>(List.of("same"));
		var original = new EmiSnapshotTransaction.Stamp(1, source, session);
		for (int transition = 0; transition < 4; transition++) {
			var current = new AtomicReference<>(original);
			var transaction = EmiSnapshotTransaction.capture(state, reload, current::get, () -> "candidate");
			int change = transition;
			assertFalse(transaction.prepareAndPublish(value -> {
				current.set(switch (change) {
					case 0 -> null;
					case 1 -> new EmiSnapshotTransaction.Stamp(2, source, session);
					case 2 -> new EmiSnapshotTransaction.Stamp(1, new java.util.ArrayList<>(source), session);
					default -> new EmiSnapshotTransaction.Stamp(1, source, new Object());
				});
				return value;
			}, value -> fail("Obsolete candidate published")));
		}
		assertNull(EmiSnapshotTransaction.capture(state, reload, () -> null, () -> {
			fail("Reloading registry must not be copied");
			return "unreachable";
		}));
	}

	@Test void failedPreparationDoesNotPublishAndCanBeRetried() {
		Object state = new Object(), reload = new Object();
		var stamp = new EmiSnapshotTransaction.Stamp(1, new Object(), new Object());
		var transaction = EmiSnapshotTransaction.capture(state, reload, () -> stamp, () -> 1);
		assertThrows(IllegalStateException.class, () -> transaction.prepareAndPublish(value -> {
			throw new IllegalStateException("fixture");
		}, value -> fail("Failed preparation published")));
		assertTrue(EmiSnapshotTransaction.capture(state, reload, () -> stamp, () -> 2)
			.prepareAndPublish(value -> value, value -> assertEquals(2, value)));
	}
}
