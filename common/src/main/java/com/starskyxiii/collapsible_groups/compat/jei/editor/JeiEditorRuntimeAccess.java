package com.starskyxiii.collapsible_groups.compat.jei.editor;

import com.starskyxiii.collapsible_groups.client.editor.EditorFluidIngredientView;
import com.starskyxiii.collapsible_groups.client.editor.EditorGenericIngredientView;
import com.starskyxiii.collapsible_groups.client.editor.EditorRuntimeAccess;
import com.starskyxiii.collapsible_groups.client.editor.model.AppearanceDraft;
import com.starskyxiii.collapsible_groups.compat.jei.preview.GroupPreviewEntry;
import com.starskyxiii.collapsible_groups.compat.jei.preview.GroupPreviewTooltip;
import com.starskyxiii.collapsible_groups.compat.jei.runtime.EditorItemIndex;
import com.starskyxiii.collapsible_groups.compat.jei.runtime.EditorItemUniverseProvider;
import com.starskyxiii.collapsible_groups.compat.jei.runtime.GroupRegistry;
import com.starskyxiii.collapsible_groups.compat.jei.runtime.PerformanceTrace;
import com.starskyxiii.collapsible_groups.compat.jei.ui.GroupSampleRenderer;
import com.starskyxiii.collapsible_groups.group.GroupDefinition;
import com.starskyxiii.collapsible_groups.group.filter.GroupFilterEditorDraft;
import com.starskyxiii.collapsible_groups.ingredient.IngredientSearchQuery;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** JEI implementation of the neutral editor runtime boundary. */
public final class JeiEditorRuntimeAccess implements EditorRuntimeAccess {
	@Override
	public List<ItemStack> allItems() {
		return List.copyOf(EditorItemUniverseProvider.INSTANCE.allStacks());
	}

	@Override
	public List<EditorFluidIngredientView> allFluids(String traceName) {
		GroupRegistry.populateJeiCachesIfEmpty();
		return EditorFluidIngredientHelper.buildViews(GroupRegistry.getJeiAllFluids(), traceName);
	}

	@Override
	public List<EditorGenericIngredientView> allGenericIngredients(String traceName) {
		GroupRegistry.populateJeiCachesIfEmpty();
		return EditorGenericIngredientHelper.buildViews(GroupRegistry.getJeiAllGenericIngredients(), traceName);
	}

	@Override public List<GroupDefinition> allGroups() { return GroupRegistry.getAllIncludingKubeJs(); }
	@Override public Map<String, Set<String>> itemReverseIndex() { return GroupRegistry.getItemIdToGroupIds(); }
	@Override public Map<String, Set<String>> fluidReverseIndex() { return GroupRegistry.getFluidIdToGroupIds(); }

	@Override
	public List<EditorFluidIngredientView> filterFluids(List<EditorFluidIngredientView> entries,
		Map<EditorFluidIngredientView, List<String>> ownership, boolean hideUsed, IngredientSearchQuery query) {
		return EditorFluidIngredientHelper.filterViews(entries, ownership, hideUsed, query);
	}

	@Override
	public List<EditorGenericIngredientView> filterGeneric(List<EditorGenericIngredientView> entries,
		Map<EditorGenericIngredientView, List<String>> ownership, boolean hideUsed, IngredientSearchQuery query) {
		return EditorGenericIngredientHelper.filterViews(entries, ownership, hideUsed, query);
	}

	@Override
	public Map<EditorFluidIngredientView, List<String>> fluidOwnership(List<EditorFluidIngredientView> entries,
		Map<String, String> names, List<GroupDefinition> groups, Map<String, Set<String>> reverseIndex) {
		return EditorFluidIngredientHelper.buildOwnership(entries, names, groups, reverseIndex);
	}

	@Override
	public Map<EditorGenericIngredientView, List<String>> genericOwnership(List<EditorGenericIngredientView> entries,
		List<GroupDefinition> groups) {
		return EditorGenericIngredientHelper.buildOwnership(entries, groups);
	}

	@Override public void renderFluid(GuiGraphics g, EditorFluidIngredientView entry, int x, int y) {
		EditorFluidIngredientHelper.render(g, entry, x, y);
	}
	@Override public void renderGeneric(GuiGraphics g, EditorGenericIngredientView entry, int x, int y) {
		EditorGenericIngredientHelper.render(g, entry, x, y);
	}
	@Override public List<Component> fluidTooltip(EditorFluidIngredientView entry) {
		return EditorFluidIngredientHelper.tooltipLines(entry);
	}
	@Override public List<Component> genericTooltip(EditorGenericIngredientView entry) {
		return EditorGenericIngredientHelper.tooltipLines(entry);
	}

