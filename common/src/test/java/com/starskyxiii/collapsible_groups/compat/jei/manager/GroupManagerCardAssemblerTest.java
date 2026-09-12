package com.starskyxiii.collapsible_groups.compat.jei.manager;

import com.starskyxiii.collapsible_groups.group.GroupChangeEvent;
import com.starskyxiii.collapsible_groups.group.GroupDefinition;
import com.starskyxiii.collapsible_groups.group.filter.GroupFilter;
import com.starskyxiii.collapsible_groups.viewer.GroupCandidateIndex;
import com.starskyxiii.collapsible_groups.viewer.ViewerGroupIndex;
import com.starskyxiii.collapsible_groups.viewer.ViewerGroupPreviewSnapshot;
import com.starskyxiii.collapsible_groups.viewer.ViewerIngredientIdentity;
import com.starskyxiii.collapsible_groups.viewer.ViewerPreviewValue;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

class GroupManagerCardAssemblerTest {
	@Test void unpublishedGenerationPerformsZeroSynchronousPreviewResolutions() {
		AtomicInteger resolverCalls = new AtomicInteger();
		ViewerGroupIndex pending = index(Optional.empty(), new CompletableFuture<>(), group -> {
			resolverCalls.incrementAndGet();
			return Optional.empty();
		});

		GroupManagerCardAssembler.Result result = GroupManagerCardAssembler.build(List.of(group("pending")), pending);

		assertTrue(result.generationPending());
		assertEquals(0, resolverCalls.get(), "pending manager build must perform zero synchronous resolutions");
		assertEquals(0, result.cards().get(0).entryCount());
	}

	@Test void publishedGenerationUsesOneSnapshotIncludingExplicitEmptyGroups() {
		AtomicInteger snapshotCalls = new AtomicInteger();
		ViewerGroupIndex published = index(Optional.of(new GroupCandidateIndex(Map.of(), Map.of(), 0, 0, 0)),
			CompletableFuture.completedFuture(null), group -> {
				snapshotCalls.incrementAndGet();
				if (group.id().equals("empty")) {
					return Optional.of(new ViewerGroupPreviewSnapshot(List.of(), List.of(), List.of()));
				}
				return Optional.of(new ViewerGroupPreviewSnapshot(
					List.of(ViewerPreviewValue.rendered((graphics, x, y) -> {})),
					List.of(ViewerPreviewValue.rendered((graphics, x, y) -> {})),
					List.of(ViewerPreviewValue.rendered((graphics, x, y) -> {}))));
			});

		GroupManagerCardAssembler.Result result = GroupManagerCardAssembler.build(
			List.of(group("filled"), group("empty")), published);

		assertFalse(result.generationPending());
		assertEquals(2, snapshotCalls.get());
		assertEquals(3, result.cards().get(0).entryCount());
		assertEquals(0, result.cards().get(1).entryCount());
	}

	@Test void screenWorkingCopyMutatesWithoutChangingPublishedSnapshot() {
		ViewerGroupIndex published = index(Optional.of(new GroupCandidateIndex(Map.of(), Map.of(), 0, 0, 0)),
			CompletableFuture.completedFuture(null), group -> Optional.of(
				new ViewerGroupPreviewSnapshot(List.of(), List.of(), List.of())));
		GroupManagerCardAssembler.Result result = GroupManagerCardAssembler.build(
			List.of(group("first"), group("second")), published);

		assertThrows(UnsupportedOperationException.class,
			() -> result.cards().set(0, result.cards().get(0)));
		List<GroupManagerCard> working = GroupManagerCardAssembler.mutableWorkingCopy(result.cards());
		working.set(0, working.get(0).withGroup(group("replacement")));
		working.removeIf(card -> card.id().equals("second"));

		assertEquals(List.of("first", "second"), result.cards().stream().map(GroupManagerCard::id).toList());
		assertEquals(List.of("replacement"), working.stream().map(GroupManagerCard::id).toList());
	}

