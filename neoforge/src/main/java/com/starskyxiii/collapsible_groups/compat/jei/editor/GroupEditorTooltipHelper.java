package com.starskyxiii.collapsible_groups.compat.jei.editor;

import com.starskyxiii.collapsible_groups.compat.jei.data.GenericIngredientView;
import com.starskyxiii.collapsible_groups.i18n.ModTranslationKeys;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds and renders hover-tooltips for both panels of {@link GroupEditorScreen}.
 * All methods are static; the class is never instantiated.
 */
final class GroupEditorTooltipHelper {

	private GroupEditorTooltipHelper() {}

	static void render(GuiGraphics g, int mouseX, int mouseY,
	                   EditorLeftPanel left, EditorRightPanel right,
	                   GroupEditorState state, Font font) {

		// --- Left panel ---
		if (left.hoveredItem >= 0 && left.hoveredItem < left.filteredItems().size()) {
			ItemStack stack = left.filteredItems().get(left.hoveredItem);
			List<Component> lines = new ArrayList<>(stack.getTooltipLines(Item.TooltipContext.EMPTY, null, TooltipFlag.Default.NORMAL));
			appendOtherGroups(lines, left.otherGroupsForItem(stack));
			if (left.isShowingItems()) appendItemHint(lines, state, stack);
			g.renderComponentTooltip(font, lines, mouseX, mouseY);
			return;
		}
		if (left.hoveredFluid >= 0 && left.hoveredFluid < left.filteredFluids().size()) {
			FluidStack fluid = left.filteredFluids().get(left.hoveredFluid);
			List<Component> lines = buildFluidLines(fluid);
			appendOtherGroups(lines, left.otherGroupsForFluid(fluid));
			if (state.isFluidSelected(fluid)) {
				lines.add(hint(ModTranslationKeys.EDITOR_HINT_CLICK_REMOVE_FROM_GROUP));
				lines.add(hint2(ModTranslationKeys.EDITOR_HINT_DRAG_REMOVE_FLUIDS));
			} else {
				lines.add(hint(ModTranslationKeys.EDITOR_HINT_CLICK_ADD_TO_GROUP));
				lines.add(hint2(ModTranslationKeys.EDITOR_HINT_DRAG_ADD_FLUIDS));
			}
			g.renderComponentTooltip(font, lines, mouseX, mouseY);
			return;
		}
		if (left.hoveredGeneric >= 0 && left.hoveredGeneric < left.filteredGeneric().size()) {
			GenericIngredientView entry = left.filteredGeneric().get(left.hoveredGeneric);
			List<Component> lines = buildGenericLines(entry);
			appendOtherGroups(lines, left.otherGroupsForGeneric(entry));
			if (state.isGenericSelected(entry)) {
				lines.add(hint(ModTranslationKeys.EDITOR_HINT_CLICK_REMOVE_FROM_GROUP));
				lines.add(hint2(ModTranslationKeys.EDITOR_HINT_DRAG_REMOVE_ENTRIES));
			} else {
				lines.add(hint(ModTranslationKeys.EDITOR_HINT_CLICK_ADD_TO_GROUP));
				lines.add(hint2(ModTranslationKeys.EDITOR_HINT_DRAG_ADD_ENTRIES));
			}
			g.renderComponentTooltip(font, lines, mouseX, mouseY);
			return;
		}

		// --- Right panel ---
		if (right.hoveredItem >= 0 && right.hoveredItem < right.groupItems().size()) {
			ItemStack stack = right.groupItems().get(right.hoveredItem);
			boolean isExact = state.isExactSelected(stack);
			boolean isWhole = state.isWholeItemSelected(stack);
			List<Component> lines = new ArrayList<>(stack.getTooltipLines(Item.TooltipContext.EMPTY, null, TooltipFlag.Default.NORMAL));
			if (!isExact && !isWhole) lines.add(dim(ModTranslationKeys.EDITOR_TAG_MATCHED));
			else if (isWhole) {
				lines.add(hint(ModTranslationKeys.EDITOR_HINT_REMOVE_ONLY_VARIANT));
				lines.add(hint2(ModTranslationKeys.EDITOR_HINT_CTRL_REMOVE_ALL));
			} else {
				lines.add(hint(ModTranslationKeys.EDITOR_HINT_REMOVE_THIS));
				lines.add(hint2(ModTranslationKeys.EDITOR_HINT_CTRL_REMOVE_ALL));
			}
			g.renderComponentTooltip(font, lines, mouseX, mouseY);
			return;
		}
		if (right.hoveredFluid >= 0 && right.hoveredFluid < right.groupFluids().size()) {
			FluidStack fluid = (FluidStack) right.groupFluids().get(right.hoveredFluid);
			List<Component> lines = buildFluidLines(fluid);
			if (state.isFluidSelected(fluid)) lines.add(hint(ModTranslationKeys.EDITOR_HINT_CLICK_REMOVE_FROM_GROUP));
			else                              lines.add(dim(ModTranslationKeys.EDITOR_TAG_MATCHED));
			g.renderComponentTooltip(font, lines, mouseX, mouseY);
			return;
		}
		if (right.hoveredGeneric >= 0 && right.hoveredGeneric < right.groupGeneric().size()) {
			GenericIngredientView entry = right.groupGeneric().get(right.hoveredGeneric);
			List<Component> lines = buildGenericLines(entry);
			if (state.isGenericSelected(entry))        lines.add(hint(ModTranslationKeys.EDITOR_HINT_CLICK_REMOVE_FROM_GROUP));
			else if (state.isGenericTagMatched(entry)) lines.add(dim(ModTranslationKeys.EDITOR_TAG_MATCHED));
			g.renderComponentTooltip(font, lines, mouseX, mouseY);
		}
	}

