package com.starskyxiii.collapsible_groups.ingredient;

import net.minecraft.resources.ResourceLocation;
import java.util.List;
import java.util.Set;

public record TagQueryDiagnostics(Availability availability, Existence existence) {
	public enum Availability { SUPPORTED, PARTIAL, UNAVAILABLE, PENDING }
	public enum Existence { PRESENT, ABSENT, UNKNOWN }
	public static final TagQueryDiagnostics UNREPORTED = new TagQueryDiagnostics(Availability.SUPPORTED, Existence.UNKNOWN);
	public static final TagQueryDiagnostics PENDING = new TagQueryDiagnostics(Availability.PENDING, Existence.UNKNOWN);
	public record Source(boolean available, Set<ResourceLocation> existing) {
		public Source { existing = Set.copyOf(existing); }
	}
	public record Summary(Availability availability, Set<ResourceLocation> existing) {
		public Summary { existing = Set.copyOf(existing); }
		public TagQueryDiagnostics query(ResourceLocation tag) {
			return new TagQueryDiagnostics(availability, existing.contains(tag) ? Existence.PRESENT
				: availability == Availability.SUPPORTED ? Existence.ABSENT : Existence.UNKNOWN);
		}
	}
	public static Summary summarize(List<Source> sources) {
		int available = 0;
		Set<ResourceLocation> existing = new java.util.HashSet<>();
		Set<Set<ResourceLocation>> seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
		for (Source source : sources) {
			if (!source.available()) continue;
			available++;
			if (seen.add(source.existing())) existing.addAll(source.existing());
		}
		Availability availability = available == 0 ? Availability.UNAVAILABLE
			: available == sources.size() ? Availability.SUPPORTED : Availability.PARTIAL;
		return new Summary(availability, existing);
	}
	public static TagQueryDiagnostics aggregate(List<Source> sources, ResourceLocation tag) {
		return summarize(sources).query(tag);
	}
}
