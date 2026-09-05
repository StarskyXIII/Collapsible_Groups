package com.starskyxiii.collapsible_groups.ingredient;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class TagQueryDiagnosticsTest {
	@Test void thousandsOfQueriesNeverRevisitSourcesAndReplacementCanChangeAvailability() {
		var reads = new java.util.concurrent.atomic.AtomicInteger();
		List<TagQueryDiagnostics.Source> sources = new java.util.AbstractList<>() {
			public int size() { return 1000; }
			public TagQueryDiagnostics.Source get(int index) { reads.incrementAndGet(); return PRESENT; }
		};
		var summary = TagQueryDiagnostics.summarize(sources);
		int preparationReads = reads.get();
		for (int pass = 0; pass < 3; pass++) {
			for (int i = 0; i < 1024; i++) {
				assertEquals(TagQueryDiagnostics.Existence.ABSENT,
					summary.query(ResourceLocation.parse("test:absent_" + i)).existence());
			}
		}
		assertEquals(preparationReads, reads.get());
		assertEquals(TagQueryDiagnostics.Existence.PRESENT, summary.query(TAG).existence());
		var replacement = TagQueryDiagnostics.summarize(List.of(UNKNOWN));
		assertEquals(TagQueryDiagnostics.Availability.UNAVAILABLE, replacement.query(TAG).availability());
		assertEquals(TagQueryDiagnostics.Existence.PRESENT, summary.query(TAG).existence());
	}
	private static final ResourceLocation TAG = ResourceLocation.parse("test:tag");
	private static final TagQueryDiagnostics.Source PRESENT = new TagQueryDiagnostics.Source(true, Set.of(TAG));
	private static final TagQueryDiagnostics.Source ABSENT = new TagQueryDiagnostics.Source(true, Set.of());
	private static final TagQueryDiagnostics.Source UNKNOWN = new TagQueryDiagnostics.Source(false, Set.of());

	@Test void multipleQueryableSourcesAreFullySupported() {
		assertEquals(new TagQueryDiagnostics(TagQueryDiagnostics.Availability.SUPPORTED, TagQueryDiagnostics.Existence.PRESENT),
			TagQueryDiagnostics.aggregate(List.of(PRESENT, ABSENT, PRESENT), TAG));
		assertEquals(new TagQueryDiagnostics(TagQueryDiagnostics.Availability.SUPPORTED, TagQueryDiagnostics.Existence.ABSENT),
			TagQueryDiagnostics.aggregate(List.of(ABSENT, ABSENT), TAG));
	}

	@Test void missingSourcesCannotProveTagAbsence() {
		assertEquals(new TagQueryDiagnostics(TagQueryDiagnostics.Availability.PARTIAL, TagQueryDiagnostics.Existence.UNKNOWN),
			TagQueryDiagnostics.aggregate(List.of(ABSENT, UNKNOWN), TAG));
		assertEquals(new TagQueryDiagnostics(TagQueryDiagnostics.Availability.PARTIAL, TagQueryDiagnostics.Existence.PRESENT),
			TagQueryDiagnostics.aggregate(List.of(PRESENT, UNKNOWN), TAG));
		assertEquals(TagQueryDiagnostics.Availability.UNAVAILABLE, TagQueryDiagnostics.aggregate(List.of(UNKNOWN), TAG).availability());
		assertEquals(TagQueryDiagnostics.Existence.UNKNOWN, TagQueryDiagnostics.aggregate(List.of(), TAG).existence());
	}
}
