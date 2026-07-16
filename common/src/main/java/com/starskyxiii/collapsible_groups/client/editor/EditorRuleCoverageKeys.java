package com.starskyxiii.collapsible_groups.client.editor;

import com.starskyxiii.collapsible_groups.client.editor.EditorFluidIngredientView;
import com.starskyxiii.collapsible_groups.client.editor.EditorGenericIngredientView;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Single authority for the keys used by the rule-coverage sets.
 *
 * <p>The right panel converts its resolved group members into these keys once and
 * hands them to {@code GroupEditorState}; the source grid derives the same key for
 * each cell to decide whether it is rule-covered. The conventions mirror the
 * source-grid ownership caches so both features agree on identity: component-less
 * items use their registry id, component-bearing items use {@code id#exactEncoded},
 * fluid = resource id, generic = {@code typeId|resourceId} (same as the drag key).
 *
 * <p>If an exact selector cannot be encoded, a component-bearing item is deliberately
 * unkeyable: producers omit it and consumers treat it as uncovered. It must never
 * fall back to the registry id, which would broaden coverage to sibling variants.
 */
final class EditorRuleCoverageKeys {

	private EditorRuleCoverageKeys() {}

	private static final String EXACT_SELECTOR_PREFIX = "stack:";

	static Optional<String> itemKey(ItemStack stack, Supplier<Optional<String>> exactSelector) {
		String registryId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
		if (stack.getComponentsPatch().isEmpty()) {
			// Component-less stacks do not consult the identity cache.
			return Optional.of(registryId);
		}
		return deriveItemKey(registryId, true, exactSelector);
	}

	/**
	 * Pure-Java seam for coverage-key policy tests. It intentionally has no Minecraft
	 * types, so common tests retain their headless boundary.
	 */
	static Optional<String> deriveItemKey(String registryId, boolean hasComponents,
			Supplier<Optional<String>> exactSelector) {
		if (!hasComponents) {
			return Optional.of(registryId);
		}
		return exactSelector.get().map(encoded -> registryId + "#" + exactEncoded(encoded));
	}

	private static String exactEncoded(String selector) {
		return selector.startsWith(EXACT_SELECTOR_PREFIX)
			? selector.substring(EXACT_SELECTOR_PREFIX.length())
			: selector;
	}

	static String fluidKey(EditorFluidIngredientView fluid) {
		return fluid.resourceId();
	}

	static String genericKey(EditorGenericIngredientView generic) {
		return generic.typeId() + "|" + generic.resourceId();
	}

	static Set<String> fluidIds(List<EditorFluidIngredientView> fluids) {
		Set<String> out = new HashSet<>(Math.max(16, fluids.size()));
		for (EditorFluidIngredientView fluid : fluids) {
			out.add(fluidKey(fluid));
		}
		return out;
	}

	static Set<String> genericKeys(List<EditorGenericIngredientView> generics) {
		Set<String> out = new HashSet<>(Math.max(16, generics.size()));
		for (EditorGenericIngredientView generic : generics) {
			out.add(genericKey(generic));
		}
		return out;
	}
}
