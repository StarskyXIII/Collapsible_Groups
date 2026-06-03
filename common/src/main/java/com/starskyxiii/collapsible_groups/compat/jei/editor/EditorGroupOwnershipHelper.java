package com.starskyxiii.collapsible_groups.compat.jei.editor;

import com.starskyxiii.collapsible_groups.core.GroupDefinition;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class EditorGroupOwnershipHelper {
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

	static Map<ItemStack, List<String>> buildItemOwnership(
		List<ItemStack> items,
		Map<String, String> groupNames,
		List<GroupDefinition> otherGroups,
		Map<String, Set<String>> reverseIndex
	) {
		Map<ItemStack, List<String>> ownership = new IdentityHashMap<>();
		if (reverseIndex != null) {
			for (ItemStack stack : items) {
				String registryId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
				Set<String> groupIds = reverseIndex.getOrDefault(registryId, Set.of());
				List<String> names = new ArrayList<>();
				for (String groupId : groupIds) {
					String name = groupNames.get(groupId);
					if (name != null) names.add(name);
				}
				if (!names.isEmpty()) ownership.put(stack, names);
			}
			return ownership;
		}

		for (GroupDefinition other : otherGroups) {
			String name = displayName(other);
			for (ItemStack stack : items) {
				if (other.matchesIgnoringEnabled(stack)) {
					ownership.computeIfAbsent(stack, k -> new ArrayList<>()).add(name);
				}
			}
		}
		return ownership;
	}

	static String displayName(GroupDefinition group) {
		return displayName(group.id(), group.name());
	}

	static String displayName(String id, String name) {
		return (name != null && !name.isBlank()) ? name : id;
	}
}
