package com.starskyxiii.collapsible_groups.compat.emi;

import com.starskyxiii.collapsible_groups.Constants;
import com.starskyxiii.collapsible_groups.client.editor.EditorFluidIngredientView;
import com.starskyxiii.collapsible_groups.client.editor.EditorGenericIngredientView;
import com.starskyxiii.collapsible_groups.client.editor.EditorGroupOwnershipHelper;
import com.starskyxiii.collapsible_groups.client.editor.EditorRuntimeAccess;
import com.starskyxiii.collapsible_groups.client.editor.model.AppearanceDraft;
import com.starskyxiii.collapsible_groups.client.preview.GroupPreviewEntry;
import com.starskyxiii.collapsible_groups.client.preview.GroupPreviewTooltip;
import com.starskyxiii.collapsible_groups.client.preview.GroupSampleRenderer;
import com.starskyxiii.collapsible_groups.group.GroupDefinition;
import com.starskyxiii.collapsible_groups.group.GroupIconDefinition;
import com.starskyxiii.collapsible_groups.group.GroupRepository;
import com.starskyxiii.collapsible_groups.group.filter.GroupFilterEditorDraft;
import com.starskyxiii.collapsible_groups.ingredient.IngredientSearchDocument;
import com.starskyxiii.collapsible_groups.ingredient.IngredientSearchQuery;
import com.starskyxiii.collapsible_groups.viewer.ViewerIngredient;
import com.starskyxiii.collapsible_groups.viewer.ViewerIngredientIdentity;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/** EMI implementation of the editor boundary. No JEI runtime object is consulted. */
final class EmiEditorRuntimeAccess implements EditorRuntimeAccess {
	private final EmiViewerAdapter adapter;
	private final EmiViewerGroupIndex index;

	EmiEditorRuntimeAccess(EmiViewerAdapter adapter, EmiViewerGroupIndex index) {
		this.adapter = adapter;
		this.index = index;
	}

	@Override public List<ItemStack> allItems() {
		return adapter.editorDisplayIngredients().stream()
			.filter(value -> value.kind() == ViewerIngredient.Kind.ITEM)
			.map(ViewerIngredient::entry).map(EmiIngredient::getEmiStacks).map(values -> values.get(0).getItemStack())
			.filter(stack -> !stack.isEmpty()).toList();
	}

	@Override public List<EditorFluidIngredientView> allFluids(String traceName) {
		return fluidViews(adapter.editorDisplayIngredients().stream()
			.filter(value -> value.kind() == ViewerIngredient.Kind.FLUID).toList());
	}

	@Override public List<EditorGenericIngredientView> allGenericIngredients(String traceName) {
		return genericViews(adapter.editorDisplayIngredients().stream()
			.filter(value -> value.kind() == ViewerIngredient.Kind.GENERIC).toList());
	}

	@Override public List<GroupDefinition> allGroups() {
		return GroupRepository.getAllIncludingScripted();
	}

	@Override public Map<String, Set<String>> itemReverseIndex() { return reverseIndex(ViewerIngredient.Kind.ITEM); }
	@Override public Map<String, Set<String>> fluidReverseIndex() { return reverseIndex(ViewerIngredient.Kind.FLUID); }

	private Map<String, Set<String>> reverseIndex(ViewerIngredient.Kind kind) {
		Map<ViewerIngredientIdentity, String> ownership = index.resolveOwnership(allGroups());
		Map<String, Set<String>> result = new LinkedHashMap<>();
		for (ViewerIngredient<EmiIngredient> ingredient : adapter.bootstrapContext().universe().ordered()) {
			if (ingredient.kind() != kind) continue;
			String owner = ownership.get(ingredient.identity());
			if (owner == null || ingredient.view().resourceLocation() == null) continue;
			result.computeIfAbsent(ingredient.view().resourceLocation().toString(), ignored -> new LinkedHashSet<>())
				.add(owner);
		}
		return result;
	}

	@Override public List<EditorFluidIngredientView> filterFluids(List<EditorFluidIngredientView> entries,
		Map<EditorFluidIngredientView, List<String>> ownership, boolean hideUsed, IngredientSearchQuery query) {
		return entries.stream().filter(entry -> (!hideUsed || ownership.getOrDefault(entry, List.of()).isEmpty())
			&& query.matches(entry.searchDocument())).toList();
	}