	@Override public List<ItemStack> resolveEditorDraftItems(GroupFilterEditorDraft draft, boolean enabled) {
		return GroupRegistry.resolveEditorDraftItems(draft, enabled);
	}
	@Override public List<ItemStack> resolveHybridEditorDraftItems(GroupFilterEditorDraft draft, boolean enabled) {
		return GroupRegistry.resolveHybridEditorDraftItems(draft, enabled);
	}
	@Override public List<ItemStack> resolveItems(GroupDefinition definition) { return GroupRegistry.resolveItems(definition); }
	@Override public List<EditorFluidIngredientView> resolveFluids(GroupDefinition definition, String traceName) {
		return EditorFluidIngredientHelper.buildViews(GroupRegistry.resolveFluids(definition), traceName);
	}
	@Override public List<EditorGenericIngredientView> resolveGenericIngredients(GroupDefinition definition, String traceName) {
		return EditorGenericIngredientHelper.buildViews(GroupRegistry.resolveGenericIngredients(definition), traceName);
	}
	@Override public boolean verifyItemIndex() { return EditorItemIndex.isVerifyEnabled(); }
	@Override public long beginTrace() { return PerformanceTrace.begin(); }
	@Override public void logIfSlow(String name, long startedAt, long thresholdMillis, String details) {
		PerformanceTrace.logIfSlow(name, startedAt, thresholdMillis, details);
	}

	@Override public Optional<GroupDefinition> findGroup(String id) { return GroupRegistry.findById(id); }
	@Override public void saveQuietly(GroupDefinition definition) { GroupRegistry.saveQuietly(definition); }
	@Override public String sanitizeGeneratedIdBase(String name) { return GroupRegistry.sanitizeGeneratedIdBase(name); }
	@Override public String generateUniqueId(String name) { return GroupRegistry.generateUniqueId(name); }
	@Override public String generateUniqueIdIncludingKubeJs(String name) {
		return GroupRegistry.generateUniqueIdIncludingKubeJs(name);
	}
	@Override public void invalidateFullMatchCache(String id) { GroupRegistry.invalidateFullMatchCache(id); }
	@Override public void populateFullMatchCacheFromSaved(GroupDefinition definition) {
		GroupRegistry.populateFullMatchCacheFromSaved(definition);
	}
	@Override public void notifyViewer() { GroupRegistry.notifyJei(); }
	@Override public void setEnabledQuietlyWithoutEvent(String id, boolean enabled) {
		GroupRegistry.setEnabledQuietlyWithoutEvent(id, enabled);
	}

	@Override
	public PreviewLayout renderPreview(GuiGraphics graphics, PreviewRect area, boolean expanded, int page,
		AppearanceDraft appearance, List<ItemStack> headerIcons, List<ItemStack> items, Font font,
		PreviewFallbacks fallbacks) {
		GroupSampleRenderer.Layout layout = GroupSampleRenderer.render(graphics, rect(area), expanded, page,
			appearance.toTheme(), headerIcons, items, font, new GroupSampleRenderer.Fallbacks(
				fallbacks.nameRgb(), fallbacks.collapsedHeaderArgb(), fallbacks.expandedHeaderArgb(),
				fallbacks.expandedGroupArgb(), fallbacks.expandedBorderArgb()));
		return layout(layout);
	}

	@Override
	public PreviewLayout layoutPreview(PreviewRect area, boolean expanded, int itemCount, int page) {
		return layout(GroupSampleRenderer.layout(rect(area), expanded, itemCount, page));
	}

	@Override
	public PreviewTooltip previewTooltip(String displayName, int nameColorRgb, int itemCount, int fluidCount,
		int genericCount, boolean expanded, List<PreviewEntry> entries) {
		List<GroupPreviewEntry> converted = new ArrayList<>(entries.size());
		for (PreviewEntry entry : entries) {
			converted.add(switch (entry.kind()) {
				case ITEM -> GroupPreviewEntry.ofItem((ItemStack) entry.value());
				case FLUID -> GroupPreviewEntry.ofFluid(((EditorFluidIngredientView) entry.value()).ingredient());
				case GENERIC -> {
					EditorGenericIngredientView generic = (EditorGenericIngredientView) entry.value();
					yield GroupPreviewEntry.ofGeneric(EditorGenericIngredientHelper.type(generic), generic.ingredient());
				}
			});
		}
		GroupPreviewTooltip.Result result = GroupPreviewTooltip.build(displayName, nameColorRgb, itemCount,
			fluidCount, genericCount, expanded, converted);
		return new PreviewTooltip(result.lines(), result.visual());
	}

	private static GroupSampleRenderer.Rect rect(PreviewRect rect) {
		return new GroupSampleRenderer.Rect(rect.x(), rect.y(), rect.width(), rect.height());
	}

	private static PreviewLayout layout(GroupSampleRenderer.Layout layout) {
		List<PreviewCell> cells = layout.cells().stream()
			.map(cell -> new PreviewCell(rect(cell.rect()), cell.itemIndex(), cell.header()))
			.toList();
		return new PreviewLayout(rect(layout.area()), rect(layout.headerCell()), rect(layout.previousPageButton()),
			rect(layout.nextPageButton()), cells, layout.page(), layout.pageCount(), layout.childCapacity(), layout.itemCount());
	}

	private static PreviewRect rect(GroupSampleRenderer.Rect rect) {
		return rect == null ? null : new PreviewRect(rect.x(), rect.y(), rect.width(), rect.height());
	}
}
