package com.starskyxiii.collapsible_groups.compat.kubejs;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KubeJSRemoteListenerTest {
	@Test
	void delayedOlderSnapshotCannotReplaceNewerSnapshot() {
		KubeJSRemoteListener.RemoteSnapshot initial = new KubeJSRemoteListener.RemoteSnapshot(1, List.of(), List.of());
		KubeJSRemoteListener.RemoteSnapshot newer = new KubeJSRemoteListener.RemoteSnapshot(3, List.of(), List.of());
		KubeJSRemoteListener.RemoteSnapshot delayed = new KubeJSRemoteListener.RemoteSnapshot(2, List.of(), List.of());
		AtomicReference<KubeJSRemoteListener.RemoteSnapshot> target = new AtomicReference<>(initial);

		assertTrue(KubeJSRemoteListener.installIfNewer(target, newer));
		assertFalse(KubeJSRemoteListener.installIfNewer(target, delayed));
		assertEquals(3, target.get().revision());
	}

	@Test
	void clearPublishesNewerEmptySnapshot() {
		long before = KubeJSRemoteListener.snapshot().revision();
		KubeJSRemoteListener.clear();
		KubeJSRemoteListener.RemoteSnapshot cleared = KubeJSRemoteListener.snapshot();
		assertTrue(cleared.revision() > before);
		assertTrue(cleared.itemGroups().isEmpty());
		assertTrue(cleared.fluidGroups().isEmpty());
		AtomicReference<KubeJSRemoteListener.RemoteSnapshot> target = new AtomicReference<>(cleared);
		assertFalse(KubeJSRemoteListener.installIfNewer(target,
			new KubeJSRemoteListener.RemoteSnapshot(before, List.of(), List.of())));
	}
}
