package com.starskyxiii.collapsible_groups.client.editor;

import com.starskyxiii.collapsible_groups.client.editor.EditorRuntimeServices;

import com.starskyxiii.collapsible_groups.client.editor.EditorFluidIngredientView;
import com.starskyxiii.collapsible_groups.client.editor.EditorGenericIngredientView;
import com.starskyxiii.collapsible_groups.i18n.ModTranslationKeys;
import com.starskyxiii.collapsible_groups.client.widget.EditorShellLayout;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds and renders hover-tooltips for both panels of {@link GroupEditorScreen}.
 */
final class GroupEditorTooltipHelper {

	private GroupEditorTooltipHelper() {}

	static void render(GuiGraphics g, int mouseX, int mouseY,
	                   EditorLeftPanel left, EditorRightPanel right,
	                   GroupEditorState state, Font font, EditorShellLayout shell,
	                   boolean searchFieldVisible) {
		if (shouldShowSearchSyntaxTooltip(searchFieldVisible, shell.searchField(), mouseX, mouseY)) {
			g.renderComponentTooltip(font, searchSyntaxLines(), mouseX, mouseY);
			return;
		}

		// --- Left panel ---
		if (left.hoveredItem >= 0 && left.hoveredItem < left.filteredItems().size()) {
			ItemStack stack = left.filteredItems().get(left.hoveredItem);
			List<Component> lines = new ArrayList<>(EditorItemTooltipHelper.tooltipLines(stack));
			appendOtherGroups(lines, left.otherGroupsForItem(stack));
			if (left.isShowingItems()) appendItemHint(lines, state, stack);
			g.renderComponentTooltip(font, lines, mouseX, mouseY);
			return;
		}
		if (left.hoveredFluid >= 0 && left.hoveredFluid < left.filteredFluids().size()) {
			EditorFluidIngredientView fluid = left.filteredFluids().get(left.hoveredFluid);
			List<Component> lines = EditorRuntimeServices.get().fluidTooltip(fluid);
			appendOtherGroups(lines, left.otherGroupsForFluid(fluid));
			if (!state.canEditContents()) {
				lines.add(dim(ModTranslationKeys.EDITOR_RULES_CONTENTS_LOCKED));
			} else if (!state.isFluidSelected(fluidIngredient(fluid))
				&& state.isFluidRuleCovered(EditorRuleCoverageKeys.fluidKey(fluid))) {
				lines.add(ruleCovered());
			} else if (state.isFluidSelected(fluidIngredient(fluid))) {
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
			EditorGenericIngredientView entry = left.filteredGeneric().get(left.hoveredGeneric);
			List<Component> lines = EditorRuntimeServices.get().genericTooltip(entry);
			appendOtherGroups(lines, left.otherGroupsForGeneric(entry));
			if (!state.canEditContents()) {
				lines.add(dim(ModTranslationKeys.EDITOR_RULES_CONTENTS_LOCKED));
			} else if (!state.isGenericSelected(entry)
				&& state.isGenericRuleCovered(EditorRuleCoverageKeys.genericKey(entry))) {
				lines.add(ruleCovered());
			} else if (state.isGenericSelected(entry)) {
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
			List<Component> lines = new ArrayList<>(EditorItemTooltipHelper.tooltipLines(stack));
			if (!state.canEditContents()) lines.add(dim(ModTranslationKeys.EDITOR_RULES_CONTENTS_LOCKED));
			else if (!isExact && !isWhole) lines.add(dim(ModTranslationKeys.EDITOR_TAG_MATCHED));
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
			EditorFluidIngredientView fluid = right.groupFluids().get(right.hoveredFluid);
			List<Component> lines = EditorRuntimeServices.get().fluidTooltip(fluid);
			if (!state.canEditContents()) lines.add(dim(ModTranslationKeys.EDITOR_RULES_CONTENTS_LOCKED));
			else if (state.isFluidSelected(fluidIngredient(fluid))) lines.add(hint(ModTranslationKeys.EDITOR_HINT_CLICK_REMOVE_FROM_GROUP));
			else lines.add(dim(ModTranslationKeys.EDITOR_TAG_MATCHED));
			g.renderComponentTooltip(font, lines, mouseX, mouseY);
			return;
		}
		if (right.hoveredGeneric >= 0 && right.hoveredGeneric < right.groupGeneric().size()) {
			EditorGenericIngredientView entry = right.groupGeneric().get(right.hoveredGeneric);
			List<Component> lines = EditorRuntimeServices.get().genericTooltip(entry);
			if (!state.canEditContents()) lines.add(dim(ModTranslationKeys.EDITOR_RULES_CONTENTS_LOCKED));
			else if (state.isGenericSelected(entry)) lines.add(hint(ModTranslationKeys.EDITOR_HINT_CLICK_REMOVE_FROM_GROUP));
			else if (state.isGenericTagMatched(entry)) lines.add(dim(ModTranslationKeys.EDITOR_TAG_MATCHED));
			g.renderComponentTooltip(font, lines, mouseX, mouseY);
		}
	}

	static boolean shouldShowSearchSyntaxTooltip(boolean searchFieldVisible, EditorShellLayout.Rect bounds,
		int mouseX, int mouseY) {
		return searchFieldVisible && bounds.contains(mouseX, mouseY);
	}

	static List<Component> searchSyntaxLines() {
		return java.util.Arrays.stream(Component.translatable(ModTranslationKeys.EDITOR_SEARCH_SYNTAX_TOOLTIP)
			.getString().split("\\n", -1)).<Component>map(Component::literal).toList();
	}

	private static void appendItemHint(List<Component> lines, GroupEditorState state, ItemStack stack) {
		if (!state.canEditContents()) {
			lines.add(dim(ModTranslationKeys.EDITOR_RULES_CONTENTS_LOCKED));
		} else if (!state.isWholeItemSelected(stack) && !state.isExactSelected(stack)
			&& state.itemRuleCoverageKey(stack).map(state::isItemRuleCovered).orElse(false)) {
			lines.add(ruleCovered());
		} else if (state.isWholeItemSelected(stack)) {
			// a component-less whole selection toggles off on a plain click; only items that
			// carry a component patch switch to an exact variant on the next click.
			lines.add(hint(stack.getComponentsPatch().isEmpty()
				? ModTranslationKeys.EDITOR_HINT_CLICK_REMOVE_FROM_GROUP
				: ModTranslationKeys.EDITOR_HINT_SWITCH_TO_VARIANT));
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

	/**
	 * overlap tooltip: the ownership caches carry the single JEI winner
	 * (winner semantics, see EditorGroupOwnershipHelper), so this names the group
	 * that actually displays the ingredient - decided by priority - and stays a
	 * neutral hint (the cell remains clickable to add to the current group).
	 */
	private static void appendOtherGroups(List<Component> lines, List<String> groups) {
		if (groups.isEmpty()) return;
		lines.add(Component.translatable(ModTranslationKeys.EDITOR_OVERLAP_SHOWN_BY,
			Component.literal(groups.getFirst()).withStyle(ChatFormatting.YELLOW))
			.withStyle(ChatFormatting.GOLD));
	}

	/** rule-covered hint — green italic, matching the cell's green visual, pointing at the rules mode. */
	private static Component ruleCovered() {
		return Component.translatable(ModTranslationKeys.EDITOR_RULE_COVERED).withStyle(ChatFormatting.GREEN, ChatFormatting.ITALIC);
	}

	private static Component hint(String key)  { return Component.translatable(key).withStyle(ChatFormatting.GRAY,      ChatFormatting.ITALIC); }
	private static Component hint2(String key) { return Component.translatable(key).withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC); }
	private static Component dim(String key)   { return Component.translatable(key).withStyle(ChatFormatting.DARK_GRAY); }

	private static Object fluidIngredient(EditorFluidIngredientView fluid) {
		return fluid.ingredient();
	}
}
