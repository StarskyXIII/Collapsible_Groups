package com.starskyxiii.collapsible_groups.compat.jei.runtime;

import com.starskyxiii.collapsible_groups.core.ItemUniverseProvider;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.function.Supplier;

/** Default editor item universe: full JEI variants when available, registry defaults otherwise. */
public final class EditorItemUniverseProvider implements ItemUniverseProvider {
	public static final EditorItemUniverseProvider INSTANCE = new EditorItemUniverseProvider(
		() -> {
			GroupRegistry.populateJeiCachesIfEmpty();
			return GroupRegistry.getJeiAllItems();
		},
		EditorItemUniverseProvider::registryDefaults
	);

	private final Supplier<List<ItemStack>> preferred;
	private final Supplier<List<ItemStack>> fallback;

	EditorItemUniverseProvider(Supplier<List<ItemStack>> preferred, Supplier<List<ItemStack>> fallback) {
		this.preferred = preferred;
		this.fallback = fallback;
	}

	@Override
	public List<ItemStack> allStacks() {
		return preferNonEmpty(preferred.get(), fallback);
	}

	static <T> List<T> preferNonEmpty(List<T> preferred, Supplier<List<T>> fallback) {
		return preferred != null && !preferred.isEmpty() ? preferred : List.copyOf(fallback.get());
	}

	private static List<ItemStack> registryDefaults() {
		return BuiltInRegistries.ITEM.stream()
			.filter(item -> item != Items.AIR)
			.map(ItemStack::new)
			.toList();
	}
}
