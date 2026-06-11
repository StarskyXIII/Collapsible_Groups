package com.starskyxiii.collapsible_groups.compat.jei.manager;

import com.starskyxiii.collapsible_groups.compat.jei.data.GenericIngredientRef;
import com.starskyxiii.collapsible_groups.compat.jei.oreui.GroupActionEligibility;
import com.starskyxiii.collapsible_groups.compat.jei.oreui.GroupCardViewModel;
import com.starskyxiii.collapsible_groups.compat.jei.oreui.GroupSource;
import com.starskyxiii.collapsible_groups.compat.jei.oreui.PreviewPaneModel;
import com.starskyxiii.collapsible_groups.compat.jei.preview.GroupPreviewEntry;
import com.starskyxiii.collapsible_groups.core.GroupDefinition;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Objects;

public record GroupManagerCard(
	GroupDefinition group,
	List<ItemStack> items,
	List<Object> fluids,
	List<GenericIngredientRef> genericEntries,
	List<GroupPreviewEntry> previewEntries,
	GroupCardViewModel viewModel
) {
	public GroupManagerCard {
		group = Objects.requireNonNull(group, "group");
		items = List.copyOf(Objects.requireNonNull(items, "items"));
		fluids = List.copyOf(Objects.requireNonNull(fluids, "fluids"));
		genericEntries = List.copyOf(Objects.requireNonNull(genericEntries, "genericEntries"));
		previewEntries = List.copyOf(Objects.requireNonNull(previewEntries, "previewEntries"));
		viewModel = viewModel != null ? viewModel : buildViewModel(group, items.size(), fluids.size(), genericEntries.size());
	}

	public static GroupManagerCard create(
		GroupDefinition group,
		List<ItemStack> items,
		List<Object> fluids,
		List<GenericIngredientRef> genericEntries,
		List<GroupPreviewEntry> previewEntries
	) {
		return new GroupManagerCard(group, items, fluids, genericEntries, previewEntries, null);
	}

	public String id() {
		return group.id();
	}

	public String displayName() {
		return resolvedDisplayName(group);
	}

	public GroupSource source() {
		return viewModel.source();
	}

	public GroupActionEligibility actionEligibility() {
		return viewModel.actionEligibility();
	}

	public boolean editable() {
		return source().userEditable();
	}

	public int itemCount() {
		return items.size();
	}

	public int fluidCount() {
		return fluids.size();
	}

	public int genericCount() {
		return genericEntries.size();
	}

	public int entryCount() {
		return previewEntries.size();
	}

	public GroupManagerCard withGroup(GroupDefinition updatedGroup) {
		Objects.requireNonNull(updatedGroup, "updatedGroup");
		return new GroupManagerCard(
			updatedGroup,
			items,
			fluids,
			genericEntries,
			previewEntries,
			buildViewModel(updatedGroup, itemCount(), fluidCount(), genericCount())
		);
	}

	private static GroupCardViewModel buildViewModel(
		GroupDefinition group,
		int itemCount,
		int fluidCount,
		int genericCount
	) {
		GroupSource source = GroupSource.fromGroupId(group.id());
		String displayName = resolvedDisplayName(group);
		PreviewPaneModel preview = new PreviewPaneModel(
			group.id(),
			displayName,
			source,
			group.enabled(),
			itemCount,
			fluidCount,
			genericCount,
			List.of()
		);
		return new GroupCardViewModel(
			group.id(),
			displayName,
			source,
			group.enabled(),
			GroupActionEligibility.forSource(source),
			preview,
			false
		);
	}

	private static String resolvedDisplayName(GroupDefinition group) {
		String resolved = group.displayName().fallback();
		return resolved.isEmpty() ? group.id() : resolved;
	}
}
