package com.starskyxiii.collapsible_groups.client.editor;

import com.starskyxiii.collapsible_groups.client.editor.EditorRuntimeServices;

import com.starskyxiii.collapsible_groups.group.GroupDefinition;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Function;

public final class EditorGroupOwnershipHelper {
	private EditorGroupOwnershipHelper() {}

	static Map<String, String> enabledGroupDisplayNames(List<GroupDefinition> groups, String editId) {
		Map<String, String> groupNames = new HashMap<>();
		for (GroupDefinition group : groups) {
			if (!group.id().equals(editId) && group.enabled()) {
				groupNames.put(group.id(), displayName(group));
			}
		}
		return groupNames;
	}

	static List<GroupDefinition> enabledOtherGroups(List<GroupDefinition> groups, String editId) {
		return groups.stream()
			.filter(group -> !group.id().equals(editId) && group.enabled())
			.toList();
	}

	/**
	 * Ownership of source-grid ingredients by <em>other</em> groups, keyed to the
	 * single JEI winner (never all matches). Both branches share this "winner"
	 * semantics so the overlap corner-tab and its tooltip name the group that JEI
	 * actually displays the ingredient under:
	 * <ul>
	 *   <li>{@code reverseIndex} (live path): built from resolved first-match
	 *       owners in {@link com.starskyxiii.collapsible_groups.compat.jei.runtime.IngredientFilterHelper},
	 *       already deduped to one owner per ingredient id.</li>
	 *   <li>{@code otherGroups} fallback: priority-ordered (see
	 *       {@code EditorRuntimeServices.get().getAllIncludingKubeJs}); the first group whose
	 *       {@code matches} predicate succeeds is the winner, matching
	 *       {@code EditorRuntimeServices.get().findGroup}'s first-match rule.</li>
	 * </ul>
	 * The returned value is a single-element list to preserve the tooltip
	 * signature; the head element is the winning group's display name.
	 *
	 * <p>Registry access only through {@code registryId}/{@code matches}, so both
	 * paths can be regression-tested without a bootstrapped game.
	 */
	public static <T> Map<T, List<String>> buildOwnership(
		List<T> entries,
		Map<String, String> groupNames,
		List<GroupDefinition> otherGroups,
		Map<String, Set<String>> reverseIndex,
		Function<T, String> registryId,
		BiPredicate<GroupDefinition, T> matches,
		Function<GroupDefinition, String> winnerName
	) {
		Map<T, List<String>> ownership = new IdentityHashMap<>();
		if (reverseIndex != null) {
			for (T entry : entries) {
				Set<String> groupIds = reverseIndex.getOrDefault(registryId.apply(entry), Set.of());
				for (String groupId : groupIds) {
					String name = groupNames.get(groupId);
					if (name != null) {
						ownership.put(entry, List.of(name));
						break;
					}
				}
			}
			return ownership;
		}

		for (T entry : entries) {
			for (GroupDefinition other : otherGroups) {
				if (matches.test(other, entry)) {
					ownership.put(entry, List.of(winnerName.apply(other)));
					break;
				}
			}
		}
		return ownership;
	}

	/** Item specialization of {@link #buildOwnership}; see its winner-semantics contract. */
	static Map<ItemStack, List<String>> buildItemOwnership(
		List<ItemStack> items,
		Map<String, String> groupNames,
		List<GroupDefinition> otherGroups,
		Map<String, Set<String>> reverseIndex
	) {
		return buildOwnership(items, groupNames, otherGroups, reverseIndex,
			stack -> BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(),
			GroupDefinition::matchesIgnoringEnabled,
			EditorGroupOwnershipHelper::displayName);
	}

	public static String displayName(GroupDefinition group) {
		return displayName(group.id(), group.name());
	}

	static String displayName(String id, String name) {
		return (name != null && !name.isBlank()) ? name : id;
	}
}
