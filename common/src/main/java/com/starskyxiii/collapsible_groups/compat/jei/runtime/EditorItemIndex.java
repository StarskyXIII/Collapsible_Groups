package com.starskyxiii.collapsible_groups.compat.jei.runtime;

import com.starskyxiii.collapsible_groups.platform.Services;
import com.starskyxiii.collapsible_groups.core.GroupFilterEditorDraft;
import com.starskyxiii.collapsible_groups.core.GroupItemSelector;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Pre-built index over the JEI item list for fast editor draft resolution.
 *
 * <p>Replaces the per-edit O(allItems) full-scan path for structurally editable
 * drafts with an O(selectorCount + matchedCandidates) index lookup.
 *
 * <p>Lifecycle: owned by {@link GroupRegistry}, lazily built on first editable
 * preview rebuild, invalidated whenever {@code jeiAllItems} is replaced or cleared.
 * The index is not invalidated on every edit ??it is keyed on stable JEI item
 * identity and does not depend on the current group definition or draft.
 *
 * <p>Order preservation: output order matches the original JEI item order, the same
 * as the current {@code jeiAllItems.stream().filter(previewDef::matches).toList()}
 * result. This is enforced by sorting matched items using stored JEI ordinals.
 */
public final class EditorItemIndex {

	private final List<ItemStack> orderedItems;
	private final Map<ResourceLocation, List<ItemStack>> byId;
	private final Map<ResourceLocation, List<ItemStack>> byTag;
	/** Maps each ItemStack object identity -> its stable index in JEI order. */
	private final IdentityHashMap<ItemStack, Integer> orderByIdentity;

	private static final String VERIFY_OVERRIDE =
		System.getProperty("collapsible_groups.editor_index_verify");

	private EditorItemIndex(
		List<ItemStack> orderedItems,
		Map<ResourceLocation, List<ItemStack>> byId,
		Map<ResourceLocation, List<ItemStack>> byTag,
		IdentityHashMap<ItemStack, Integer> orderByIdentity
	) {
		this.orderedItems = orderedItems;
		this.byId = byId;
		this.byTag = byTag;
		this.orderByIdentity = orderByIdentity;
	}

	/**
	 * Builds the index from the current JEI item list.
	 * Runs once per JEI item-cache generation (paid lazily on first editable edit).
	 */
	public static EditorItemIndex build(List<ItemStack> jeiItems) {
		long traceStart = PerformanceTrace.begin();

		Map<ResourceLocation, List<ItemStack>> byId = new HashMap<>();
		Map<ResourceLocation, List<ItemStack>> byTag = new HashMap<>();
		IdentityHashMap<ItemStack, Integer> orderByIdentity = new IdentityHashMap<>(jeiItems.size() * 2);

		for (int i = 0; i < jeiItems.size(); i++) {
			ItemStack stack = jeiItems.get(i);
			orderByIdentity.put(stack, i);

			ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
			if (id != null) {
				byId.computeIfAbsent(id, k -> new ArrayList<>()).add(stack);
			}

			stack.getItem().builtInRegistryHolder().tags().forEach(tagKey ->
				byTag.computeIfAbsent(tagKey.location(), k -> new ArrayList<>()).add(stack)
			);
		}

		EditorItemIndex index = new EditorItemIndex(
			jeiItems,
			Map.copyOf(byId),
			Map.copyOf(byTag),
			orderByIdentity
		);

		PerformanceTrace.logIfSlow("EditorItemIndex.build", traceStart, 50,
			"items=" + jeiItems.size() + " byId=" + byId.size() + " byTag=" + byTag.size());

		return index;
	}

	/**
	 * Resolves the item subset for a structurally editable draft using the pre-built index.
	 *
	 * <p>Result order matches the original JEI item order, identical to the current
	 * full-scan path. Deduplication is enforced via identity-based membership tracking.
	 *
	 * @param draft the current editor draft (item selectors and item tags are used)
	 * @return ordered, deduplicated list of matching JEI items
	 */
	public List<ItemStack> resolveDraft(GroupFilterEditorDraft draft) {
		long traceStart = PerformanceTrace.begin();

		if (draft.explicitItemSelectors().isEmpty() && draft.itemTags().isEmpty()) {
			return List.of();
		}

		// Identity set - deduplication is automatic across overlapping selectors/tags
		IdentityHashMap<ItemStack, Boolean> matched = new IdentityHashMap<>();

		// --- Explicit item selectors (whole-item or exact-stack) ---
		for (String selector : draft.explicitItemSelectors()) {
			if (GroupItemSelector.isWholeItemSelector(selector)) {
				// Whole-item selector: all JEI variants of this registry ID
				ResourceLocation id = ResourceLocation.tryParse(selector);
				if (id != null) {
					List<ItemStack> bucket = byId.get(id);
					if (bucket != null) {
						for (ItemStack stack : bucket) matched.put(stack, Boolean.TRUE);
					}
				}
			} else {
				// Exact-stack selector: decode once, then narrow to registry-ID bucket
				Optional<ItemStack> decoded = GroupItemSelector.decodeExactSelector(selector);
				decoded.ifPresent(reference -> {
					ResourceLocation id = BuiltInRegistries.ITEM.getKey(reference.getItem());
					if (id != null) {
						List<ItemStack> bucket = byId.get(id);
						if (bucket != null) {
							for (ItemStack candidate : bucket) {
								if (ItemStack.isSameItemSameComponents(reference, candidate)) {
									matched.put(candidate, Boolean.TRUE);
								}
							}
						}
					}
				});
			}
		}

		// --- Item tag selectors ---
		for (String tagId : draft.itemTags()) {
			ResourceLocation tagRl = ResourceLocation.tryParse(tagId);
			if (tagRl != null) {
				List<ItemStack> bucket = byTag.get(tagRl);
				if (bucket != null) {
					for (ItemStack stack : bucket) matched.put(stack, Boolean.TRUE);
				}
			}
		}

		// --- Preserve JEI order by sorting matched items by stored ordinal ---
		List<ItemStack> result = new ArrayList<>(matched.size());
		for (ItemStack s : matched.keySet()) result.add(s);
		result.sort(Comparator.comparingInt(s -> orderByIdentity.getOrDefault(s, Integer.MAX_VALUE)));

		List<ItemStack> copy = List.copyOf(result);
		PerformanceTrace.logIfSlow("EditorItemIndex.resolveDraft", traceStart, 1,
			"selectors=" + draft.explicitItemSelectors().size()
				+ " tags=" + draft.itemTags().size()
				+ " result=" + copy.size());
		return copy;
	}

	/** Whether correctness verification mode is active (development only). */
	public static boolean isVerifyEnabled() {
		if (VERIFY_OVERRIDE != null) {
			return Boolean.parseBoolean(VERIFY_OVERRIDE);
		}
		return Services.CONFIG.debugEditorIndexVerificationEnabled();
	}
}
