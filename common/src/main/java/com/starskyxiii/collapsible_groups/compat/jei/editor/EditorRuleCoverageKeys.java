package com.starskyxiii.collapsible_groups.compat.jei.editor;

import com.starskyxiii.collapsible_groups.compat.jei.data.GenericIngredientView;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Single authority for the id-string keys used by the P3b-3 rule-coverage sets.
 *
 * <p>The right panel converts its resolved group members into these keys once and
 * hands them to {@code GroupEditorState}; the source grid derives the same key for
 * each cell to decide whether it is rule-covered. The conventions mirror the
 * source-grid ownership caches so both features agree on identity: item = registry
 * id, fluid = resource id, generic = {@code typeId|resourceId} (same as the drag
 * key).
 */
final class EditorRuleCoverageKeys {

	private EditorRuleCoverageKeys() {}

	static String itemKey(ItemStack stack) {
		return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
	}

	static String fluidKey(EditorFluidIngredientView fluid) {
		return fluid.resourceId();
	}

	static String genericKey(GenericIngredientView generic) {
		return generic.typeId() + "|" + generic.resourceId();
	}

	static Set<String> itemIds(List<ItemStack> items) {
		Set<String> out = new HashSet<>(Math.max(16, items.size()));
		for (ItemStack stack : items) {
			out.add(itemKey(stack));
		}
		return out;
	}

	static Set<String> fluidIds(List<EditorFluidIngredientView> fluids) {
		Set<String> out = new HashSet<>(Math.max(16, fluids.size()));
		for (EditorFluidIngredientView fluid : fluids) {
			out.add(fluidKey(fluid));
		}
		return out;
	}

	static Set<String> genericKeys(List<GenericIngredientView> generics) {
		Set<String> out = new HashSet<>(Math.max(16, generics.size()));
		for (GenericIngredientView generic : generics) {
			out.add(genericKey(generic));
		}
		return out;
	}
}
