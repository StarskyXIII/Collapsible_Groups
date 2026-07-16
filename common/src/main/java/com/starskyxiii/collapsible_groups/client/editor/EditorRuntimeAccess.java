package com.starskyxiii.collapsible_groups.client.editor;

import com.starskyxiii.collapsible_groups.client.editor.model.AppearanceDraft;
import com.starskyxiii.collapsible_groups.group.GroupDefinition;
import com.starskyxiii.collapsible_groups.group.filter.GroupFilterEditorDraft;
import com.starskyxiii.collapsible_groups.ingredient.IngredientSearchQuery;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** JEI-backed operations consumed by the viewer-neutral group editor. */
public interface EditorRuntimeAccess {
	List<ItemStack> allItems();
	List<EditorFluidIngredientView> allFluids(String traceName);
	List<EditorGenericIngredientView> allGenericIngredients(String traceName);
	List<GroupDefinition> allGroups();
	Map<String, Set<String>> itemReverseIndex();
	Map<String, Set<String>> fluidReverseIndex();

	List<EditorFluidIngredientView> filterFluids(List<EditorFluidIngredientView> entries,
		Map<EditorFluidIngredientView, List<String>> ownership, boolean hideUsed, IngredientSearchQuery query);
	List<EditorGenericIngredientView> filterGeneric(List<EditorGenericIngredientView> entries,
		Map<EditorGenericIngredientView, List<String>> ownership, boolean hideUsed, IngredientSearchQuery query);
	Map<EditorFluidIngredientView, List<String>> fluidOwnership(List<EditorFluidIngredientView> entries,
		Map<String, String> groupNames, List<GroupDefinition> otherGroups, Map<String, Set<String>> reverseIndex);
	Map<EditorGenericIngredientView, List<String>> genericOwnership(List<EditorGenericIngredientView> entries,
		List<GroupDefinition> otherGroups);
	void renderFluid(GuiGraphics graphics, EditorFluidIngredientView entry, int x, int y);
	void renderGeneric(GuiGraphics graphics, EditorGenericIngredientView entry, int x, int y);
	List<Component> fluidTooltip(EditorFluidIngredientView entry);
	List<Component> genericTooltip(EditorGenericIngredientView entry);

	List<ItemStack> resolveEditorDraftItems(GroupFilterEditorDraft draft, boolean enabled);
	List<ItemStack> resolveHybridEditorDraftItems(GroupFilterEditorDraft draft, boolean enabled);
	List<ItemStack> resolveItems(GroupDefinition definition);
	List<EditorFluidIngredientView> resolveFluids(GroupDefinition definition, String traceName);
	List<EditorGenericIngredientView> resolveGenericIngredients(GroupDefinition definition, String traceName);
	boolean verifyItemIndex();
	long beginTrace();
	void logIfSlow(String name, long startedAt, long thresholdMillis, String details);

	Optional<GroupDefinition> findGroup(String id);
	void saveQuietly(GroupDefinition definition);
	String sanitizeGeneratedIdBase(String name);
	String generateUniqueId(String name);
	String generateUniqueIdIncludingKubeJs(String name);
	void invalidateFullMatchCache(String id);
	void populateFullMatchCacheFromSaved(GroupDefinition definition);
	void notifyViewer();
	void setEnabledQuietlyWithoutEvent(String id, boolean enabled);

	PreviewLayout renderPreview(GuiGraphics graphics, PreviewRect area, boolean expanded, int page,
		AppearanceDraft appearance, List<ItemStack> headerIcons, List<ItemStack> items, Font font,
		PreviewFallbacks fallbacks);
	PreviewLayout layoutPreview(PreviewRect area, boolean expanded, int itemCount, int page);
	PreviewTooltip previewTooltip(String displayName, int nameColorRgb, int itemCount, int fluidCount,
		int genericCount, boolean expanded, List<PreviewEntry> entries);

	record PreviewEntry(Kind kind, Object value) {
		public enum Kind { ITEM, FLUID, GENERIC }
		public static PreviewEntry item(ItemStack stack) { return new PreviewEntry(Kind.ITEM, stack); }
		public static PreviewEntry fluid(EditorFluidIngredientView view) { return new PreviewEntry(Kind.FLUID, view); }
		public static PreviewEntry generic(EditorGenericIngredientView view) { return new PreviewEntry(Kind.GENERIC, view); }
	}
	record PreviewFallbacks(int nameRgb, int collapsedHeaderArgb, int expandedHeaderArgb,
		int expandedGroupArgb, int expandedBorderArgb) {}
	record PreviewRect(int x, int y, int width, int height) {
		public int right() { return x + width; }
		public int bottom() { return y + height; }
		public boolean contains(double mouseX, double mouseY) {
			return mouseX >= x && mouseX < right() && mouseY >= y && mouseY < bottom();
		}
	}
	record PreviewCell(PreviewRect rect, int itemIndex, boolean header) {}
	record PreviewLayout(PreviewRect area, PreviewRect headerCell, @Nullable PreviewRect previousPageButton,
		@Nullable PreviewRect nextPageButton, List<PreviewCell> cells, int page, int pageCount,
		int childCapacity, int itemCount) {
		public boolean canPageBackward() { return page > 0; }
		public boolean canPageForward() { return page + 1 < pageCount; }
	}
	record PreviewTooltip(List<Component> lines, Optional<TooltipComponent> visual) {}
}