	// -----------------------------------------------------------------------
	// Left panel item hints
	// -----------------------------------------------------------------------

	private static void appendItemHint(List<Component> lines, GroupEditorState state, ItemStack stack) {
		if (state.isWholeItemSelected(stack)) {
			lines.add(hint(ModTranslationKeys.EDITOR_HINT_SWITCH_TO_VARIANT));
			lines.add(hint2(ModTranslationKeys.EDITOR_HINT_DRAG_REMOVE));
			lines.add(hint2(ModTranslationKeys.EDITOR_HINT_CTRL_REMOVE_ALL));
		} else if (state.isExactSelected(stack)) {
			lines.add(hint(ModTranslationKeys.EDITOR_HINT_REMOVE_THIS));
			lines.add(hint2(ModTranslationKeys.EDITOR_HINT_DRAG_REMOVE));
			lines.add(hint2(ModTranslationKeys.EDITOR_HINT_CTRL_SELECT_ALL));
		} else {
			lines.add(hint(ModTranslationKeys.EDITOR_HINT_ADD_THIS));
			lines.add(hint2(ModTranslationKeys.EDITOR_HINT_DRAG_ADD));
			lines.add(hint2(ModTranslationKeys.EDITOR_HINT_CTRL_ADD_ALL));
		}
	}

	// -----------------------------------------------------------------------
	// Shared tooltip helpers
	// -----------------------------------------------------------------------

	private static void appendOtherGroups(List<Component> lines, List<String> groups) {
		if (groups.isEmpty()) return;
		if (groups.size() == 1) {
			lines.add(Component.translatable(ModTranslationKeys.EDITOR_ALREADY_IN_GROUP).withStyle(ChatFormatting.GOLD)
				.append(Component.literal(groups.getFirst()).withStyle(ChatFormatting.YELLOW)));
			return;
		}
		lines.add(Component.translatable(ModTranslationKeys.EDITOR_ALREADY_IN_GROUPS).withStyle(ChatFormatting.GOLD));
		int limit = Math.min(4, groups.size());
		for (int i = 0; i < limit; i++)
			lines.add(Component.literal("- " + groups.get(i)).withStyle(ChatFormatting.YELLOW));
		if (groups.size() > limit)
			lines.add(Component.translatable(ModTranslationKeys.EDITOR_MORE_GROUPS, groups.size() - limit).withStyle(ChatFormatting.DARK_GRAY));
	}

	private static List<Component> buildFluidLines(FluidStack fluid) {
		List<Component> lines = new ArrayList<>();
		lines.add(fluid.getHoverName());
		lines.add(Component.literal(BuiltInRegistries.FLUID.getKey(fluid.getFluid()).toString())
			.withStyle(ChatFormatting.DARK_GRAY));
		return lines;
	}

	private static List<Component> buildGenericLines(GenericIngredientView entry) {
		List<Component> lines = new ArrayList<>();
		lines.add(entry.displayName());
		lines.add(Component.literal(entry.resourceId()).withStyle(ChatFormatting.DARK_GRAY));
		lines.add(Component.literal(entry.typeId()).withStyle(ChatFormatting.GRAY));
		return lines;
	}

	private static Component hint(String key)  { return Component.translatable(key).withStyle(ChatFormatting.GRAY,      ChatFormatting.ITALIC); }
	private static Component hint2(String key) { return Component.translatable(key).withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC); }
	private static Component dim(String key)   { return Component.translatable(key).withStyle(ChatFormatting.DARK_GRAY); }
}
