package com.starskyxiii.collapsible_groups.compat.emi;

import com.starskyxiii.collapsible_groups.group.GroupDefinition;
import com.starskyxiii.collapsible_groups.group.filter.GroupFilter;
import com.starskyxiii.collapsible_groups.group.filter.GroupFilterEditorDraft;
import com.starskyxiii.collapsible_groups.ingredient.ItemStackIngredientView;
import com.starskyxiii.collapsible_groups.viewer.ViewerIngredient;
import com.starskyxiii.collapsible_groups.viewer.ViewerIngredientIdentity;
import com.starskyxiii.collapsible_groups.viewer.ViewerIngredientUniverse;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EmiDisabledEditorPreviewTest {
	@Test void disabledLivePreviewIsEmptyButPreparedPreviewSurvivesToggleAndReopen() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		ItemStack stone = new ItemStack(Items.STONE);
		var universe = new ViewerIngredientUniverse<EmiIngredient>(List.of(new ViewerIngredient<>(
			new ViewerIngredientIdentity("item", "stone"), ViewerIngredient.Kind.ITEM,
			EmiStack.of(stone), new ItemStackIngredientView(stone))));
		var group = new GroupDefinition("stone", "Stone", false, new GroupFilter.Id("item", "minecraft:stone"));
		var index = new EmiViewerGroupIndex(Runnable::run);
		index.requestRebuild(1, universe, List.of(group));
		var runtime = new EmiEditorRuntimeAccess(null, index);
		assertEquals(1, runtime.cachedFullMatchItems(group).size());
		assertTrue(runtime.resolveItems(group).isEmpty());
		assertTrue(runtime.resolveFluids(group, "test").isEmpty());
		assertTrue(runtime.resolveGenericIngredients(group, "test").isEmpty());
		var draft = GroupFilterEditorDraft.decode(group.filter()).draft();
		assertTrue(runtime.resolveEditorDraftItems(draft, false).isEmpty());
		assertTrue(runtime.resolveHybridEditorDraftItems(draft, false).isEmpty());
		assertTrue(runtime.resolvePreviewItems(group, draft, true).isEmpty());
		assertTrue(runtime.resolvePreviewItems(group, draft, false).isEmpty());
		var enabled = new GroupDefinition("stone", "Stone", true, group.filter());
		var emptyDraft = GroupFilterEditorDraft.decode(new GroupFilter.Id("item", "minecraft:dirt")).draft();
		assertEquals(runtime.resolveItems(enabled), runtime.resolvePreviewItems(enabled, emptyDraft, true));
		assertEquals(runtime.resolveItems(enabled), runtime.resolvePreviewItems(enabled, emptyDraft, false));
		assertEquals(1, runtime.resolveEditorDraftItems(draft, true).size());
		assertTrue(runtime.resolveEditorDraftItems(draft, false).isEmpty());
		runtime.closeEditor();
		index.prepareFullMatch(group);
		assertEquals(1, runtime.cachedFullMatchItems(group).size());
	}
}
