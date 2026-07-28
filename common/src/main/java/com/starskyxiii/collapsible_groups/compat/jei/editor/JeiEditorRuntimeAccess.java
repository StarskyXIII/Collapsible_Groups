package com.starskyxiii.collapsible_groups.compat.jei.editor;

import com.starskyxiii.collapsible_groups.client.editor.EditorFluidIngredientView;
import com.starskyxiii.collapsible_groups.client.editor.EditorGenericIngredientView;
import com.starskyxiii.collapsible_groups.client.editor.EditorRuntimeAccess;
import com.starskyxiii.collapsible_groups.client.editor.model.AppearanceDraft;
import com.starskyxiii.collapsible_groups.client.preview.GroupPreviewEntry;
import com.starskyxiii.collapsible_groups.compat.jei.preview.JeiGroupPreviewEntries;
import com.starskyxiii.collapsible_groups.client.preview.GroupPreviewTooltip;
import com.starskyxiii.collapsible_groups.compat.jei.JeiViewerGroupIndex;
import com.starskyxiii.collapsible_groups.compat.jei.runtime.EditorItemIndex;
import com.starskyxiii.collapsible_groups.compat.jei.runtime.EditorItemUniverseProvider;
import com.starskyxiii.collapsible_groups.compat.jei.runtime.GroupRegistry;
import com.starskyxiii.collapsible_groups.compat.jei.runtime.PerformanceTrace;
import com.starskyxiii.collapsible_groups.client.preview.GroupSampleRenderer;
import com.starskyxiii.collapsible_groups.group.GroupDefinition;
import com.starskyxiii.collapsible_groups.group.GroupIconDefinition;
import com.starskyxiii.collapsible_groups.group.filter.GroupFilterEditorDraft;
import com.starskyxiii.collapsible_groups.ingredient.IngredientSearchQuery;
import com.starskyxiii.collapsible_groups.viewer.ViewerIngredientIdentity;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/** JEI implementation of the neutral editor runtime boundary. */
public class JeiEditorRuntimeAccess implements EditorRuntimeAccess {
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
		Map<String, GroupDefinition> byId = new java.util.LinkedHashMap<>();
		groups.forEach(group -> byId.put(group.id(), group));
		Map<ViewerIngredientIdentity, String> resolved = JeiViewerGroupIndex.instance().resolveOwnership(groups);
		Map<EditorGenericIngredientView, List<String>> ownership = new java.util.IdentityHashMap<>();
		for (EditorGenericIngredientView entry : entries) {
			String groupId = resolved.get(
				new ViewerIngredientIdentity(entry.typeId(), EditorGenericIngredientHelper.identityValueId(entry)));
			GroupDefinition owner = byId.get(groupId);
			if (owner != null) ownership.put(entry,
				List.of(com.starskyxiii.collapsible_groups.client.editor.EditorGroupOwnershipHelper.displayName(owner)));
		}
		return ownership;
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
	@Override
	public CompletableFuture<Void> prepareEditorEntry(GroupDefinition definition) {
		long startedAt = PerformanceTrace.begin();
		return JeiViewerGroupIndex.instance().prepareEditorAsync(GroupRegistry.getAllIncludingKubeJs(), () -> {
			GroupRegistry.warmEditorItemIndex();
			GroupRegistry.populateFullMatchCacheFromSaved(definition);
			// EditorRightPanel consumes all three maps atomically; empty is ready, null is loading.
			JeiViewerGroupIndex.instance().ensureFullMatchItems();
			JeiViewerGroupIndex.instance().ensureFullMatchFluids();
			JeiViewerGroupIndex.instance().ensureFullMatchGeneric();
		}).whenComplete((ignored, error) -> PerformanceTrace.logIfSlow("GroupEditorScreen.entry", startedAt, 0,
			"group=" + definition.id() + " ready=" + (error == null)
				+ " elapsedMillis=" + PerformanceTrace.elapsedMillis(startedAt)));
	}
	@Override public List<ItemStack> cachedFullMatchItems(GroupDefinition definition) {
		return GroupRegistry.getFullMatchItemsCached(definition.id());
	}
	@Override public List<EditorFluidIngredientView> cachedFullMatchFluids(GroupDefinition definition, String traceName) {
		List<Object> values = GroupRegistry.getFullMatchFluidsCached(definition.id());
		return values == null ? null : EditorFluidIngredientHelper.buildViews(values, traceName);
	}
	@Override public List<EditorGenericIngredientView> cachedFullMatchGeneric(GroupDefinition definition, String traceName) {
		List<com.starskyxiii.collapsible_groups.compat.jei.data.GenericIngredientRef> values =
			GroupRegistry.getFullMatchGenericCached(definition.id());
		return values == null ? null : EditorGenericIngredientHelper.buildViews(values, traceName);
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
	public List<PreviewEntry> resolveHeaderIcons(List<GroupIconDefinition> iconIds, List<PreviewEntry> fallbackEntries) {
		List<mezz.jei.api.ingredients.ITypedIngredient<?>> resolved =
			com.starskyxiii.collapsible_groups.compat.jei.JeiViewerAdapter.instance()
				.resolveHeaderIconIngredients(iconIds);
		if (resolved.isEmpty()) return fallbackEntries.stream().limit(2).toList();
		List<PreviewEntry> entries = new ArrayList<>(resolved.size());
		for (mezz.jei.api.ingredients.ITypedIngredient<?> typed : resolved) {
			typed.getItemStack().ifPresentOrElse(stack -> entries.add(PreviewEntry.item(stack)), () -> {
				Object fluid = com.starskyxiii.collapsible_groups.compat.jei.preview.PreviewIngredientRenderer
					.getFluidIngredient(typed);
				if (fluid != null) {
					List<EditorFluidIngredientView> views = EditorFluidIngredientHelper.buildViews(
						List.of(fluid), "EditorHeaderIcon.fluid");
					if (!views.isEmpty()) entries.add(PreviewEntry.fluid(views.get(0)));
				} else {
					@SuppressWarnings("unchecked")
					mezz.jei.api.ingredients.IIngredientType<Object> type =
						(mezz.jei.api.ingredients.IIngredientType<Object>) typed.getType();
					String typeId = com.starskyxiii.collapsible_groups.compat.jei.JeiIngredientTypes
						.getCanonicalId(type);
					if (typeId == null || typeId.isBlank()) {
						String uid = type.getUid();
						typeId = uid == null || uid.isBlank()
							? "jei:" + type.getIngredientClass().getName()
							: uid;
					}
					List<EditorGenericIngredientView> views = EditorGenericIngredientHelper.buildViews(
						List.of(new com.starskyxiii.collapsible_groups.compat.jei.data.GenericIngredientRef(
							typeId, type, typed.getIngredient())), "EditorHeaderIcon.generic");
					if (!views.isEmpty()) entries.add(PreviewEntry.generic(views.get(0)));
				}
			});
		}
		return List.copyOf(entries);
	}

	@Override
	public PreviewLayout renderPreview(GuiGraphics graphics, PreviewRect area, boolean expanded, int page,
		AppearanceDraft appearance, List<PreviewEntry> headerIcons, List<PreviewEntry> items, Font font,
		PreviewFallbacks fallbacks) {
		List<GroupPreviewEntry> convertedHeaders = convertPreviewEntries(headerIcons);
		List<GroupPreviewEntry> convertedItems = convertPreviewEntries(items);
		GroupSampleRenderer.Layout layout = GroupSampleRenderer.render(graphics, rect(area), expanded, page,
			appearance.toTheme(), convertedHeaders, convertedItems, font, new GroupSampleRenderer.Fallbacks(
				fallbacks.nameRgb(), fallbacks.collapsedHeaderArgb(), fallbacks.expandedHeaderArgb(),
				fallbacks.expandedGroupArgb(), fallbacks.expandedBorderArgb()));
		return layout(layout);
	}

	private static List<GroupPreviewEntry> convertPreviewEntries(List<PreviewEntry> entries) {
		List<GroupPreviewEntry> converted = new ArrayList<>(entries.size());
		for (PreviewEntry entry : entries) {
			converted.add(switch (entry.kind()) {
				case ITEM -> GroupPreviewEntry.ofItem((ItemStack) entry.value());
				case FLUID -> JeiGroupPreviewEntries.ofFluid(((EditorFluidIngredientView) entry.value()).ingredient());
				case GENERIC -> {
					EditorGenericIngredientView generic = (EditorGenericIngredientView) entry.value();
					yield JeiGroupPreviewEntries.ofGeneric(EditorGenericIngredientHelper.type(generic), generic.ingredient());
				}
			});
		}
		return List.copyOf(converted);
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
				case FLUID -> JeiGroupPreviewEntries.ofFluid(((EditorFluidIngredientView) entry.value()).ingredient());
				case GENERIC -> {
					EditorGenericIngredientView generic = (EditorGenericIngredientView) entry.value();
					yield JeiGroupPreviewEntries.ofGeneric(EditorGenericIngredientHelper.type(generic), generic.ingredient());
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