	@Override public List<EditorGenericIngredientView> filterGeneric(List<EditorGenericIngredientView> entries,
		Map<EditorGenericIngredientView, List<String>> ownership, boolean hideUsed, IngredientSearchQuery query) {
		return entries.stream().filter(entry -> (!hideUsed || ownership.getOrDefault(entry, List.of()).isEmpty())
			&& query.matches(entry.searchDocument())).toList();
	}

	@Override public Map<EditorFluidIngredientView, List<String>> fluidOwnership(
		List<EditorFluidIngredientView> entries, Map<String, String> names, List<GroupDefinition> groups,
		Map<String, Set<String>> reverseIndex) {
		return ownership(entries, groups, entry -> adapter.identityFor((EmiStack) entry.ingredient()));
	}

	@Override public Map<EditorGenericIngredientView, List<String>> genericOwnership(
		List<EditorGenericIngredientView> entries, List<GroupDefinition> groups) {
		return ownership(entries, groups,
			entry -> new ViewerIngredientIdentity(entry.typeId(), entry.identityValueId()));
	}

	private <T> Map<T, List<String>> ownership(List<T> entries, List<GroupDefinition> groups,
		java.util.function.Function<T, ViewerIngredientIdentity> identity) {
		Map<String, GroupDefinition> byId = new LinkedHashMap<>();
		groups.forEach(group -> byId.put(group.id(), group));
		Map<ViewerIngredientIdentity, String> resolved = index.resolveOwnership(groups);
		Map<T, List<String>> result = new IdentityHashMap<>();
		for (T entry : entries) {
			GroupDefinition group = byId.get(resolved.get(identity.apply(entry)));
			if (group != null) result.put(entry, List.of(EditorGroupOwnershipHelper.displayName(group)));
		}
		return result;
	}

	@Override public void renderFluid(GuiGraphics graphics, EditorFluidIngredientView entry, int x, int y) {
		((EmiIngredient) entry.ingredient()).render(graphics, x, y, 0);
	}

	@Override public void renderGeneric(GuiGraphics graphics, EditorGenericIngredientView entry, int x, int y) {
		((EmiIngredient) entry.ingredient()).render(graphics, x, y, 0);
	}

	@Override public List<Component> fluidTooltip(EditorFluidIngredientView entry) {
		return tooltipText((EmiStack) entry.ingredient(), entry.resourceId(), null);
	}

	@Override public List<Component> genericTooltip(EditorGenericIngredientView entry) {
		return tooltipText((EmiStack) entry.ingredient(), entry.resourceId(), entry.typeId());
	}

	/** Explicit EMI ClientTooltipComponent -> editor Component boundary: use EMI's text source. */
	private static List<Component> tooltipText(EmiStack stack, String resourceId, String typeId) {
		List<Component> result = new ArrayList<>(stack.getTooltipText());
		if (result.isEmpty()) result.add(stack.getName());
		result.add(Component.literal(resourceId).withStyle(ChatFormatting.DARK_GRAY));
		if (typeId != null) result.add(Component.literal(typeId).withStyle(ChatFormatting.GRAY));
		return List.copyOf(result);
	}

	@Override public List<ItemStack> resolveEditorDraftItems(GroupFilterEditorDraft draft, boolean enabled) {
		return resolveDraftItems(draft, enabled);
	}

	@Override public List<ItemStack> resolveHybridEditorDraftItems(GroupFilterEditorDraft draft, boolean enabled) {
		return resolveDraftItems(draft, enabled);
	}

	private List<ItemStack> resolveDraftItems(GroupFilterEditorDraft draft, boolean enabled) {
		return draft.toFilter().map(filter -> resolveItems(new GroupDefinition("__editor_draft", "", enabled, filter)))
			.orElse(List.of());
	}

	@Override public List<ItemStack> resolveItems(GroupDefinition definition) {
		return matching(definition, ViewerIngredient.Kind.ITEM).stream()
			.map(ViewerIngredient::entry).map(EmiIngredient::getEmiStacks).map(values -> values.get(0).getItemStack())
			.filter(stack -> !stack.isEmpty()).toList();
	}

	@Override public List<EditorFluidIngredientView> resolveFluids(GroupDefinition definition, String traceName) {
		return fluidViews(matching(definition, ViewerIngredient.Kind.FLUID));
	}

	@Override public List<EditorGenericIngredientView> resolveGenericIngredients(GroupDefinition definition,
		String traceName) {
		return genericViews(matching(definition, ViewerIngredient.Kind.GENERIC));
	}

