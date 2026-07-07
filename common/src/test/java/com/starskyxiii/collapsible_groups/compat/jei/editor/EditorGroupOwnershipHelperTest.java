package com.starskyxiii.collapsible_groups.compat.jei.editor;

import com.starskyxiii.collapsible_groups.core.Filters;
import com.starskyxiii.collapsible_groups.core.GroupDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P3b regression: both branches of {@link EditorGroupOwnershipHelper#buildOwnership}
 * must share the "single JEI winner" semantics — the reverseIndex (live) path and
 * the priority-ordered first-match fallback path produce the same output for the
 * same inputs. Uses the registry-free generic core with string entries.
 */
class EditorGroupOwnershipHelperTest {

	private static final Function<String, String> IDENTITY_ID = entry -> entry;

	private static GroupDefinition group(String id, String name) {
		return new GroupDefinition(id, name, true, Filters.itemId("minecraft:" + id));
	}

	/** Fake matcher: a group matches an entry when the entry lists the group id. */
	private static final BiPredicate<GroupDefinition, String> MATCHES =
		(group, entry) -> entry.contains("[" + group.id() + "]");

	/**
	 * Registry-free winner-name source. Production uses
	 * EditorGroupOwnershipHelper::displayName, which resolves the client display
	 * text and needs a bootstrapped game; tests map ids to the raw names instead.
	 */
	private static final Function<GroupDefinition, String> WINNER_NAME =
		group -> switch (group.id()) {
			case "high" -> "High";
			case "low" -> "Low";
			case "a" -> "A";
			case "b" -> "B";
			case "c" -> "C";
			default -> group.id();
		};

	@Test
	void fallbackPathPicksFirstMatchWinnerInPriorityOrder() {
		// otherGroups arrives priority-ordered (GroupRegistry.getAllIncludingKubeJs).
		List<GroupDefinition> others = List.of(group("high", "High"), group("low", "Low"));
		List<String> entries = List.of("[high][low]", "[low]", "none");

		Map<String, List<String>> ownership = EditorGroupOwnershipHelper.buildOwnership(
			entries, Map.of(), others, null, IDENTITY_ID, MATCHES, WINNER_NAME);

		assertEquals(List.of("High"), ownership.get("[high][low]"));
		assertEquals(List.of("Low"), ownership.get("[low]"));
		assertFalse(ownership.containsKey("none"));
	}

	@Test
	void reverseIndexPathKeysToSingleWinner() {
		// The live reverseIndex is already deduped to one owner per id; even if a
		// stale index carried several ids, only the first named group is reported.
		Map<String, Set<String>> reverseIndex = Map.of(
			"[high][low]", Set.of("high"),
			"[low]", Set.of("low"));
		Map<String, String> names = Map.of("high", "High", "low", "Low");

		Map<String, List<String>> ownership = EditorGroupOwnershipHelper.buildOwnership(
			List.of("[high][low]", "[low]", "none"), names, List.of(), reverseIndex, IDENTITY_ID, MATCHES, WINNER_NAME);

		assertEquals(List.of("High"), ownership.get("[high][low]"));
		assertEquals(List.of("Low"), ownership.get("[low]"));
		assertFalse(ownership.containsKey("none"));
	}

	@Test
	void bothPathsProduceSameOutputForSameInputs() {
		List<GroupDefinition> others = List.of(group("high", "High"), group("low", "Low"));
		List<String> entries = List.of("[high][low]", "[low]", "[high]", "none");
		Map<String, String> names = Map.of("high", "High", "low", "Low");
		// Reverse index mirroring the live builder: single first-match winner per id.
		Map<String, Set<String>> reverseIndex = Map.of(
			"[high][low]", Set.of("high"),
			"[low]", Set.of("low"),
			"[high]", Set.of("high"));

		Map<String, List<String>> viaIndex = EditorGroupOwnershipHelper.buildOwnership(
			entries, names, others, reverseIndex, IDENTITY_ID, MATCHES, WINNER_NAME);
		Map<String, List<String>> viaFallback = EditorGroupOwnershipHelper.buildOwnership(
			entries, names, others, null, IDENTITY_ID, MATCHES, WINNER_NAME);

		for (String entry : entries) {
			assertEquals(viaIndex.get(entry), viaFallback.get(entry), "entry: " + entry);
		}
	}

	@Test
	void winnerListIsAlwaysSingleElement() {
		List<GroupDefinition> others = List.of(group("a", "A"), group("b", "B"), group("c", "C"));
		Map<String, List<String>> ownership = EditorGroupOwnershipHelper.buildOwnership(
			List.of("[a][b][c]"), Map.of(), others, null, IDENTITY_ID, MATCHES, WINNER_NAME);

		assertEquals(1, ownership.get("[a][b][c]").size());
		assertEquals(List.of("A"), ownership.get("[a][b][c]"));
	}

	@Test
	void reverseIndexIgnoresGroupsWithoutDisplayNames() {
		// groupNames excludes the edited group and disabled groups; ids not in the
		// map must not surface as winners.
		Map<String, Set<String>> reverseIndex = Map.of("e", Set.of("unknown"));

		Map<String, List<String>> ownership = EditorGroupOwnershipHelper.buildOwnership(
			List.of("e"), Map.of(), List.of(), reverseIndex, IDENTITY_ID, MATCHES, WINNER_NAME);

		assertTrue(ownership.isEmpty());
	}
}
