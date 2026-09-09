package com.starskyxiii.collapsible_groups.client.editor;

import com.starskyxiii.collapsible_groups.group.GroupDefinition;
import com.starskyxiii.collapsible_groups.group.filter.GroupFilterEditorDraft;
import com.starskyxiii.collapsible_groups.ingredient.IngredientSearchQuery;
import com.starskyxiii.collapsible_groups.ingredient.TagQueryDiagnostics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

interface EditorIngredientAccess {
	default EditorIngredientTypes ingredientTypes() { return EditorIngredientTypes.UNAVAILABLE; }
	default void updateIngredientTags(String type) {}
	default EditorIngredientTags ingredientTags(String type) { return EditorIngredientTags.UNAVAILABLE; }
	default void cancelIngredientTags() {}
	default void updateIngredientIds(String type) {}
	default EditorIngredientIds ingredientIds(String type) { return EditorIngredientIds.UNAVAILABLE; }
	default void cancelIngredientIds() {}
	default TagQueryDiagnostics tagDiagnostics(String type, ResourceLocation tag) {
		return TagQueryDiagnostics.UNREPORTED;
	}
	List<ItemStack> allItems();
	List<EditorFluidIngredientView> allFluids(String traceName);
	List<EditorGenericIngredientView> allGenericIngredients(String traceName);
	Map<String, Set<String>> fluidReverseIndex();
	Map<ItemStack, List<String>> itemOwnership(List<ItemStack> entries, List<GroupDefinition> otherGroups);
	List<EditorFluidIngredientView> filterFluids(List<EditorFluidIngredientView> entries,
		Map<EditorFluidIngredientView, List<String>> ownership, boolean hideUsed, IngredientSearchQuery query);
	List<EditorGenericIngredientView> filterGeneric(List<EditorGenericIngredientView> entries,
		Map<EditorGenericIngredientView, List<String>> ownership, boolean hideUsed, IngredientSearchQuery query);
	Map<EditorFluidIngredientView, List<String>> fluidOwnership(List<EditorFluidIngredientView> entries,
		Map<String, String> groupNames, List<GroupDefinition> otherGroups, Map<String, Set<String>> reverseIndex);
	Map<EditorGenericIngredientView, List<String>> genericOwnership(List<EditorGenericIngredientView> entries,
		List<GroupDefinition> otherGroups);
	List<ItemStack> resolveEditorDraftItems(GroupFilterEditorDraft draft, boolean enabled);
	List<ItemStack> resolveHybridEditorDraftItems(GroupFilterEditorDraft draft, boolean enabled);
	default List<ItemStack> resolvePreviewItems(GroupDefinition prepared, GroupFilterEditorDraft draft, boolean indexed) {
		return indexed ? resolveEditorDraftItems(draft, prepared.enabled())
			: resolveHybridEditorDraftItems(draft, prepared.enabled());
	}
	List<ItemStack> resolveItems(GroupDefinition definition);
	List<EditorFluidIngredientView> resolveFluids(GroupDefinition definition, String traceName);
	List<EditorGenericIngredientView> resolveGenericIngredients(GroupDefinition definition, String traceName);
	CompletableFuture<Void> prepareEditorEntry(GroupDefinition definition);
	List<ItemStack> cachedFullMatchItems(GroupDefinition definition);
	List<EditorFluidIngredientView> cachedFullMatchFluids(GroupDefinition definition, String traceName);
	List<EditorGenericIngredientView> cachedFullMatchGeneric(GroupDefinition definition, String traceName);
	boolean verifyItemIndex();
	long beginTrace();
	void logIfSlow(String name, long startedAt, long thresholdMillis, String details);
}