	private List<ViewerIngredient<EmiIngredient>> matching(GroupDefinition definition, ViewerIngredient.Kind kind) {
		return adapter.bootstrapContext().universe().ordered().stream()
			.filter(value -> value.kind() == kind && definition.compiledFilter().matches(value.view())).toList();
	}

	@Override public CompletableFuture<Void> prepareEditorEntry(GroupDefinition definition) {
		return adapter.prepareEditorIndex().thenRun(() -> index.prepareFullMatch(definition));
	}

	@Override public List<ItemStack> cachedFullMatchItems(GroupDefinition definition) {
		return index.fullMatchItems(definition.id()).stream().map(ViewerIngredient::entry)
			.map(EmiIngredient::getEmiStacks).map(values -> values.get(0).getItemStack())
			.filter(stack -> !stack.isEmpty()).toList();
	}

	@Override public List<EditorFluidIngredientView> cachedFullMatchFluids(GroupDefinition definition,
		String traceName) { return fluidViews(index.fullMatchFluids(definition.id())); }

	@Override public List<EditorGenericIngredientView> cachedFullMatchGeneric(GroupDefinition definition,
		String traceName) { return genericViews(index.fullMatchGeneric(definition.id())); }

	@Override public void invalidateFullMatchCache(String id) { index.invalidateFullMatch(id); }
	@Override public void populateFullMatchCacheFromSaved(GroupDefinition definition) { index.prepareFullMatch(definition); }
	@Override public boolean verifyItemIndex() { return false; }
	@Override public long beginTrace() { return System.nanoTime(); }
	@Override public void logIfSlow(String name, long startedAt, long thresholdMillis, String details) {
		if (startedAt == 0L) return;
		long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
		if (elapsed >= thresholdMillis) Constants.LOG.info("[Perf] {} took {} ms {}", name, elapsed, details);
	}
	@Override public Optional<GroupDefinition> findGroup(String id) { return GroupRepository.findById(id); }
	@Override public void saveQuietly(GroupDefinition definition) { GroupRepository.saveQuietly(definition); }
	@Override public String sanitizeGeneratedIdBase(String name) {
		return GroupRepository.sanitizeGeneratedIdBase(name);
	}
	@Override public String generateUniqueId(String name) { return GroupRepository.generateUniqueId(name); }
	@Override public String generateUniqueIdIncludingKubeJs(String name) {
		return GroupRepository.generateUniqueIdIncludingScripted(name);
	}
	@Override public void notifyViewer() { GroupRepository.notifyViewer(); }
	@Override public void setEnabledQuietlyWithoutEvent(String id, boolean enabled) {
		GroupRepository.setEnabledQuietlyWithoutEvent(id, enabled);
	}

	@Override public List<PreviewEntry> resolveHeaderIcons(List<GroupIconDefinition> iconIds,
		List<PreviewEntry> fallbackEntries) {
		return adapter.resolveHeaderIconsFromDefinitions(iconIds,
			fallbackEntries.stream().map(PreviewEntry::icon).toList()).stream()
			.map(EmiEditorRuntimeAccess::previewEntry).toList();
	}

	@Override public PreviewLayout renderPreview(GuiGraphics graphics, PreviewRect area, boolean expanded, int page,
		AppearanceDraft appearance, List<PreviewEntry> headerIcons, List<PreviewEntry> entries, Font font,
		PreviewFallbacks fallbacks) {
		GroupSampleRenderer.Layout layout = GroupSampleRenderer.render(graphics,
			new GroupSampleRenderer.Rect(area.x(), area.y(), area.width(), area.height()), expanded, page,
			appearance.toTheme(), previewEntries(headerIcons), previewEntries(entries), font,
			new GroupSampleRenderer.Fallbacks(fallbacks.nameRgb(), fallbacks.collapsedHeaderArgb(),
				fallbacks.expandedHeaderArgb(), fallbacks.expandedGroupArgb(), fallbacks.expandedBorderArgb()));
		return previewLayout(layout);
	}

	@Override public PreviewLayout layoutPreview(PreviewRect area, boolean expanded, int itemCount, int page) {
		GroupSampleRenderer.Layout layout = GroupSampleRenderer.layout(
			new GroupSampleRenderer.Rect(area.x(), area.y(), area.width(), area.height()),
			expanded, itemCount, page);
		List<PreviewCell> cells = layout.cells().stream()
			.map(cell -> new PreviewCell(rect(cell.rect()), cell.itemIndex(), cell.header()))
			.toList();
		return new PreviewLayout(rect(layout.area()), rect(layout.headerCell()), rect(layout.previousPageButton()),
			rect(layout.nextPageButton()), cells, layout.page(), layout.pageCount(), layout.childCapacity(),
			layout.itemCount());
	}