	@Test void pendingSnapshotUsesTheSameMutableOwnershipBoundary() {
		List<GroupManagerCard> pendingSnapshot = List.of();
		List<GroupManagerCard> working = GroupManagerCardAssembler.mutableWorkingCopy(pendingSnapshot);

		ViewerGroupIndex published = index(Optional.of(new GroupCandidateIndex(Map.of(), Map.of(), 0, 0, 0)),
			CompletableFuture.completedFuture(null), group -> Optional.of(
				new ViewerGroupPreviewSnapshot(List.of(), List.of(), List.of())));
		GroupManagerCard card = GroupManagerCardAssembler.build(List.of(group("published")), published)
			.cards().get(0);
		working.add(card);

		assertTrue(pendingSnapshot.isEmpty());
		assertEquals(List.of("published"), working.stream().map(GroupManagerCard::id).toList());
	}

	@Test void generationReplacedDuringAssemblyDiscardsEveryPartialCard() {
		var first = new GroupCandidateIndex(Map.of(), Map.of(), 0, 0, 0);
		var second = new GroupCandidateIndex(Map.of(), Map.of(), 0, 0, 0);
		var current = new AtomicReference<>(Optional.of(first));
		ViewerGroupIndex published = index(current::get, CompletableFuture.completedFuture(null), group -> {
			current.set(Optional.of(second));
			return Optional.of(new ViewerGroupPreviewSnapshot(
				List.of(ViewerPreviewValue.rendered((graphics, x, y) -> {})), List.of(), List.of()));
		});
		var result = GroupManagerCardAssembler.build(List.of(group("first"), group("second")), published);
		assertTrue(result.generationPending());
		assertEquals(0, result.totalItems());
		assertEquals(0, result.totalFluids());
		assertEquals(0, result.totalGeneric());
		assertTrue(result.cards().stream().allMatch(card -> card.entryCount() == 0
			&& card.evaluation().status() == com.starskyxiii.collapsible_groups.group.GroupEvaluation.Status.PENDING));
	}

	private static ViewerGroupIndex index(Optional<GroupCandidateIndex> candidates,
		CompletableFuture<Void> readiness, SnapshotResolver resolver) {
		return index(() -> candidates, readiness, resolver);
	}

	private static ViewerGroupIndex index(Supplier<Optional<GroupCandidateIndex>> candidates,
		CompletableFuture<Void> readiness, SnapshotResolver resolver) {
		return new ViewerGroupIndex() {
			@Override public Optional<GroupCandidateIndex> candidates() { return candidates.get(); }
			@Override public boolean ready() { return candidates.get().isPresent(); }
			@Override public CompletableFuture<Void> whenReady() { return readiness; }
			@Override public Optional<ViewerGroupPreviewSnapshot> fullMatchSnapshot(GroupDefinition group) {
                throw new AssertionError("Manager must not invoke the synchronous full-match resolver");
            }
            @Override public Optional<ViewerGroupPreviewSnapshot> cachedFullMatchSnapshot(GroupDefinition group) {
                return resolver.resolve(group);
            }
			@Override public Optional<String> resolveOwner(ViewerIngredientIdentity identity,
				List<GroupDefinition> groups) { return Optional.empty(); }
			@Override public Map<ViewerIngredientIdentity, String> resolveOwnership(
				List<GroupDefinition> groups) { return Map.of(); }
			@Override public void onGroupChange(GroupChangeEvent.Kind kind, List<GroupDefinition> groups) {}
		};
	}

	private static GroupDefinition group(String id) {
		return new GroupDefinition(id, id, true, new GroupFilter.Id("item", "minecraft:stone"));
	}

	@FunctionalInterface
	private interface SnapshotResolver {
		Optional<ViewerGroupPreviewSnapshot> resolve(GroupDefinition group);
	}
}