	private static PreviewRect rect(GroupSampleRenderer.Rect rect) {
		return rect == null ? null : new PreviewRect(rect.x(), rect.y(), rect.width(), rect.height());
	}

	@Override public PreviewTooltip previewTooltip(String displayName, int nameColorRgb, int itemCount,
		int fluidCount, int genericCount, boolean expanded, List<PreviewEntry> entries) {
		GroupPreviewTooltip.Result result = GroupPreviewTooltip.build(displayName, nameColorRgb, itemCount,
			fluidCount, genericCount, expanded, previewEntries(entries));
		return new PreviewTooltip(result.lines(), result.visual());
	}

	private static List<EditorFluidIngredientView> fluidViews(
		List<ViewerIngredient<EmiIngredient>> ingredients) {
		List<EditorFluidIngredientView> result = new ArrayList<>(ingredients.size());
		for (ViewerIngredient<EmiIngredient> ingredient : ingredients) {
			EmiStack stack = ingredient.entry().getEmiStacks().get(0);
			String id = ingredient.view().resourceLocation() == null ? stack.getId().toString()
				: ingredient.view().resourceLocation().toString();
			result.add(new EditorFluidIngredientView(stack, stack.getName(), id, search(stack, id,
				ingredient.identity().typeId()), ItemStack.EMPTY));
		}
		return List.copyOf(result);
	}

	private static List<EditorGenericIngredientView> genericViews(
		List<ViewerIngredient<EmiIngredient>> ingredients) {
		List<EditorGenericIngredientView> result = new ArrayList<>(ingredients.size());
		for (ViewerIngredient<EmiIngredient> ingredient : ingredients) {
			EmiStack stack = ingredient.entry().getEmiStacks().get(0);
			String id = ingredient.view().resourceLocation() == null ? stack.getId().toString()
				: ingredient.view().resourceLocation().toString();
			result.add(new EditorGenericIngredientView(ingredient.identity().typeId(), stack, stack,
				stack.getName(), id, ingredient.identity().valueId(), Set.of(),
				search(stack, id, ingredient.identity().typeId())));
		}
		return List.copyOf(result);
	}

	private static IngredientSearchDocument search(EmiStack stack, String id, String type) {
		String namespace = id.contains(":") ? id.substring(0, id.indexOf(':')) : id;
		return IngredientSearchDocument.of(List.of(stack.getName().getString(), id, type),
			List.of(namespace), Set.of());
	}

	private static PreviewEntry previewEntry(ViewerIngredient<EmiIngredient> ingredient) {
		return switch (ingredient.kind()) {
			case ITEM -> PreviewEntry.item(ingredient.entry().getEmiStacks().get(0).getItemStack());
			case FLUID -> PreviewEntry.fluid(fluidViews(List.of(ingredient)).get(0));
			case GENERIC -> PreviewEntry.generic(genericViews(List.of(ingredient)).get(0));
		};
	}

	private List<GroupPreviewEntry> previewEntries(List<PreviewEntry> entries) {
		return entries.stream().map(entry -> switch (entry.kind()) {
			case ITEM -> GroupPreviewEntry.ofItem((ItemStack) entry.value());
			case FLUID -> GroupPreviewEntry.ofRenderer((graphics, x, y) ->
				renderFluid(graphics, (EditorFluidIngredientView) entry.value(), x, y));
			case GENERIC -> GroupPreviewEntry.ofRenderer((graphics, x, y) ->
				renderGeneric(graphics, (EditorGenericIngredientView) entry.value(), x, y));
		}).toList();
	}

	private static PreviewLayout previewLayout(GroupSampleRenderer.Layout layout) {
		List<PreviewCell> cells = layout.cells().stream()
			.map(cell -> new PreviewCell(rect(cell.rect()), cell.itemIndex(), cell.header())).toList();
		return new PreviewLayout(rect(layout.area()), rect(layout.headerCell()), rect(layout.previousPageButton()),
			rect(layout.nextPageButton()), cells, layout.page(), layout.pageCount(), layout.childCapacity(),
			layout.itemCount());
	}
}
